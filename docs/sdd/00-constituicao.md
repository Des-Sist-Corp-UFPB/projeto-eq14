# Constituição do Projeto — CALADRIUS

> A **Constituição** reúne os princípios **invioláveis** do projeto. São as decisões já
> tomadas e estabilizadas que toda especificação, plano técnico e implementação devem
> respeitar. Mudar um princípio aqui não é "mais uma tarefa": é uma decisão arquitetural
> consciente, que precisa de justificativa explícita e atualização coordenada de código,
> migrations e documentação.

Cada artigo abaixo enuncia o princípio, o **porquê** dele existir e o que ele **proíbe na
prática**. A fonte primária destes princípios é a [`CLAUDE.md`](../../CLAUDE.md) e os
documentos técnicos em [`docs/`](../).

---

## Artigo I — Stack e versões são fixas dentro do incremento

O sistema é **Java 21 + Spring Boot 3.4.5**, com **Thymeleaf + HTMX 2.0** na view,
**Bootstrap 5.3** no estilo, **PostgreSQL 16** no banco, **Flyway** nas migrations e
**Spring Security 6** na segurança.

**Por quê:** o projeto foi adaptado do boilerplate "Sistema Mercado" do professor, mantendo
a mesma stack e arquitetura; o ambiente de produção da disciplina é padronizado.

**Proíbe:** trocar o paradigma de view por uma SPA (React/Vue), introduzir um ORM
alternativo, ou subir versões major sem validar o build de produção. Novas dependências
passam pelo SAST (`mvn verify -Psecurity`) e pelo OWASP Dependency-Check.

---

## Artigo II — Arquitetura em camadas, sem atalhos

O fluxo é **Controller → Service → Repository → PostgreSQL**, com responsabilidades
estritas (detalhado em [`ARCHITECTURE.md`](../ARCHITECTURE.md)):

- **Controller** recebe HTTP, valida DTO com `@Valid`, delega ao Service e devolve template
  (página completa **ou** fragmento HTMX). **Não contém regra de negócio.**
- **Service** (`@Service`, `@Transactional`) concentra **toda** a regra de negócio e lança
  exceções de domínio.
- **Repository** (`JpaRepository`) é a única porta para o banco.
- **Domain (entidade JPA)** não carrega lógica de negócio complexa; usa
  `@PrePersist`/`@PreUpdate` apenas para auditoria/defaults.

**Por quê:** previsibilidade, testabilidade (services testáveis isoladamente) e separação
clara entre transporte, negócio e persistência.

**Proíbe:** regra de negócio em controller ou template; acesso a repository direto do
controller para **mutações** (consultas simples de leitura para montar o dashboard são
toleradas, como em `HomeController`); SQL/JPQL concatenado com entrada do usuário.

---

## Artigo III — Persistência por soft-delete (onde aplicável)

**`usuarios`** e **`veiculos`** **nunca** são apagados fisicamente: "excluir" preenche a
coluna `removido_em`. Todas as consultas filtram `removido_em IS NULL`. A unicidade
(telefone, e-mail, CPF, placa) é garantida por **índices únicos parciais** — válidos apenas
entre registros ativos.

**Por quê:** preserva histórico e integridade referencial (viagens apontam para motoristas
e veículos que podem ter sido "removidos") e permite reaproveitar um identificador depois
que o registro anterior foi desativado.

**Proíbe:** `DELETE` físico de usuário ou veículo; consultas que ignorem o filtro de
soft-delete; índices únicos totais sobre colunas com soft-delete.

> **Exceção consagrada:** **cidades** são dados de referência e usam **remoção física**
> (`CidadeService.excluir` faz `delete`). **Viagens** também são removidas fisicamente
> (com cascade nos assentos). Isso é intencional — não confundir com o soft-delete acima.

---

## Artigo IV — Migrations Flyway são imutáveis e incrementais

Cada alteração de schema é uma **nova** migration `V{n}__{descricao_snake_case}.sql`.
Migrations já aplicadas **nunca** são editadas (o Flyway compara checksums).

- `V1__criar_tabela_produto.sql` — herdada do boilerplate, **intocável** (já aplicada no
  banco compartilhado da disciplina).
- `V2__criar_schema_caladrius.sql` — schema do domínio.
- `V3__remover_produto_e_seed_cidades.sql` — limpeza do boilerplate + cidades de referência.
- `V4__…` em diante — toda mudança futura.

**Por quê:** o banco de produção é **compartilhado entre equipes**; reescrever uma migration
aplicada quebra o histórico do Flyway para todos.

**Proíbe:** editar `V1`, `V2`, `V3` (ou qualquer migration já commitada/aplicada); alterar
schema direto no banco sem migration correspondente.

---

## Artigo V — O schema do banco é o contrato; o Hibernate só valida

Em produção a aplicação roda com **`ddl-auto: validate`**. O Hibernate **não cria nem altera**
tabelas: ele apenas confere se cada entidade JPA bate com o schema criado pelo Flyway. Se não
bater, **a aplicação não sobe**.

**Por quê:** garante que o modelo de código e o banco compartilhado nunca divirjam
silenciosamente.

**Proíbe:** introduzir/alterar um campo de entidade sem a migration Flyway correspondente;
confiar em `ddl-auto: update` para "consertar" o schema.

---

## Artigo VI — Identidade, enums e UUIDs seguem padrões fixos

- **PKs são UUID**, geradas via `GenerationType.UUID` (Java) com `DEFAULT gen_random_uuid()`
  no banco — função **nativa** do PostgreSQL 13+, que **não exige a extensão `pgcrypto`**
  (evita problema de permissão no banco compartilhado).
- **Enums são `VARCHAR`** mapeados com `@Enumerated(EnumType.STRING)`; os valores são os
  **nomes das enums Java em MAIÚSCULAS**, com `CHECK` nas tabelas centrais.

**Por quê:** robustez com `ddl-auto: validate` e independência de extensões do banco.

**Proíbe:** usar enum nativo do PostgreSQL; depender de `pgcrypto`; persistir o `ordinal()`
de uma enum (sempre o nome).

---

## Artigo VII — Controle de acesso baseado em papéis (RBAC)

O sistema tem três papéis: **PASSAGEIRO**, **MOTORISTA**, **GERENTE**. Um usuário pode ter
mais de um. A autorização é decidida a partir dos papéis (Spring Security; papel `GERENTE`
→ autoridade `ROLE_GERENTE`).

- Rotas públicas: `/login`, `/registrar`, `/ping`, `/actuator/health`, estáticos.
- Módulos de gestão (`/usuarios`, `/veiculos`, `/cidades`, `/viagens`): **exclusivos do
  `GERENTE`**.
- Qualquer outra rota exige apenas estar autenticado.

**Por quê:** o incremento atual concentra a operação no gestor; passageiro e motorista têm
telas próprias previstas para incrementos futuros.

**Proíbe:** expor módulos de gestão sem checagem de papel; criar telas privilegiadas fora
do `SecurityConfig`.

---

## Artigo VIII — Autenticação no banco, login flexível, senhas com BCrypt

A autenticação lê os usuários do **PostgreSQL** (substitui o `InMemoryUserDetailsManager`
do boilerplate). O login aceita **e-mail OU telefone**: o formato é detectado
automaticamente (contém `@` → e-mail; senão → telefone, normalizado para dígitos). Senhas
são sempre hash **BCrypt**. Somente usuários **ATIVOS** autenticam.

**Por quê:** decisão do redesenho v3 da equipe — o telefone é o identificador canônico
(obrigatório e sempre presente); o e-mail é opcional e também serve para login.

**Proíbe:** armazenar senha em texto puro; revelar na mensagem de erro se o problema foi o
identificador ou a senha (mensagem genérica "Credenciais inválidas"); autenticar usuário
não-ativo.

---

## Artigo IX — Tudo em português

Identificadores de domínio, métodos, colunas, endpoints REST, comentários, mensagens de
validação e templates são em **português**. Endpoints usam os termos do domínio
(`/veiculos`, `/viagens`, `/cidades`, `/usuarios`).

**Por quê:** o domínio é local (transporte municipal de saúde) e a disciplina é em
português; reduz atrito de tradução mental.

**Proíbe:** misturar inglês no domínio (nomes de entidade, colunas, rotas). Termos técnicos
consagrados do framework (ex.: `Controller`, `Repository`, `@Transactional`) permanecem como
são.

---

## Artigo X — HTMX com fragmentos, sem JavaScript customizado

A interface usa **renderização no servidor**: o controller devolve a **página completa** em
requisição normal e um **fragmento** quando há o header `HX-Request`. Templates ficam em
`templates/{modulo}/` com `fragments/{tabela,linha,form}.html`.

**Por quê:** simplicidade e depurabilidade; toda a lógica de tela fica no servidor
(Thymeleaf) com acesso direto ao contexto Spring.

**Proíbe:** introduzir uma SPA ou JavaScript de aplicação customizado para resolver o que o
HTMX já cobre; quebrar o padrão "página completa vs fragmento".

---

## Artigo XI — Segurança como requisito, não como verniz

- **Escaping automático** nos templates (`th:text`, nunca `th:utext` com dado de usuário).
- **CSRF habilitado** para formulários de página inteira (login, cadastro); desabilitado
  **apenas** para os endpoints HTMX de mutação (mesmo padrão do boilerplate).
- **Sem segredos no código**: variáveis sensíveis em `.env`.
- **SAST no pipeline**: SpotBugs + FindSecBugs + OWASP Dependency-Check
  (`mvn verify -Psecurity`); build falha para CVSS ≥ 7.0.

Detalhes em [`SECURITY.md`](../SECURITY.md).

**Proíbe:** desabilitar CSRF globalmente; hardcode de credenciais; ignorar achados de SAST
sem supressão **justificada** (em `spotbugs-exclude.xml` / `owasp-suppressions.xml`).

---

## Artigo XII — O gate do CI é a compilação

O `.github/workflows/deploy.yml` faz o build da imagem (`docker/Dockerfile` → `mvn clean
package -DskipTests`), publica no GHCR e implanta a cada `push` na `main`. **O gate é a
COMPILAÇÃO.**

**Por quê:** uma quebra de compilação interrompe o deploy de produção da disciplina.

**Proíbe:** dar `push` na `main` sem validar `docker build -f docker/Dockerfile .` localmente;
introduzir mudança que não compila no mesmo perfil do Dockerfile.

---

## Artigo XIII — `/ping` é um contrato público

`GET /ping` deve retornar **200 com JSON** e permanecer **público** (sem autenticação). É o
health check exigido pela disciplina.

**Proíbe:** proteger, remover ou alterar o contrato de resposta de `/ping`.

---

## Artigo XIV — Restrições do ambiente compartilhado

O banco de **produção** (`eq14`) é **compartilhado entre equipes**. Por isso o **pool de
conexões em produção é limitado a 5** (`application-prod.yml`).

**Por quê:** evitar esgotar as conexões do servidor compartilhado.

**Proíbe:** elevar o pool de produção sem alinhar com a disciplina; abrir conexões fora do
controle do pool.

---

## Como emendar a Constituição

1. Documente a motivação (que princípio muda e por quê) — idealmente como uma ADR no
   [Plano Técnico](02-plano-tecnico.md).
2. Atualize **coordenadamente** código, migrations e specs afetadas.
3. Atualize este artigo e a [`CLAUDE.md`](../../CLAUDE.md), que é a memória de longo prazo
   do projeto.
4. Valide o gate de compilação antes do `push`.
