# 02 — Testes de Web / API (HTTP, RBAC, HTMX)

Testes de **integração** com `@SpringBootTest` + **MockMvc** + **Testcontainers**: sobem o contexto
completo contra um PostgreSQL real (migrations V1→V15 aplicadas pelo Flyway) e exercem os endpoints
de verdade — controller → serviço → repositório → SQL → template Thymeleaf.

**Não há mock de serviço aqui.** É o que dá confiança de que rota, RBAC, CSRF, transação, SQL e
renderização combinam entre si.

Base comum: `IntegracaoWebTestBase` (ver [README §3](README.md#3-os-três-tipos-de-teste-e-quando-usar-cada-um)).

---

## O que estes testes verificam além do "200 OK"

1. **RBAC por papel** — cada área tem um teste que confirma que quem **não** pode entrar recebe 403
   (e que o anônimo é levado ao login).
2. **CSRF** — mutação em rota administrativa exige token; há um teste que confirma o **403 sem CSRF**.
3. **Padrão HTMX (Art. X)** — a mesma rota devolve **página inteira** na navegação e **fragmento**
   com o header `HX-Request`; os dois caminhos são exercidos.
4. **Caminhos de erro** — validação de formulário, regra de negócio e 404 têm cenário próprio. É onde
   os bugs de verdade moram.

---

## `PaginasPublicasTest` / `PaginasAutenticadasTest` / `ControleAcessoTest`

O mapa de acesso do sistema: o que é público (`/login`, `/registrar`, `/ping`, `/actuator/health`),
o que exige autenticação e o que exige papel. `ControleAcessoTest` é a rede de segurança do
`SecurityConfig`: mudar um `requestMatcher` sem querer quebra aqui, não em produção.

## `AuthControllerTest` — 5 cenários (SPEC-ACE-01/07/12)

| Cenário | Verifica |
|---|---|
| `GET /login` e `GET /registrar` públicos | com o modelo (`registroForm`, `municipios`) montado |
| `POST /registrar` cria o passageiro e redireciona a `/login?cadastro` | conta nasce `ATIVO` com papel `PASSAGEIRO` (sem WhatsApp configurado — RN-VER-07) |
| senha curta reexibe o formulário | **Bean Validation** com erro no campo `senha` |
| telefone já cadastrado reexibe o formulário | **regra de negócio** (caminho diferente da validação de formato) |
| CPF inválido reexibe o formulário e **não cria** a conta | |

## `UsuarioControllerTest` — 12 cenários (SPEC-CAD-01)

CRUD completo por HTTP: listagem (página × fragmento HTMX × rota de fragmento), modais de novo e
edição, criação válida/ inválida/duplicada, atualização (inclusive com **senha em branco**, que deve
manter o hash), exclusão com soft-delete, 404 de id inexistente e **409 ao tentar excluir a si
mesmo** (DT-02, com a mensagem da regra no corpo). Fecha com o RBAC (passageiro/motorista → 403).

## `VeiculoControllerTest` — 11 cenários (SPEC-CAD-02)

Listagem e fragmentos, modal novo/editar (com a placa atual), criação válida e com placa inválida,
`PUT` válido e inválido, `DELETE` (soft-delete) e 404. RBAC: passageiro → 403.

## `CidadeControllerTest` — 7 cenários (SPEC-CAD-03)

Listagem, fragmentos, criação válida/inválida, edição (modal + `PUT` válido e inválido), exclusão e
404. A exclusão exercita a cascata da **DT-01** pelo caminho HTTP real.

## `ViagemControllerTest` — 12 cenários (SPEC-VIA-01/06)

| Cenário | Verifica |
|---|---|
| listagem (página e fragmento) e modal de nova | |
| **painel da semana** com e sem `?ref=` | a grade de 7 dias renderiza em qualquer data |
| criar viagem imprevista (caminho feliz) | persiste de verdade — a contagem muda |
| sem veículo → formulário (validação) | |
| motorista **sem o papel** → formulário com o erro da regra | a mesma regra do teste unitário, agora via HTTP |
| chegada antes da saída → recusada | |
| `POST /{id}/status` muda o status (flash de sucesso) | |
| `POST /{id}/status` de viagem inexistente → **404** | |
| `DELETE /{id}` exclui; inexistente → 404 | |
| `POST /designar` sem veículo/motorista → volta ao painel com erro | |
| RBAC: passageiro → 403 | |

> Estes testes usam um gerente **persistido** (`autenticar(persistir(...))`): o serviço registra quem
> criou a viagem e recarrega esse usuário do banco — com um principal sintético a FK estouraria.

## `LinhaControllerTest` — 9 cenários (SPEC-VIA-02)

Telas de lista e formulário, criação com dias da semana, validação (sem destino) e regra de negócio
(chegada antes da saída), edição, **`alternar`** (desativa e reativa) e **`excluir`** (linha sem
viagens sai; com viagens, a trava do serviço responde em flash). RBAC completo.

## `SolicitacaoControllerTest` — 5 cenários (SPEC-VIA-03)

A visão do passageiro: acesso exclusivo (`GERENTE`/`MOTORISTA` → 403; anônimo → login), o calendário
que **só oferece as linhas do dia selecionado** e — o cenário mais importante — o **isolamento**:
um passageiro não vê a solicitação de outro **pela API**, não só no serviço.

## `GestaoSolicitacaoControllerTest` — 2 cenários (SPEC-WPP-02)

O painel do gestor (`/gestao/solicitacoes`) e o RBAC da avaliação de demandas sob demanda.

## `TelasAdministrativasTest` — 9 cenários

| Cenário | Verifica |
|---|---|
| `/admin`, `/admin/configuracoes`, `/admin/auditoria`, `/admin/convites` abrem para o SYSADMIN | |
| **GERENTE recebe 403 em `/admin`** | o papel administrativo é isolado de propósito |
| `POST /admin/configuracoes` grava timeout e cidade-sede | e o serviço passa a devolver os novos valores |
| `GET /historico` (GERENTE) com paginação | a visão de auditoria restrita a `OPERACAO` |
| `POST /admin/convites` cria GERENTE `PENDENTE` com link | usa um SYSADMIN **persistido** — o token guarda quem o criou (FK) |
| convite com telefone repetido → erro em flash | |
| `GERENTE` convida `MOTORISTA` | a mesma engine, outro papel |
| `GET /ativar` público; `POST` com token inválido → erro | ativação não vaza se o token existe |
| `/perfil` abre para qualquer autenticado e salva o endereço | SPEC-CAD-04 |

## `ContaCompletarTest` — 5 cenários (SPEC-ACE-02)

O filtro `PerfilIncompletoFilter`: conta criada por login social (sem telefone) é levada a
`/conta/completar` e **só sai de lá** ao informar o telefone. Cobre também os caminhos liberados
(para não haver laço de redirecionamento).

## `VerificacaoRecuperacaoWebTest` — 4 cenários (SPEC-ACE-03)

Telas públicas de verificação/recuperação; `POST /esqueci-senha` com identificador desconhecido
**redireciona igual** (anti-enumeração); telefone desconhecido na verificação volta com erro; e o
**`POST` sem CSRF é barrado com 403**.

## `LogControllerTest` — 7 cenários (central de logs)

A tela `/logs`: trilha completa do sistema com a **etiqueta da área** de cada evento.

| Cenário | Verifica |
|---|---|
| GERENTE e SYSADMIN entram; passageiro e motorista → 403 | quem vê a trilha |
| a tela oferece todas as áreas como etiqueta/filtro | o catálogo chega ao template |
| filtro por área devolve **só** aquela área | `USUARIOS` traz `USUARIO_CRIADO` e **não** traz `LOGIN_SUCESSO` |
| busca livre acha pelo detalhe | o texto gravado no log é pesquisável |
| área desconhecida na URL → **400** | o filtro aceita só o catálogo `AreaSistema` |
| **ações do painel WhatsApp deixam rastro** | era a lacuna: conectar/desconectar/configurar não geravam log nenhum |
| `/historico` mostra **só** o ciclo das solicitações | a separação entre as duas telas de fato acontece |

## `AreaSistemaTest` — 6 cenários (unitário)

A classificação que gera a etiqueta, derivada da ação/entidade já gravadas (sem
coluna nova, valendo retroativamente para toda a trilha existente):

- classifica pela **ação** (o sinal mais específico);
- **ação vence entidade**: `CONTA_ATIVADA` grava a entidade `Usuario`, mas é evento de
  *Acesso* — se a ordem invertesse, um login apareceria em "Usuários";
- sem ação conhecida, cai na **entidade**; evento de segurança desconhecido ainda vira
  *Acesso*; o resto vira *Sistema* — **nenhum log fica sem etiqueta**;
- catálogo consistente: toda área tem rótulo e cor, e nenhuma ação está em duas áreas.

## `HomeControllerTest`

O painel inicial renderiza com as contagens.
*(Nota: hoje a home mostra os totais para **qualquer** papel — é a **DT-17** no roadmap; quando for
corrigida, este teste ganha um cenário por papel.)*

## `CaladriusApplicationTests` — o teste de contexto

Sobe a aplicação inteira contra o PostgreSQL do Testcontainers com **todas as migrations**
(V1→V15) e `ddl-auto: validate`. Falha se **qualquer** entidade JPA divergir do schema Flyway —
que é exatamente o que derrubaria o deploy em produção.
