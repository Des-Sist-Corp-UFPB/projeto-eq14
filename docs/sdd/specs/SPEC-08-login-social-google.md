# SPEC-08 — Login Social com Google (OAuth2 / OIDC)

| | |
|---|---|
| **Área** | `AUT` (autenticação) / `IDP` (identidades externas) |
| **Papéis** | Todos (login); PASSAGEIRO (auto-provisão); GERENTE/SYSADMIN (vínculo a conta existente) |
| **Status geral** | ✅ Implementado — migration **V10** (`identidades_oauth` + `perfil_incompleto`), `oauth2Login` + auto-provisão + `/conta/completar`. Requer `GOOGLE_CLIENT_ID`/`SECRET` no ambiente para ativar. |
| **Constituição** | Artigos II (camadas), IV (migrations forward-only), VI (UUID/enums), VIII (segurança) |
| **Relacionada** | [SPEC-01](SPEC-01-autenticacao.md) (login senha), [SPEC-02](SPEC-02-gestao-usuarios.md) (usuários), [SPEC-07](SPEC-07-endereco-do-passageiro.md) (perfil do passageiro) |
| **Código (a criar)** | `IdentidadeOauth` (entidade), `identidades_oauth` (V10), `CaladriusOidcUserService`, ajustes em `SecurityConfig`, tela de login, fluxo de "completar cadastro" |

---

## 1. Objetivo

Permitir que o usuário **entre com a conta Google** ("Continuar com Google") **além** do login por
e-mail/telefone + senha já existente. A integração com um **serviço externo** (Google Identity, via
OpenID Connect) atende ao requisito de avaliação *"integração com algum serviço externo"* e reduz
atrito de cadastro para o passageiro.

> **Motivação (do dono do projeto):** *"fazer a integração com o google para que o usuário possa
> fazer login utilizando seu gmail."*

O login social **coexiste** com o `formLogin` atual (stateful, sessão/JSESSIONID) — não o substitui.

---

## 2. Decisões de arquitetura (✅ aprovadas na revisão)

- **(1) Protocolo: Spring Security OAuth2 Client (OIDC nativo).** Usa
  `spring-boot-starter-oauth2-client` e `.oauth2Login(...)` na `SecurityFilterChain`, ao lado do
  `formLogin`. Mantém o modelo **stateful**: ao final do fluxo OAuth o Spring cria a **mesma
  `HttpSession`** e o principal continua sendo o nosso `UsuarioAutenticado`. **Firebase foi
  descartado** (desenhado para apps stateless/SPA; atritaria com a sessão e adicionaria SDK externo).
- **(2) Vínculo por tabela dedicada `identidades_oauth`** (não por e-mail solto). Chave de
  identidade = par **(`provedor`, `subject_id`)**, onde `subject_id` é o `sub` **imutável** do Google
  (o e-mail pode mudar; o `sub` não). Suporta múltiplos provedores no futuro e é auditável.
- **(3) Auto-provisão de PASSAGEIRO** no primeiro login Google quando não há conta. A conta nasce
  **com perfil incompleto** (ver §2.2) porque o Google **não fornece telefone**, e `telefone` é
  **NOT NULL e único** em `usuarios`.

### 2.1 Resolução de identidade (ordem obrigatória no callback)

No retorno do Google (com `sub`, `email` verificado, `name`), o `CaladriusOidcUserService` resolve
nesta ordem:

1. **Por identidade** — existe `identidades_oauth(GOOGLE, sub)`? → carrega o `Usuario` vinculado. **Fim.**
2. **Por e-mail (vínculo de conta existente)** — existe `Usuario` ativo com esse `email` (verificado
   pelo Google)? → **cria o vínculo** `identidades_oauth(GOOGLE, sub, usuario)` e autentica. Isso
   evita conta duplicada e "adota" contas criadas por convite/senha.
3. **Auto-provisão** — não existe conta → cria `Usuario` PASSAGEIRO com `email`, `nome_completo`
   (do Google), **sem senha** (`hash_senha` nulo, já permitido) e **perfil incompleto**; cria o
   vínculo `identidades_oauth`. Registra auditoria `SEGURANCA` (`LOGIN_GOOGLE_AUTOPROVISAO`).

> **Importante:** o passo 2 só casa e-mail **verificado** (`email_verified = true` no id_token).
> E-mail não verificado cai na auto-provisão (passo 3) com o cuidado de não colidir com unicidade.

### 2.2 Perfil incompleto (telefone obrigatório)

Como `usuarios.telefone` é obrigatório/único e o Google não o fornece, a conta auto-provisionada
nasce sem telefone. Tratamento:

- Um **marcador de perfil incompleto** indica que faltam dados obrigatórios (telefone).
  **Decisão (✅ Opção A):** coluna dedicada `perfil_incompleto BOOLEAN NOT NULL DEFAULT FALSE`
  em `usuarios`, **ortogonal** ao `status`. Não reaproveitamos `StatusUsuario.PENDENTE` (Opção B
  descartada) porque `PENDENTE` já é usado no fluxo de convite e porque só `ATIVO` autentica
  (`Usuario.isAtivo()`): uma conta `PENDENTE` não conseguiria logar para *então* completar o
  cadastro. Um usuário pode ser `ATIVO` **e** `perfil_incompleto = true` ao mesmo tempo.
- **`telefone` passa a aceitar `NULL`** (a V10 remove o `NOT NULL`): a conta auto-provisionada
  nasce sem telefone (o Google não o fornece) e o preenche em `/conta/completar`. Os fluxos
  normais (cadastro público, criação pelo gerente) **continuam exigindo** telefone na camada de
  serviço — a unicidade parcial entre ativos é preservada (NULLs são distintos no índice único).
- Enquanto incompleto, um **filtro** (`PerfilIncompletoFilter`) redireciona o usuário para `/conta/completar`
  (informar telefone) antes de acessar funções de negócio. Casa com a SPEC-07: o passageiro **já**
  não pode solicitar transporte sem endereço; aqui, não navega sem telefone.
- Ao salvar o telefone (validado e único), o marcador é limpo e o usuário segue normalmente.

### 2.3 Esquema previsto (migration V10 — aditiva)

```sql
-- V10__criar_identidades_oauth.sql  (forward-only, sem extensões/superusuário)
CREATE TABLE identidades_oauth (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario         UUID         NOT NULL REFERENCES usuarios(id),
    provedor        VARCHAR(20)  NOT NULL,            -- 'GOOGLE' (enum ProvedorOauth)
    subject_id      VARCHAR(255) NOT NULL,            -- 'sub' imutável do provedor
    email_provedor  VARCHAR(160),                     -- e-mail no provedor (informativo)
    vinculado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_identidades_provedor CHECK (provedor IN ('GOOGLE')),
    CONSTRAINT uq_identidades_provedor_subject UNIQUE (provedor, subject_id)
);
CREATE INDEX ix_identidades_usuario ON identidades_oauth(usuario);

-- Marcador de perfil incompleto (Opção A) — ortogonal ao status.
ALTER TABLE usuarios ADD COLUMN perfil_incompleto BOOLEAN NOT NULL DEFAULT FALSE;

-- Telefone passa a aceitar NULL: a conta auto-provisionada por Google nasce sem
-- telefone e o completa depois. Os fluxos normais ainda exigem telefone no serviço.
ALTER TABLE usuarios ALTER COLUMN telefone DROP NOT NULL;
```

> Segue as decisões de banco do projeto: PK UUID via `gen_random_uuid()` nativo, enum como
> `VARCHAR` + `CHECK`, migration **aditiva** (V1 intocada), sem extensões. **V10** é a próxima
> (após V9 de linhas/viagens).

### 2.4 Configuração (segredos fora do código)

```yaml
# application.yml (valores via variáveis de ambiente — NÃO commitar segredo)
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: [openid, email, profile]
```

- `client-id`/`client-secret` vêm do **Google Cloud Console** (tela de consentimento OAuth +
  credenciais "OAuth 2.0 Client ID" do tipo *Web application*).
- **Redirect URI autorizada**: `https://<host>/login/oauth2/code/google` (e a variante de dev,
  `http://localhost:8080/login/oauth2/code/google`).
- Segredos via env (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`); ausentes em dev ⇒ o botão Google
  pode ficar oculto (degradação graciosa), nunca derrubar a app.

---

## 3. User stories

- **US-01** — Como **visitante**, quero clicar em *"Continuar com Google"* na tela de login para
  entrar sem digitar senha.
- **US-02** — Como **usuário já cadastrado** (por convite/senha) com o mesmo e-mail do Google,
  quero que meu primeiro login Google **vincule** à minha conta existente, sem duplicar.
- **US-03** — Como **novo passageiro**, quero que minha conta seja criada automaticamente no
  primeiro login Google e ser guiado a **completar meu telefone**.
- **US-04** — Como **gerente/sysadmin**, quero que o login social respeite o **RBAC** (a conta
  vinculada mantém seus papéis; auto-provisão entra apenas como PASSAGEIRO).

---

## 4. Requisitos funcionais

- **FR-AUT-10** — Exibir botão *"Continuar com Google"* na tela `/login`, iniciando
  `/oauth2/authorization/google`.
- **FR-AUT-11** — Processar o callback OIDC e resolver a identidade conforme §2.1 (identidade →
  e-mail → auto-provisão).
- **FR-AUT-12** — Persistir o vínculo em `identidades_oauth` no primeiro acesso (auto-provisão **ou**
  adoção por e-mail).
- **FR-AUT-13** — Auto-provisionar PASSAGEIRO com perfil incompleto quando não houver conta.
- **FR-AUT-14** — Bloquear o acesso a funções de negócio enquanto `perfil_incompleto`, redirecionando
  para `/conta/completar` (coleta e valida telefone único).
- **FR-AUT-15** — Registrar auditoria `SEGURANCA` nos eventos: `LOGIN_GOOGLE_SUCESSO`,
  `LOGIN_GOOGLE_VINCULO` (adoção por e-mail), `LOGIN_GOOGLE_AUTOPROVISAO`.

---

## 5. Regras de negócio

- **RN-AUT-10** — A chave de identidade externa é **(`provedor`, `subject_id`)**, única. Nunca o
  e-mail isolado.
- **RN-AUT-11** — Adoção por e-mail (passo 2) exige **e-mail verificado** pelo provedor.
- **RN-AUT-12** — Conta auto-provisionada recebe **apenas** o papel `PASSAGEIRO`; nunca papéis de
  gestão.
- **RN-AUT-13** — Usuário **removido** (soft-delete) ou **suspenso** não autentica por Google
  (mesma regra do login por senha — `isAtivo()`).
- **RN-AUT-14** — Usuário OIDC tem `hash_senha` nulo: o login por senha permanece indisponível até
  que ele defina uma senha (fluxo futuro, fora do escopo).
- **RN-AUT-15** — O telefone informado em `/conta/completar` segue as mesmas validações do cadastro
  (somente dígitos, único entre ativos).

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-01** — *Dado* um Google `sub` já vinculado, *quando* o usuário faz login Google, *então* ele
  entra na conta vinculada sem criar nada novo.
- **CA-02** — *Dado* um `Usuario` ativo com e-mail `x@gmail.com` e **sem** vínculo, *quando* ele faz
  login Google com e-mail verificado `x@gmail.com`, *então* o sistema cria `identidades_oauth` e o
  autentica **na conta existente** (sem duplicar).
- **CA-03** — *Dado* um e-mail Google sem conta, *quando* ele faz o primeiro login, *então* é criado
  um `Usuario` PASSAGEIRO com `perfil_incompleto = true` e ele é levado a `/conta/completar`.
- **CA-04** — *Dado* um usuário com perfil incompleto, *quando* ele tenta acessar `/viagens` (ou
  qualquer função de negócio), *então* é redirecionado para `/conta/completar`.
- **CA-05** — *Dado* o usuário em `/conta/completar`, *quando* informa um telefone válido e único,
  *então* `perfil_incompleto` vira `false` e ele acessa o sistema normalmente.
- **CA-06** — *Dado* um usuário **suspenso**, *quando* tenta login Google, *então* é barrado (mesma
  regra do login por senha).
- **CA-07** — *Dado* um e-mail Google **não verificado** que coincide com conta existente, *quando*
  faz login, *então* **não** há adoção automática (cai na auto-provisão / tratamento de colisão).

---

## 7. Pontos a definir na implementação

- ~~Marcador de perfil incompleto~~ — **resolvido (Opção A)**: coluna `perfil_incompleto BOOLEAN`
  + `telefone` nullable (ver §2.2/§2.3).
- **Colisão de unicidade na auto-provisão** quando o e-mail não-verificado bate com conta existente:
  como o e-mail não-verificado **não** é gravado na conta auto-provisionada (RN-AUT-11), não há
  colisão de unicidade; o usuário fica sem e-mail até completar/vincular. (CA-07.)
- **Botão Google ausente sem segredos** (dev sem `GOOGLE_CLIENT_ID`): ocultar via flag de template.

---

## 8. Fora do escopo (futuro)

- Outros provedores (Apple, Microsoft) — a tabela já comporta via `provedor`.
- Definir senha local para conta criada por Google (mesclar credenciais).
- Vincular/desvincular provedores na tela de perfil.
- 2FA/MFA.

---

## 9. Sequenciamento

Implementada como **migration V10** (aditiva, após V9). Não depende de outras specs pendentes; toca
em SPEC-01 (login) e SPEC-02 (usuários) e dialoga com a SPEC-07 (perfil do passageiro: "não navega
sem telefone" espelha "não solicita sem endereço"). **Gate de deploy = compilação** — validar com
`mvn clean package -DskipTests` / `docker build` antes do push na `main`.

---

## 10. Rastreabilidade (planejada)

| Requisito | Artefato (a criar) |
|---|---|
| FR-AUT-10 | botão "Continuar com Google" em `templates/auth/login.html` |
| FR-AUT-11..12 | `CaladriusOidcUserService`, `IdentidadeOauth`, `IdentidadeOauthRepository`, `identidades_oauth` (V10) |
| FR-AUT-13 | auto-provisão em `UsuarioService`/`OidcUserService` |
| FR-AUT-14 | filtro/interceptor de perfil incompleto + `ContaController` (`/conta/completar`) |
| FR-AUT-15 | `AuditoriaService.registrarSeguranca(...)` |
| Config | `application.yml` (registration google), `SecurityConfig.oauth2Login(...)` |
| Modelo | `identidades_oauth` (V10), `usuarios.perfil_incompleto` (V10) |
