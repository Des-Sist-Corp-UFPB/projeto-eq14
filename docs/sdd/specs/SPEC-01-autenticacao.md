# SPEC-01 — Autenticação e Auto-cadastro

| | |
|---|---|
| **Área** | `AUT` |
| **Papéis** | Público (não autenticado) / Todos os autenticados |
| **Status geral** | ✅ Implementado |
| **Constituição** | Artigos VII (RBAC), VIII (auth no banco/BCrypt), XI (CSRF), XIII (`/ping`) |
| **Código** | `AuthController`, `CaladriusUserDetailsService`, `UsuarioAutenticado`, `SecurityConfig`, `UsuarioService.registrarPassageiro`, `Documentos`, `templates/auth/{login,registro}.html` |

---

## 1. Objetivo

Permitir que pessoas entrem no sistema com **e-mail OU telefone** e que pacientes
**se auto-cadastrem** como passageiros, mantendo segurança (BCrypt, CSRF, mensagens
genéricas de erro) e a restrição de que **somente usuários ativos** autenticam.

---

## 2. User stories

- **US-AUT-1** — Como **gestor**, quero entrar com meu telefone **ou** e-mail e senha, para
  acessar os módulos de gestão sem precisar lembrar qual identificador cadastrei.
- **US-AUT-2** — Como **paciente**, quero me cadastrar sozinho informando ao menos nome,
  telefone e senha, para solicitar transporte no futuro sem depender do gestor.
- **US-AUT-3** — Como **usuário autenticado**, quero sair do sistema (logout) com segurança.
- **US-AUT-4** — Como **operador da disciplina**, quero um endpoint público de health check,
  para monitorar se a aplicação está no ar.

---

## 3. Requisitos funcionais

### Login

- **FR-AUT-01** ✅ — O sistema exibe uma página de login pública em `GET /login` com campos
  de **identificador** (e-mail ou telefone) e **senha**.
- **FR-AUT-02** ✅ — O `POST /login` é processado pelo **Spring Security** (não por código de
  controller próprio).
- **FR-AUT-03** ✅ — O sistema **detecta o formato** do identificador: se contém `@`, busca
  por e-mail (case-insensitive); senão, normaliza para apenas dígitos e busca por telefone.
- **FR-AUT-04** ✅ — Apenas usuários **ativos e não removidos** podem autenticar
  (`isEnabled()` ← `status == ATIVO && removido_em IS NULL`).
- **FR-AUT-05** ✅ — Em sucesso, o usuário é redirecionado **sempre** para o painel inicial
  (`/`), independentemente da página de origem (`defaultSuccessUrl("/", true)`).
- **FR-AUT-06** ✅ — Em falha (identificador inexistente, inativo ou senha incorreta), a
  mensagem é **genérica** ("Credenciais inválidas") — não revela qual campo falhou.

### Auto-cadastro de passageiro

- **FR-AUT-07** ✅ — O sistema exibe um formulário público de cadastro em `GET /registrar`.
- **FR-AUT-08** ✅ — O `POST /registrar` cria um usuário com papel **`PASSAGEIRO`** e status
  **`ATIVO`**, senha em BCrypt.
- **FR-AUT-09** ✅ — Em sucesso, redireciona para `GET /login?cadastro` com indicação de
  sucesso (flash attribute).
- **FR-AUT-10** ✅ — Em erro de formato (Bean Validation) ou de regra de negócio (telefone/
  e-mail já em uso, CPF inválido), **reexibe** o formulário com as mensagens.

### Logout e sessão

- **FR-AUT-11** ✅ — `logout` encerra a sessão e redireciona para `/login?logout`.
- **FR-AUT-12** ✅ — Na navbar, o nome do usuário logado é exibido
  (`principal.nomeCompleto`).

### Rotas públicas / health check

- **FR-AUT-13** ✅ — `GET /ping` é **público** e retorna **200 com JSON** (Constituição,
  Art. XIII).
- **FR-AUT-14** ✅ — São públicas também: `/login`, `/registrar`, `/actuator/health` e os
  recursos estáticos (`/webjars/**`, `/css/**`, `/js/**`). Qualquer outra rota exige login.

---

## 4. Regras de negócio

- **RN-AUT-01** — **Telefone é o identificador canônico**: obrigatório e sempre presente; o
  `getUsername()` do principal devolve o telefone, mesmo que o login tenha sido por e-mail.
- **RN-AUT-02** — **E-mail e CPF são opcionais**, mas, quando informados, devem ser únicos
  entre usuários ativos (validado no cadastro — ver SPEC-02).
- **RN-AUT-03** — Telefone é **normalizado para apenas dígitos** antes de buscar/salvar
  (ex.: `(83) 99999-9999` → `83999999999`).
- **RN-AUT-04** — E-mail é comparado/armazenado em **minúsculas** (case-insensitive).
- **RN-AUT-05** — A senha do cadastro é obrigatória, com **6 a 72 caracteres** (limite de 72
  é do BCrypt), e nunca trafega/persiste em texto puro.
- **RN-AUT-06** — A detecção e-mail/telefone usa a heurística "contém `@`"
  (`Documentos.pareceEmail`).

---

## 5. Validações de entrada (RegistroForm)

| Campo | Regra | Mensagem |
|---|---|---|
| `nomeCompleto` | obrigatório, ≤ 160 | "Informe seu nome completo" / "O nome deve ter no máximo 160 caracteres" |
| `telefone` | obrigatório, ≤ 20 | "Informe seu telefone" / "Telefone inválido" |
| `cpf` | opcional, ≤ 14 (com máscara); se preenchido, **CPF válido** (dígitos verificadores) | "CPF inválido" |
| `email` | opcional, formato de e-mail, ≤ 160 | "E-mail inválido" |
| `senha` | obrigatória, 6–72 | "Crie uma senha" / "A senha deve ter entre 6 e 72 caracteres" |

> A validação de CPF (`Documentos.cpfValido`) ocorre no **serviço**: 11 dígitos, não todos
> iguais, dois dígitos verificadores corretos.

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-AUT-01 — Login por telefone**
  *Dado* um gerente ativo com telefone `83999999999` e senha `admin123`,
  *Quando* ele envia o login com esses dados,
  *Então* é autenticado e redirecionado para `/`.

- **CA-AUT-02 — Login por e-mail**
  *Dado* o mesmo gerente com e-mail `admin@caladrius.local`,
  *Quando* ele envia o login com o e-mail e a senha correta,
  *Então* é autenticado igualmente (formato detectado pelo `@`).

- **CA-AUT-03 — Telefone com máscara**
  *Dado* um usuário cujo telefone armazenado é `83999999999`,
  *Quando* ele digita `(83) 99999-9999` no login,
  *Então* a normalização para dígitos encontra o usuário e o login funciona.

- **CA-AUT-04 — Usuário inativo barrado**
  *Dado* um usuário com status `SUSPENSO`,
  *Quando* ele tenta logar com a senha correta,
  *Então* o acesso é negado (`isEnabled() == false`).

- **CA-AUT-05 — Mensagem genérica**
  *Dado* qualquer falha de login (identificador inexistente ou senha errada),
  *Então* a mensagem exibida é genérica e não revela qual campo falhou.

- **CA-AUT-06 — Auto-cadastro feliz**
  *Dado* nome, telefone novo e senha válida,
  *Quando* o formulário de registro é enviado,
  *Então* um usuário `PASSAGEIRO`/`ATIVO` é criado e há redirect para `/login?cadastro`.

- **CA-AUT-07 — Telefone duplicado no cadastro**
  *Dado* um telefone já usado por um usuário ativo,
  *Quando* alguém tenta se cadastrar com ele,
  *Então* o formulário é reexibido com "Telefone já cadastrado".

- **CA-AUT-08 — Acesso anônimo a módulo protegido**
  *Dado* um visitante não autenticado,
  *Quando* ele acessa `/usuarios`,
  *Então* é redirecionado ao login.

- **CA-AUT-09 — Health check público**
  *Quando* qualquer cliente faz `GET /ping`,
  *Então* recebe **200** com corpo JSON, sem precisar de autenticação.

---

## 7. Fluxo de detecção do identificador

```
Identificador digitado
        │
        ▼
 contém "@" ?
   ┌────┴────┐
  sim        não
   │          │
   ▼          ▼
busca por   normaliza p/ dígitos
 e-mail      e busca por telefone
 (lower)        │
   └────┬───────┘
        ▼
 usuário ativo encontrado?
   ┌────┴────┐
  sim        não → UsernameNotFoundException ("Credenciais inválidas")
   │
   ▼
 Spring valida a senha (BCrypt) e checa isEnabled()
```

---

## 8. Casos de borda

- **Identificador nulo/vazio** → tratado como string vazia; não encontra usuário → erro
  genérico.
- **Telefone só com símbolos** (sem dígitos) → normaliza para vazio → não encontra.
- **E-mail com maiúsculas** → encontrado mesmo assim (busca case-insensitive).
- **Usuário sem senha (`hash_senha` nulo)** → não consegue autenticar (sem credencial).
  Cenário possível para usuários criados sem senha em fluxos futuros.

---

## 9. Fora do escopo desta spec

- Recuperação de senha por e-mail (mencionada no domínio, **não implementada**).
- Telas/redirecionamentos por papel após login (hoje todos caem em `/`).
- Verificação de e-mail/telefone (confirmação por link/código).
- Bloqueio por tentativas (rate limiting / lockout).

---

## 10. Rastreabilidade

| Requisito | Artefato |
|---|---|
| FR-AUT-01, 07, 09, 10 | `AuthController` |
| FR-AUT-02, 05, 11, 13, 14 | `SecurityConfig` |
| FR-AUT-03, 04, 06 | `CaladriusUserDetailsService`, `UsuarioAutenticado`, `Documentos` |
| FR-AUT-08 | `UsuarioService.registrarPassageiro` |
| Validações | `RegistroForm`, `Documentos.cpfValido` |
| Telas | `templates/auth/login.html`, `templates/auth/registro.html` |
