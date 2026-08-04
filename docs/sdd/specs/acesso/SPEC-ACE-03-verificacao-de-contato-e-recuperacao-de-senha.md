# SPEC-ACE-03 — Verificação de contato e recuperação de senha

| | |
|---|---|
| **Área** | `VER` (verificação de contato) + `REC` (recuperação de senha) |
| **Papéis** | Público (não autenticado): auto-cadastro, verificação e "esqueci a senha". Todos os autenticados: reverificar contato. |
| **Status geral** | ✅ **Implementado (2026-07-21)** — migration **V14**, **223 testes verdes** (TDD), app sobe com o schema validado. OTP (WhatsApp) p/ telefone e reset, link (e-mail) p/ verificar e-mail, **seletor de método** na recuperação. **Pendente**: integração SPEC-ACE-02 (e-mail do Google já verificado + OTP no `/conta/completar` — RN-VER-08) e **lockout do login por senha** (§5.4). Canal WhatsApp depende da infra da Evolution (SPEC-WPP-01); e-mail é stub até integrar SMTP. |
| **Código (a criar)** | `CodigoVerificacao` (entidade) + `codigos_verificacao` (V14); `FinalidadeCodigo`/`FinalidadeToken` (enums VARCHAR); `VerificacaoService` (OTP: gerar/validar/lockout, marca telefone/e-mail verificado); evolução de `ConviteService`/`TokenAtivacao` (coluna `finalidade`) para o link de verificação de e-mail; `RecuperacaoSenhaService` (ou métodos no `VerificacaoService`); `VerificacaoController` (`/verificar-telefone`, `/verificar-email`), `RecuperacaoSenhaController` (`/esqueci-senha`, `/redefinir-senha`); ajustes em `UsuarioService.registrarPassageiro`, `SecurityConfig` (rotas públicas), `Usuario` (`telefone_verificado_em`, `email_verificado_em`); telas `auth/{verificar-telefone,esqueci-senha,redefinir-senha,verificar-email}.html`. |
| **Constituição** | Artigos II (camadas), IV (migrations forward-only), VI (UUID/enums VARCHAR), VII (RBAC/rotas públicas), VIII (telefone canônico / senha BCrypt), IX (português), X (HTMX), XI (segurança), XIV (ambiente compartilhado, sem extensões) |
| **Relacionada** | [SPEC-ACE-01](SPEC-ACE-01-autenticacao.md) (login senha; realiza os itens da sua §9), [SPEC-ACE-02](SPEC-ACE-02-login-social-google.md) (Google/OIDC; e-mail já verificado pela Google), [SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md) (canal WhatsApp e a porta `ProvedorWhatsapp`), [SPEC-WPP-02](../whatsapp/SPEC-WPP-02-solicitacao-sob-demanda-e-onboarding-whatsapp.md) (onboarding pelo bot; token "Acesso à plataforma") · **ADR-16** (proposta, §4) |

---

## 1. A lacuna que esta spec cobre

A **SPEC-ACE-01 §9** já lista, explicitamente, **três coisas fora do escopo** até aqui — e são exatamente
o que o dono do projeto pediu agora:

1. **Recuperação de senha** ("esqueci a senha") — mencionada no domínio, **não implementada**.
2. **Verificação de e-mail/telefone** (confirmação por link/código) — **não implementada**.
3. **Bloqueio por tentativas** (rate limiting / lockout) — **não implementado**.

Hoje o cadastro (`POST /registrar`) cria um `PASSAGEIRO` **ATIVO** direto, **sem confirmar** que o
telefone ou o e-mail informados pertencem de fato à pessoa; e quem esquece a senha **não tem como
recuperá-la** pela interface (depende de um gerente reemitir convite). Esta spec fecha essas lacunas
**reaproveitando o que já existe** — o `TokenAtivacao` (link mágico já pronto) e o
`NotificacaoService` multicanal (in-app / e-mail / WhatsApp) — sem inventar mecanismo de segurança novo.

---

## 2. O que muda em relação ao que já existe

| Comportamento hoje | Onde | O que muda nesta spec |
|---|---|---|
| `POST /registrar` cria `PASSAGEIRO` **ATIVO** imediatamente | SPEC-ACE-01 FR-AUT-08 | Passa a criar **`PENDENTE`** e disparar um **código OTP** (WhatsApp) para verificar o telefone; **só vira `ATIVO`** após a verificação (reusa a transição PENDENTE→ATIVO já existente do convite). Com **degradação graciosa** quando não há canal (RN-VER-07). |
| E-mail informado no cadastro **nunca é confirmado** | SPEC-ACE-01 | Passa a receber um **link mágico** de verificação por e-mail (reusa `TokenAtivacao` com `finalidade`); a conta funciona sem isso — e-mail é opcional (RN-AUT-02), mas fica marcada como **verificada** ao clicar. |
| Sem "esqueci a senha" | SPEC-ACE-01 §9 | Novo fluxo público `/esqueci-senha` → **código OTP** (WhatsApp) → `/redefinir-senha` define a nova senha. |
| Sem qualquer limite de tentativas | SPEC-ACE-01 §9 | O OTP tem **expiração curta + contador de tentativas + bloqueio** (RN-VER-04/05); requisições limitadas por identificador/IP (RN-VER-06). |
| Conta criada por Google não marca contato verificado | SPEC-ACE-02 | O **e-mail do Google já vem verificado** (`email_verified`): a conta nasce com `email_verificado_em` preenchido; ao completar o perfil com telefone (`/conta/completar`), dispara a verificação de **telefone** por OTP. |

Nada disso quebra o login por senha (SPEC-ACE-01) nem o Google (SPEC-ACE-02): **adiciona** camadas de
confiança de contato e o caminho de recuperação.

---

## 3. A decisão-base — modelo **híbrido** (mecanismo × canal)

> A pergunta "código **ou** e-mail" mistura **dois eixos independentes**. Separá-los é a decisão-chave
> desta spec:
> - **Mecanismo** — *como* o fator é provado: **código OTP** (6 dígitos que a pessoa digita de volta)
>   vs. **link mágico** (URL com token que a pessoa clica — o que o `TokenAtivacao` **já faz**).
> - **Canal** — *por onde* chega: **WhatsApp** (adaptador real, telefone é a identidade) vs. **e-mail**
>   (hoje `NotificacaoEmailCanal` é **stub** — só registra em log, não envia).

**Escolha (do dono do projeto): híbrido.**

| Necessidade | Mecanismo | Canal | Por quê |
|---|---|---|---|
| **Verificar telefone** (no cadastro) | **Código OTP (6 díg.)** | **WhatsApp** | O telefone é o **identificador canônico** (Art. VIII); o WhatsApp **entrega de verdade** e casa com o bot da SPEC-WPP-02. |
| **"Esqueci a senha"** | **Código OTP (6 díg.)** | **WhatsApp** | Mesma engine; mantém a pessoa **no mesmo fluxo** (digita o código e a nova senha na mesma tela). |
| **Verificar e-mail** (opcional) | **Link mágico** | **E-mail** | **Reaproveita o `TokenAtivacao`/`/ativar`** (zero clique-digitação) e não depende de força-bruta; adequado ao e-mail. |

**Consequências assumidas:**
- O caminho **feliz** (telefone + WhatsApp) **não depende do e-mail** — que segue stub até integrarmos
  `JavaMailSender` (fica fora do caminho crítico). A verificação de e-mail é um **plus** que só entrega
  quando o e-mail real existir (degrada como no-op até lá, coerente com a RN-WPP-02 da SPEC-WPP-01).
- **Fallback de canal:** se o usuário não tiver WhatsApp alcançável mas tiver e-mail (e o e-mail real
  já existir), o mesmo **OTP** pode sair por e-mail (coluna `canal` em `codigos_verificacao`). O
  primário é WhatsApp.

---

## 4. Decisões de modelo (propostas) — base da **ADR-16**

### D1 — Mecanismo/canal → **✅ Híbrido** (§3)
OTP (WhatsApp) para **telefone** e **reset**; link mágico (e-mail) para **verificar e-mail**.

### D2 — Onde mora o OTP → **✅ Nova tabela `codigos_verificacao`**
Espelha o `TokenAtivacao` (guarda só o **hash**, expiração, uso único), mas com o que o OTP exige a
mais: **`finalidade`** (`VERIFICAR_TELEFONE` \| `RESET_SENHA`), **`canal`**, **`tentativas`** (para o
lockout) e **`criado_ip`** (throttle/auditoria). Não sobrecarrega o `TokenAtivacao` (que é link, não
código). Enums como **VARCHAR + CHECK** (Art. VI), UUID nativo `gen_random_uuid()` (ADR-03), sem
extensões (Art. XIV).

### D3 — Onde mora o link de verificação de e-mail → **✅ Estender `tokens_ativacao`**
Acrescenta uma coluna **`finalidade`** (`ATIVACAO` \| `VERIFICAR_EMAIL`), com **`DEFAULT 'ATIVACAO'`**
para **preservar** a semântica atual (convite/onboarding/"Acesso à plataforma"). O consumidor
`ConviteService.ativar` continua igual; a verificação de e-mail é um novo consumidor que apenas
**marca `email_verificado_em`** (não mexe em senha). Reaproveita hash, expiração e uso único já
existentes (ADR-11).

### D4 — Estado da verificação → **✅ Colunas em `usuarios`**
`telefone_verificado_em` e `email_verificado_em` (ambos `TIMESTAMPTZ NULL`), **ortogonais** ao
`status` (mesmo padrão do `perfil_incompleto` da SPEC-ACE-02). `NULL` = não verificado.

### D5 — Cadastro exige verificar telefone? → **✅ Sim, via transição PENDENTE→ATIVO** (com degradação)
`POST /registrar` cria o usuário **`PENDENTE`** (não autentica — `isEnabled()==false`) e dispara o OTP;
a verificação o promove a **`ATIVO`**. Reusa **exatamente** a transição do convite (`ConviteService`),
sem estado novo. **Degradação (RN-VER-07):** se **nenhum canal** conseguir entregar o código (WhatsApp
stub **e** sem e-mail real), a conta é criada **`ATIVO` não verificada** (comportamento de hoje) e
marcada para verificação posterior — para o web **não** ficar refém da infra pendente da Evolution.
> *Aberto a confirmação do dono do projeto: manter o "bloqueio até verificar" ou apenas "avisar e deixar
> entrar". O padrão proposto é bloquear no cadastro (mais seguro), com a degradação acima.*

### D6 — Parâmetros do OTP → **✅ 6 dígitos, 10 min, 5 tentativas, reenvio 60 s**
Código **numérico de 6 dígitos**; validade **10 minutos**; **máx. 5 tentativas** por código (depois
invalida — exige reenviar); **reenvio** com carência de **60 s** e teto por hora. Guardado como
**hash** (SHA-256, como o `TokenAtivacao`); recomendado **HMAC com segredo (pepper)** no lugar do
SHA-256 puro, dado o baixo espaço de 10⁶ (mitiga leitura do banco; a expiração curta + lockout cobrem
a força-bruta online).

---

## 5. Escopo

### 5.1 Inclui — Verificação de contato (`VER`)
- **Telefone (OTP/WhatsApp):** no cadastro público e ao completar perfil (SPEC-ACE-02), dispara um código
  de 6 dígitos; tela `/verificar-telefone` para digitar; sucesso marca `telefone_verificado_em` e (no
  cadastro) promove `PENDENTE → ATIVO`.
- **E-mail (link/e-mail):** quando há e-mail, envia um **link mágico** (`/verificar-email?token=…`);
  clicar marca `email_verificado_em`. **Não** bloqueia o uso da conta (e-mail é opcional).
- **Reenvio** de código (com carência) e **reemissão** do link.
- **Google (SPEC-ACE-02):** e-mail já chega **verificado** (`email_verified`); telefone é verificado quando
  informado em `/conta/completar`.

### 5.2 Inclui — Recuperação de senha (`REC`)
- Botão **"Esqueci minha senha"** na tela de login → `/esqueci-senha`: um **card central** onde a
  pessoa **escolhe o método** (E-mail **ou** Telefone), preenche o campo correspondente e aperta
  **"Enviar"**; um **controle de voltar** no canto superior do card retorna ao `/login`.
- Geração de **OTP** com `finalidade = RESET_SENHA` no **canal do método escolhido** (Telefone →
  WhatsApp; E-mail → e-mail), materializado na coluna `canal` de `codigos_verificacao`.
- Tela `/redefinir-senha` (código + nova senha + confirmação) → valida o código, aplica **BCrypt**,
  invalida o código, audita e redireciona para `/login?senhaRedefinida`.

### 5.3 Inclui — Bloqueio e anti-abuso (parcial)
- **Lockout do código:** contador de tentativas por código (RN-VER-05).
- **Throttle:** limite de emissões por identificador/IP em janela de tempo (RN-VER-06).
- **Anti-enumeração:** respostas **genéricas** — nunca revelam se o identificador existe (RN-REC-02).

### 5.4 Não inclui (futuro)
- **Lockout do *login por senha*** (bloquear a conta após N senhas erradas) — é o companheiro natural,
  mas fica para uma próxima etapa; aqui o foco é verificação + reset. Registrar como **DT**.
- **2FA/MFA** permanente no login (o OTP aqui é pontual: verificação e reset, não segundo fator a cada
  acesso).
- **E-mail real** (`JavaMailSender`/SMTP) — é **pré-requisito** do canal e-mail, mas a integração de
  infra (segredo no deploy) é tarefa à parte, como a Evolution da SPEC-WPP-01 (ver §10 e §12).
- **Troca de telefone/e-mail** com reverificação obrigatória na tela de perfil (fica indicado, não
  detalhado aqui).
- **WhatsApp dos motoristas** (fora, como nas SPEC-WPP-01/11).

---

## 6. Modelagem (migration V14 — proposta)

Evolução **aditiva** (Art. IV), sem tocar migrations existentes, sem extensões/superusuário (Art. XIV):

```
-- V14: verificação de contato e recuperação de senha

ALTER TABLE usuarios
  ADD COLUMN telefone_verificado_em  TIMESTAMPTZ,   -- NULL = telefone não verificado
  ADD COLUMN email_verificado_em     TIMESTAMPTZ;   -- NULL = e-mail não verificado

-- Link mágico ganha finalidade; DEFAULT preserva o convite/ativação atual (ADR-11).
ALTER TABLE tokens_ativacao
  ADD COLUMN finalidade VARCHAR(30) NOT NULL DEFAULT 'ATIVACAO'
    CHECK (finalidade IN ('ATIVACAO','VERIFICAR_EMAIL'));

-- Código OTP (6 dígitos): guarda só o HASH, com expiração, uso único e lockout.
CREATE TABLE codigos_verificacao (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario      UUID NOT NULL REFERENCES usuarios(id),
  codigo_hash  VARCHAR(120) NOT NULL,               -- SHA-256/HMAC do código cru
  finalidade   VARCHAR(30)  NOT NULL
               CHECK (finalidade IN ('VERIFICAR_TELEFONE','RESET_SENHA')),
  canal        VARCHAR(20)  NOT NULL DEFAULT 'WHATSAPP'
               CHECK (canal IN ('WHATSAPP','EMAIL')),
  tentativas   SMALLINT     NOT NULL DEFAULT 0,      -- para o lockout (RN-VER-05)
  criado_em    TIMESTAMPTZ  NOT NULL,               -- preenchido pela app (como TokenAtivacao)
  expira_em    TIMESTAMPTZ  NOT NULL,
  usado_em     TIMESTAMPTZ,
  criado_ip    VARCHAR(45)                          -- throttle/auditoria (RN-VER-06)
);
CREATE INDEX ix_codigos_usuario_finalidade ON codigos_verificacao (usuario, finalidade);
```

- **PK UUID** via `GenerationType.UUID` do Hibernate (como no `TokenAtivacao`); o `DEFAULT` no DDL é
  só rede de segurança.
- **Enums como VARCHAR + CHECK** (ADR-02/Art. VI); `FinalidadeCodigo` (`VERIFICAR_TELEFONE`,
  `RESET_SENHA`) e `FinalidadeToken` (`ATIVACAO`, `VERIFICAR_EMAIL`) no pacote `domain/enums`.
- **Só o hash** do código é persistido (nunca o valor cru) — mesma política do `TokenAtivacao`
  (Art. XI). `usado_em` garante **uso único**; `expira_em` a validade curta.
- **`ddl-auto: validate`** em prod exige que as entidades batam com este schema (Art. V).

---

## 7. Fluxos

### 7.1 Cadastro com verificação de telefone (OTP/WhatsApp)

```mermaid
sequenceDiagram
    autonumber
    actor U as Pessoa (cadastro)
    participant AC as AuthController
    participant US as UsuarioService
    participant VS as VerificacaoService
    participant NS as NotificacaoService
    participant WA as WhatsApp (canal)

    U->>AC: POST /registrar (nome, telefone, e-mail?, senha)
    AC->>US: registrarPassageiro(form)
    US->>US: cria Usuario PENDENTE (telefone_verificado_em = NULL)
    US->>VS: gerarCodigo(usuario, VERIFICAR_TELEFONE)
    VS->>VS: gera 6 dígitos, salva HASH (expira 10 min)
    VS->>NS: enviar(destino, "Código CALADRIUS", "…123456…", WHATSAPP)
    NS->>WA: entrega o código
    AC-->>U: redirect /verificar-telefone (identificador na sessão)
    U->>AC: POST /verificar-telefone (codigo)
    AC->>VS: verificar(usuario, VERIFICAR_TELEFONE, codigo)
    alt código válido
        VS->>US: marca telefone_verificado_em = agora; status = ATIVO
        AC-->>U: redirect /login?verificado
    else inválido/expirado
        VS-->>AC: incrementa tentativas; erro genérico (lockout em 5)
        AC-->>U: reexibe /verificar-telefone (erro) + opção "reenviar"
    end
    Note over U,WA: e-mail (se houver) recebe, em paralelo, um LINK de verificação (§7.3)
```

### 7.2 "Esqueci a senha" (OTP/WhatsApp)

```mermaid
sequenceDiagram
    autonumber
    actor U as Pessoa
    participant RC as RecuperacaoSenhaController
    participant RS as RecuperacaoSenhaService
    participant VS as VerificacaoService
    participant NS as NotificacaoService

    U->>RC: GET /esqueci-senha  (link na tela de login)
    U->>RC: POST /esqueci-senha (metodo = EMAIL|TELEFONE + valor)
    RC->>RS: solicitarReset(metodo, valor, ip)
    RS->>RS: encontra usuário ATIVO pelo campo do método escolhido
    alt existe e é elegível
        RS->>VS: enviarCodigo(usuario, RESET_SENHA, canal(metodo), ip)
        VS->>NS: enviar(destino, "Código…", "…código…", canal)
    end
    RC-->>U: "Se a conta existir, enviamos um código." (SEMPRE — anti-enumeração)
    U->>RC: GET /redefinir-senha
    U->>RC: POST /redefinir-senha (identificador, codigo, novaSenha, confirmar)
    RC->>RS: redefinir(identificador, codigo, novaSenha)
    RS->>VS: verificar(usuario, RESET_SENHA, codigo)
    alt válido
        RS->>RS: senha = BCrypt(novaSenha); invalida o código; audita SEGURANCA
        RC-->>U: redirect /login?senhaRedefinida
    else inválido/expirado/limite
        RC-->>U: reexibe com erro genérico
    end
```

### 7.3 Verificar e-mail (link mágico — reusa `TokenAtivacao`)

```mermaid
sequenceDiagram
    autonumber
    actor U as Pessoa
    participant VC as VerificacaoController
    participant CS as ConviteService (token)
    participant NS as NotificacaoService
    participant EM as E-mail (canal)

    Note over CS,EM: no cadastro/perfil, se há e-mail:
    CS->>CS: cria TokenAtivacao (finalidade = VERIFICAR_EMAIL)
    CS->>NS: enviar(destino, "Confirme seu e-mail", ".../verificar-email?token=…", EMAIL)
    NS->>EM: entrega o link
    U->>VC: GET /verificar-email?token=…
    VC->>CS: verificarEmail(token)
    alt token válido (não usado, no prazo, finalidade correta)
        CS->>CS: marca email_verificado_em; consome o token
        VC-->>U: "E-mail confirmado."
    else inválido/expirado
        VC-->>U: "Link inválido ou expirado." + opção de reenviar
    end
```

---

## 8. Regras de negócio

### Verificação (`VER`)

| Regra | Descrição |
|---|---|
| **RN-VER-01** | O **código OTP** tem **6 dígitos numéricos**, validade **10 min**, **uso único** e é guardado **apenas como hash** (nunca em texto) — mesma política do `TokenAtivacao` (Art. XI). |
| **RN-VER-02** | Verificar o **telefone** com sucesso grava `telefone_verificado_em` e, quando o usuário estava **PENDENTE por cadastro**, promove para **ATIVO** (reusa a transição do convite — ADR-11). |
| **RN-VER-03** | O **e-mail** é verificado por **link mágico** (`TokenAtivacao`, `finalidade = VERIFICAR_EMAIL`); grava `email_verificado_em`; **não** bloqueia o uso da conta (e-mail é opcional — RN-AUT-02). |
| **RN-VER-04** | Cada código **expira** em 10 min; expirado/usado é rejeitado com mensagem **genérica**. |
| **RN-VER-05** | **Lockout:** no máximo **5 tentativas** por código; ao atingir, o código é **invalidado** e a pessoa deve **reenviar** um novo. |
| **RN-VER-06** | **Throttle de emissão:** limite de códigos por **identificador** e por **IP** numa janela (ex.: 5/15 min) e **carência de 60 s** entre reenvios; excedeu → "tente novamente em instantes". |
| **RN-VER-07** | **Degradação graciosa:** se **nenhum canal** entregar o código (WhatsApp em stub — RN-WPP-02 — e sem e-mail real), o cadastro **não** trava: cria `ATIVO` **não verificado** e registra pendência (o app nunca cai por falta de canal — herda RN-WPP-01). |
| **RN-VER-08** | Conta criada por **Google** (SPEC-ACE-02) nasce com **`email_verificado_em`** preenchido (`email_verified` do OIDC); o **telefone** é verificado por OTP quando informado em `/conta/completar`. |
| **RN-VER-09** | A verificação e a reemissão são **idempotentes** quanto ao estado: reverificar um contato já verificado é no-op de sucesso; emitir novo código **invalida** o anterior da mesma finalidade. |

### Recuperação de senha (`REC`)

| Regra | Descrição |
|---|---|
| **RN-REC-01** | "Esqueci a senha" pede um **método explícito** (E-mail **ou** Telefone) — não usa a heurística `@` do login; o método define o **campo de busca** e o **canal** do OTP (Telefone→WhatsApp, E-mail→e-mail). Só emite código para usuário **ATIVO e não removido**. |
| **RN-REC-02** | **Anti-enumeração:** a resposta é **sempre** genérica ("se a conta existir, enviamos um código"), independentemente de o identificador existir — não revela cadastro (alinhado à FR-AUT-06). |
| **RN-REC-03** | Redefinir exige um **código válido** (`RESET_SENHA`) + **nova senha** conforme RN-AUT-05 (6–72, BCrypt); ao concluir, o código é **consumido** e **todos** os demais códigos de reset pendentes do usuário são invalidados. |
| **RN-REC-04** | Toda redefinição é registrada na **auditoria de segurança** (`AuditoriaService.registrarSeguranca`), com IP; não há e-mail/WhatsApp que **confirme a troca** obrigatoriamente nesta etapa (recomendado como aviso in-app). |
| **RN-REC-05** | Uma conta **sem senha** (`hash_senha` nulo — ex.: passageiro auto-cadastrado pelo bot, SPEC-WPP-02) pode usar o "esqueci a senha" para **definir** a primeira senha (equivale ao "Acesso à plataforma"), desde que tenha telefone verificável. |

---

## 9. Requisitos funcionais

### Verificação
- **FR-VER-01** — `POST /registrar` cria o usuário **PENDENTE** e dispara OTP de `VERIFICAR_TELEFONE`
  (WhatsApp), redirecionando para `GET /verificar-telefone`.
- **FR-VER-02** — `GET /verificar-telefone` (público) exibe o campo de código e um botão **"Reenviar"**.
- **FR-VER-03** — `POST /verificar-telefone` valida o código e, em sucesso, marca o telefone e ativa a
  conta (redirect `/login?verificado`); em erro, reexibe com mensagem genérica.
- **FR-VER-04** — `POST /verificar-telefone/reenviar` gera novo código respeitando a carência (RN-VER-06).
- **FR-VER-05** — Havendo e-mail, o sistema envia um **link** de `VERIFICAR_EMAIL` (e-mail) no cadastro
  e permite reemitir a partir do perfil/conta.
- **FR-VER-06** — `GET /verificar-email?token=…` (público) consome o token e marca `email_verificado_em`.

### Recuperação
- **FR-REC-01** — A tela de login exibe o link **"Esqueci minha senha"** para `GET /esqueci-senha`.
- **FR-REC-02** — `POST /esqueci-senha` recebe **método (E-mail|Telefone) + valor**, emite OTP
  `RESET_SENHA` no **canal do método** (quando elegível) e responde **genericamente** (RN-REC-02).
- **FR-REC-03** — `GET /redefinir-senha` exibe os campos **código + nova senha + confirmação**.
- **FR-REC-04** — `POST /redefinir-senha` valida o código e aplica a nova senha (BCrypt), com redirect
  `/login?senhaRedefinida`.

### Segurança/rotas
- **FR-VER-07** — São **públicas** (Art. VII): `/verificar-telefone`, `/verificar-email`,
  `/esqueci-senha`, `/redefinir-senha` (e o `POST` de reenvio) — adicionadas ao `permitAll` do
  `SecurityConfig`, mantendo **CSRF ativo** nesses formulários de página inteira (Art. XI).

---

## 10. Validações de entrada

| Campo | Regra | Mensagem |
|---|---|---|
| `codigo` | obrigatório, **exatamente 6 dígitos** | "Informe o código de 6 dígitos" / "Código inválido" |
| `identificador` (esqueci) | obrigatório; e-mail (contém `@`) ou telefone (dígitos) | "Informe seu e-mail ou telefone" |
| `novaSenha` | obrigatória, **6–72** (RN-AUT-05) | "Crie uma senha" / "A senha deve ter entre 6 e 72 caracteres" |
| `confirmarSenha` | igual a `novaSenha` | "As senhas não conferem" |
| `token` (verificar e-mail) | obrigatório | "Link inválido ou expirado" |

---

## 11. RBAC e rotas (proposta)

| Método | Rota | Acesso | Ação |
|---|---|---|---|
| GET/POST | `/verificar-telefone` | Público | Digitar/validar o OTP do telefone |
| POST | `/verificar-telefone/reenviar` | Público | Reemitir o OTP (com carência) |
| GET | `/verificar-email` | Público | Consumir o link mágico de e-mail |
| GET/POST | `/esqueci-senha` | Público | Solicitar código de reset |
| GET/POST | `/redefinir-senha` | Público | Definir nova senha com o código |

Padrão HTMX onde fizer sentido (contador de reenvio, validação inline) — Art. X. As telas ficam em
`templates/auth/` como as demais (login, registro, ativar).

### 11.1 Design das telas (coerência com o projeto)

As telas seguem o **mesmo padrão visual** das telas de auth atuais — o **card central** de
`auth/ativar.html` (cartão de ~400 px com `border-radius`, centralizado por
`display:flex; align-items:center; justify-content:center` no `body`), com a logo, um título, o alerta
(`.alert`) e o botão primário em `--cal-primary`. Reaproveita as variáveis CSS `--cal-*` já usadas; sem
novo framework/JS custom (Art. X). Especificamente:

- **`/esqueci-senha`** — card central com um **seletor de método** (E-mail | Telefone — dois botões/
  *radios*), o campo correspondente e o botão **"Enviar"**. Um **controle de voltar** no **canto
  superior** do card (seta "←" / "Voltar ao login") retorna a `/login`.
- **`/redefinir-senha`** — card central com **código (6 dígitos)** + **nova senha** + **confirmar** e o
  botão de concluir; mesmo controle de voltar.
- **`/verificar-telefone`** — card central com o campo de **código** e o link **"Reenviar"** (contagem
  regressiva via HTMX). Em telas estreitas, o card usa `max-width` como em `ativar.html`.

---

## 12. Critérios de aceite (Dado / Quando / Então)

- **CA-VER-01 — Cadastro dispara OTP e fica PENDENTE**
  *Dado* um telefone novo, *Quando* alguém envia `POST /registrar`, *Então* cria-se um usuário
  **PENDENTE** e um código de 6 dígitos é enviado (WhatsApp), com redirect para `/verificar-telefone`.

- **CA-VER-02 — Verificação ativa a conta**
  *Dado* o código correto e no prazo, *Quando* enviado em `/verificar-telefone`, *Então*
  `telefone_verificado_em` é gravado, o status vira **ATIVO** e há redirect `/login?verificado`.

- **CA-VER-03 — Código errado é genérico e conta tentativa**
  *Dado* um código incorreto, *Quando* enviado, *Então* a mensagem é genérica e `tentativas` incrementa;
  na **5ª** falha o código é invalidado (exige reenviar).

- **CA-VER-04 — Código expirado**
  *Dado* um código com mais de 10 min, *Quando* enviado, *Então* é rejeitado como inválido.

- **CA-VER-05 — Degradação sem canal**
  *Dado* o WhatsApp **não configurado** (stub) e sem e-mail, *Quando* alguém se cadastra, *Então* a
  conta é criada **ATIVA não verificada** e o fluxo **não** quebra (RN-VER-07).

- **CA-VER-06 — E-mail verificado por link**
  *Dado* um e-mail informado, *Quando* a pessoa clica no link `/verificar-email?token=…` válido,
  *Então* `email_verificado_em` é gravado e o token é consumido.

- **CA-VER-07 — Google já vem com e-mail verificado**
  *Dado* um primeiro login Google, *Então* a conta nasce com `email_verificado_em` preenchido (SPEC-ACE-02).

- **CA-REC-01 — Esqueci a senha (feliz)**
  *Dado* um usuário ATIVO com telefone, *Quando* pede reset e informa o código + nova senha válida,
  *Então* a senha é trocada (BCrypt), o código é consumido e há redirect `/login?senhaRedefinida`.

- **CA-REC-02 — Anti-enumeração**
  *Dado* um identificador **inexistente**, *Quando* alguém usa `/esqueci-senha`, *Então* a resposta é
  **igual** à do caso existente (não revela cadastro) e **nenhum** código é emitido.

- **CA-REC-03 — Reset audita segurança**
  *Quando* uma senha é redefinida, *Então* há um registro de auditoria **SEGURANCA** com o IP.

- **CA-REC-04 — Reset limitado por tentativas**
  *Dado* 5 códigos de reset errados, *Então* o código é invalidado e novo envio respeita a carência.

---

## 13. Casos de borda

- **Reenvio em rajada** → carência de 60 s + teto por janela (RN-VER-06); UI mostra contagem.
- **Dois códigos ativos** (pediu de novo) → o novo **invalida** o anterior da mesma finalidade (RN-VER-09).
- **Conta sem senha** (bot, SPEC-WPP-02) usando "esqueci a senha" → **define** a primeira senha (RN-REC-05).
- **Telefone alterado após verificado** → some `telefone_verificado_em` (reverificar) — indicado, detalhe no perfil.
- **Usuário SUSPENSO/removido** pedindo reset → tratado como inelegível, resposta genérica (RN-REC-01/02).
- **Token de e-mail reaproveitado** → uso único: segunda vez → "link inválido ou expirado".
- **Relógio/timezone** → tudo em `Instant`/`TIMESTAMPTZ` (UTC), como no resto do domínio.

---

## 14. Segurança (Constituição, Art. XI)

- **Hash em repouso** de códigos e tokens (nunca o valor cru); recomendado **HMAC-SHA-256 com pepper**
  para o OTP (baixo espaço de 6 dígitos).
- **Expiração curta** (10 min OTP; validade do link herda o `TokenAtivacao`) + **uso único**.
- **Lockout por tentativas** (RN-VER-05) + **throttle por identificador/IP** (RN-VER-06) — cobre a
  força-bruta online.
- **Anti-enumeração** em "esqueci a senha" (RN-REC-02) e mensagens genéricas de erro (como FR-AUT-06).
- **CSRF ativo** nos formulários de página inteira; rotas apenas **públicas** o necessário (Art. VII).
- **Auditoria** de emissão/consumo de código e de redefinição de senha (`AuditoriaService`, categoria
  SEGURANCA) — trilha para o painel de auditoria já existente.
- **Não** logar código/token/senha em claro (o canal e-mail stub deve mascarar em produção).

---

## 15. Testes (previsto)

- **`VerificacaoServiceTest`** (unit + Mockito): gera código (hash, expira), valida sucesso/erro,
  incrementa tentativas e faz lockout na 5ª; reemissão invalida o anterior; marca telefone/e-mail.
- **`RecuperacaoSenhaServiceTest`**: fluxo feliz; anti-enumeração (usuário inexistente → sem emissão,
  resposta genérica); conta sem senha define a primeira (RN-REC-05); reset audita SEGURANCA.
- **Controllers** (MockMvc + Testcontainers): rotas públicas acessíveis sem login; CSRF exigido nos
  POSTs; redirects (`/login?verificado`, `/login?senhaRedefinida`).
- **Degradação** (RN-VER-07): canal WhatsApp stub → cadastro cria ATIVO não verificado sem quebrar.
- **Contexto** (Testcontainers): schema **V1→V14** válido (gate do `ddl-auto: validate`).

---

## 16. Impacto em specs/documentos

- **SPEC-ACE-01 §9** — marcar os três itens ("recuperação de senha", "verificação de e-mail/telefone",
  "bloqueio por tentativas") como **cobertos por SPEC-ACE-03** (o "bloqueio" apenas para os fluxos de
  código; lockout de login por senha continua fora — §5.4).
- **SPEC-ACE-02** — anotar que o e-mail do Google entra **verificado** e que `/conta/completar` passa a
  disparar a verificação de **telefone** (RN-VER-08).
- **SPEC-WPP-02** — "Acesso à plataforma" e o "esqueci a senha" convergem para **definir senha**; o
  RN-REC-05 formaliza que a conta sem senha do bot pode usar o reset.
- **Plano técnico** — registrar **ADR-16** (modelo híbrido) e a migration **V14**.
- **Roadmap / CLAUDE.md** — novo incremento quando implementado (migration V14; DT de lockout de login).

---

## 17. Rastreabilidade

| Requisito | Artefato (a criar/editar) |
|---|---|
| FR-VER-01, US cadastro | `AuthController`, `UsuarioService.registrarPassageiro` |
| FR-VER-02, 03, 04 | `VerificacaoController`, `VerificacaoService`, `auth/verificar-telefone.html` |
| FR-VER-05, 06 | `ConviteService` (finalidade), `VerificacaoController`, `auth/verificar-email.html` |
| FR-REC-01..04 | `RecuperacaoSenhaController`, `RecuperacaoSenhaService`, `auth/{esqueci,redefinir}-senha.html` |
| FR-VER-07 (rotas) | `SecurityConfig` |
| Modelo | `CodigoVerificacao` + `codigos_verificacao` (V14), `FinalidadeCodigo`/`FinalidadeToken`, colunas em `Usuario` |
| OTP/hash/lockout | `VerificacaoService`, `CodigoVerificacaoRepository` |
| Entrega | `NotificacaoService` + canais `WHATSAPP`/`EMAIL` (existentes) |
| Auditoria | `AuditoriaService.registrarSeguranca` |

---

## 18. Próximos passos

1. **Aprovar a ADR-16** (modelo híbrido) e confirmar a **D5** (bloquear cadastro até verificar × só avisar).
2. Migration **V14** (§6) + enums `FinalidadeCodigo`/`FinalidadeToken` + colunas em `Usuario`.
3. `CodigoVerificacao` + repositório; `VerificacaoService` (gerar/validar/lockout/throttle).
4. Verificação de **telefone** no cadastro (PENDENTE→ATIVO) + tela + reenvio.
5. **"Esqueci a senha"** (`RecuperacaoSenhaService` + controllers + telas) com anti-enumeração.
6. Verificação de **e-mail** por link (`finalidade` no `TokenAtivacao` + consumidor).
7. Integração **SPEC-ACE-02** (e-mail Google verificado; telefone em `/conta/completar`).
8. Testes (§15) + gate `docker build`; atualizar SPEC-ACE-01/08/11, roadmap e CLAUDE.md.

> **Pré-requisitos de infra:** o canal **WhatsApp** depende da **Evolution na VPS** (SPEC-WPP-01 §8); o
> canal **e-mail** depende de integrar **`JavaMailSender`/SMTP** (hoje `NotificacaoEmailCanal` é stub).
> O caminho feliz (telefone/WhatsApp) fica pronto assim que a Evolution subir; o e-mail entra sem mexer
> no resto (basta trocar a implementação do canal).
