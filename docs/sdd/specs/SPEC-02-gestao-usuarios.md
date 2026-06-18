# SPEC-02 — Gestão de Usuários

| | |
|---|---|
| **Área** | `USU` |
| **Papéis** | GERENTE |
| **Status geral** | ✅ Implementado |
| **Constituição** | Artigos III (soft-delete), VII (RBAC), VIII (BCrypt), X (HTMX) |
| **Código** | `UsuarioController`, `UsuarioService`, `Usuario`, `UsuarioForm`, `UsuarioRepository`, `Documentos`, `templates/usuarios/**` |

---

## 1. Objetivo

Dar ao **gerente** controle completo sobre os usuários do sistema: criar, listar/buscar,
editar e excluir (logicamente), incluindo a atribuição de **papéis** (RBAC) e **status**.

---

## 2. User stories

- **US-USU-1** — Como gerente, quero **cadastrar** usuários (motoristas, outros gerentes,
  passageiros) definindo seus papéis, para montar a equipe e a base de pacientes.
- **US-USU-2** — Como gerente, quero **buscar** usuários por nome, telefone, e-mail ou CPF,
  para encontrá-los rapidamente em uma lista paginada.
- **US-USU-3** — Como gerente, quero **editar** um usuário (inclusive trocar papéis e
  status), para corrigir dados ou suspender acessos.
- **US-USU-4** — Como gerente, quero **excluir** um usuário sem perder o histórico, para
  desativá-lo preservando a integridade das viagens em que ele aparece.

---

## 3. Requisitos funcionais

- **FR-USU-01** ✅ — `GET /usuarios` lista usuários **ativos** paginados (**10 por página**),
  ordenados por nome. Em requisição HTMX (`HX-Request`) devolve o **fragmento** da tabela;
  caso contrário, a página completa.
- **FR-USU-02** ✅ — `GET /usuarios?busca=...` filtra por **nome, telefone, e-mail ou CPF**
  (case-insensitive, "contém"). Busca vazia retorna todos os ativos.
- **FR-USU-03** ✅ — `GET /usuarios/fragmento-tabela` devolve o fragmento da tabela (usado
  para paginação/busca incremental via HTMX).
- **FR-USU-04** ✅ — `GET /usuarios/novo` devolve o **modal** de formulário em branco.
- **FR-USU-05** ✅ — `GET /usuarios/{id}/editar` devolve o modal preenchido com os dados do
  usuário, **sem expor o hash da senha** (campo senha vem vazio).
- **FR-USU-06** ✅ — `POST /usuarios` cria o usuário; em sucesso devolve o **fragmento da
  linha** nova; em erro de validação/regra devolve o **modal** com mensagens.
- **FR-USU-07** ✅ — `PUT /usuarios/{id}` atualiza o usuário com a mesma semântica de
  resposta (linha em sucesso, modal em erro).
- **FR-USU-08** ✅ — `DELETE /usuarios/{id}` faz **soft-delete** e responde `200`; se o
  usuário não existir, responde `404`.
- **FR-USU-09** ✅ — O formulário oferece as opções de **papéis** (`Papel.values()`) e
  **status** (`StatusUsuario.values()`).
- **FR-USU-10** ✅ — Todas as rotas `/usuarios/**` exigem papel **`GERENTE`**.

---

## 4. Regras de negócio

- **RN-USU-01** — **Senha obrigatória na criação**; na edição, senha em branco **mantém** a
  atual (só re-hasheia se vier preenchida).
- **RN-USU-02** — **Telefone obrigatório e único** entre ativos. Na edição, a checagem de
  unicidade só dispara se o telefone **mudou**.
- **RN-USU-03** — **CPF opcional**; se informado, deve ser **válido** (dígitos verificadores)
  e **único** entre ativos. Normalizado para apenas dígitos; vazio vira `null`.
- **RN-USU-04** — **E-mail opcional**; se informado, **único** entre ativos
  (case-insensitive). Normalizado para minúsculas e `trim`; vazio vira `null`.
- **RN-USU-05** — **Papéis**: se nenhum for informado, assume `PASSAGEIRO` por padrão.
- **RN-USU-06** — **Status**: se não informado, assume `ATIVO` na criação. Na edição, só
  altera se um status for informado.
- **RN-USU-07** — **Exclusão é lógica** (soft-delete): preenche `removido_em`; o usuário some
  das listas e libera seus identificadores únicos para reutilização.
- **RN-USU-08** — Nome é `trim`ado antes de salvar.

---

## 5. Validações de entrada (UsuarioForm)

| Campo | Regra (Bean Validation) | Regra adicional no serviço |
|---|---|---|
| `nomeCompleto` | obrigatório, ≤ 160 | `trim` |
| `telefone` | obrigatório, ≤ 20 | normaliza p/ dígitos; único entre ativos |
| `cpf` | opcional, ≤ 14 | valida dígitos verificadores; único entre ativos |
| `email` | opcional, formato e-mail, ≤ 160 | lower+trim; único entre ativos |
| `senha` | ≤ 72 | **obrigatória na criação**; em branco na edição mantém |
| `papeis` | — | default `{PASSAGEIRO}` se vazio |
| `status` | — | default `ATIVO` na criação |

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-USU-01 — Criar sem senha falha**
  *Dado* um formulário de criação sem senha,
  *Quando* o gerente envia,
  *Então* a operação é rejeitada com "A senha é obrigatória ao criar um usuário".

- **CA-USU-02 — Criar motorista**
  *Dado* nome, telefone novo, senha e papel `MOTORISTA`,
  *Quando* o gerente cria o usuário,
  *Então* ele é salvo ativo, com BCrypt, e passa a aparecer como motorista selecionável em
  viagens (ver SPEC-05).

- **CA-USU-03 — Telefone duplicado**
  *Dado* um telefone já usado por um ativo,
  *Quando* o gerente tenta criar/editar outro usuário com ele,
  *Então* recebe "Telefone já cadastrado: ...".

- **CA-USU-04 — CPF inválido**
  *Dado* um CPF com dígitos verificadores incorretos,
  *Quando* o gerente salva,
  *Então* recebe "CPF inválido".

- **CA-USU-05 — Editar mantendo senha**
  *Dado* um usuário existente,
  *Quando* o gerente edita deixando a senha em branco,
  *Então* os demais campos mudam e o hash da senha permanece o mesmo.

- **CA-USU-06 — Soft-delete libera telefone**
  *Dado* um usuário ativo com telefone X,
  *Quando* o gerente o exclui e depois cadastra outro usuário com o mesmo telefone X,
  *Então* o novo cadastro é aceito (unicidade só entre ativos).

- **CA-USU-07 — Busca multi-campo**
  *Dado* usuários cadastrados,
  *Quando* o gerente busca por um trecho de e-mail,
  *Então* a lista retorna os que casam em nome, telefone, e-mail **ou** CPF.

- **CA-USU-08 — Excluir inexistente**
  *Quando* o gerente exclui um id que não existe,
  *Então* a resposta é `404`.

- **CA-USU-09 — Acesso restrito**
  *Dado* um usuário sem papel `GERENTE`,
  *Quando* acessa `/usuarios`,
  *Então* o acesso é negado.

---

## 7. Casos de borda

- **Edição que não muda o telefone** → não dispara erro de unicidade (a checagem detecta que
  não mudou).
- **CPF/e-mail apagados na edição** (campo enviado vazio) → viram `null`, liberando a
  unicidade.
- **Papéis desmarcados** → cai no default `PASSAGEIRO` (nunca fica sem papel).
- **Excluir a si mesmo / o último gerente** → **não há trava** hoje; ver §9.

---

## 8. Estados do usuário

```
PENDENTE ─┐
          ├─► ATIVO ──► (SUSPENSO | INATIVO)
          │      ▲           │
          └──────┴───────────┘   (transições livres via edição)
                  │
                  └─► [soft-delete] removido_em ≠ null  (some das listas)
```
Apenas **ATIVO** autentica (ver SPEC-01, FR-AUT-04).

---

## 9. Fora do escopo / lacunas conhecidas

- **Sem trava** impedindo excluir/suspender o **último gerente** ou a si mesmo (risco de
  lockout). Candidato a regra futura.
- **Perfis detalhados** (`perfis_passageiro/motorista/gerente`) não são editados aqui.
- Sem reativação explícita de usuário removido (exigiria endpoint próprio).
- Sem auditoria de quem concedeu papéis (colunas `concedido_em/por` existem no banco mas não
  são gerenciadas pela entidade).

---

## 10. Rastreabilidade

| Requisito | Artefato |
|---|---|
| FR-USU-01..09 | `UsuarioController` |
| RN-USU-01..08 | `UsuarioService` |
| Modelo | `Usuario`, `papeis_usuario` (V2) |
| Validação | `UsuarioForm`, `Documentos` |
| Consultas | `UsuarioRepository` |
| Telas | `templates/usuarios/{lista,fragments/*}.html` |
| Testes | `UsuarioServiceTest` |
