# Plano Técnico — CALADRIUS

> Este documento descreve **como** as [specs](specs/) são realizadas: a arquitetura em
> camadas aplicada, o modelo de dados, o mapa de endpoints, a segurança e as decisões
> arquiteturais (ADRs). Ele **não repete** os documentos de base — referencia-os:
> [`ARCHITECTURE.md`](../ARCHITECTURE.md), [`CONVENTIONS.md`](../CONVENTIONS.md),
> [`SECURITY.md`](../SECURITY.md). As regras inegociáveis estão na
> [Constituição](00-constituicao.md).

---

## 1. Visão de implementação

Cada capacidade do produto segue o mesmo trajeto em camadas:

```
HTTP (HTMX/Thymeleaf)
   │   templates/{modulo}/{lista,fragments/{tabela,linha,form}}.html
   ▼
Controller (@Controller)            valida DTO (@Valid), decide página×fragmento (HX-Request)
   │   *Controller.java
   ▼
Service (@Service @Transactional)   TODA a regra de negócio; lança exceções de domínio
   │   *Service.java
   ▼
Repository (JpaRepository)          consultas derivadas/@Query; filtra removido_em IS NULL
   │   *Repository.java
   ▼
PostgreSQL (schema do Flyway)       ddl-auto: validate confere as entidades
```

Mapeamento spec → pacote:

| Spec | Controller | Service | Entidade | Repositório |
|---|---|---|---|---|
| [SPEC-01](specs/SPEC-01-autenticacao.md) | `AuthController` + Spring Security | `UsuarioService` (registro) | `Usuario` | `UsuarioRepository` |
| [SPEC-02](specs/SPEC-02-gestao-usuarios.md) | `UsuarioController` | `UsuarioService` | `Usuario` | `UsuarioRepository` |
| [SPEC-03](specs/SPEC-03-gestao-veiculos.md) | `VeiculoController` | `VeiculoService` | `Veiculo` | `VeiculoRepository` |
| [SPEC-04](specs/SPEC-04-gestao-cidades.md) | `CidadeController` | `CidadeService` | `Cidade` | `CidadeRepository` |
| [SPEC-05](specs/SPEC-05-gestao-viagens.md) | `ViagemController` | `ViagemService` | `Viagem` | `ViagemRepository` |
| Painel | `HomeController` | (consulta direta) | — | os 4 repositórios (`count*`) |

---

## 2. Modelo de dados

### 2.1 Tabelas mapeadas por entidade (ativas no incremento)

```
┌───────────────┐         ┌──────────────────┐
│   usuarios    │◄────────│  papeis_usuario  │  (ElementCollection EAGER)
│  (soft-del)   │ 1     N │ (usuario, papel) │
└──────┬────────┘         └──────────────────┘
       │ motorista / criado_por
       │
┌──────▼────────┐   destino   ┌───────────┐
│    viagens    │────────────►│  cidades  │
│  (físico)     │             │ (físico)  │
└──────┬────────┘             └───────────┘
       │ veiculo
       ▼
┌───────────────┐
│   veiculos    │
│  (soft-del)   │
└───────────────┘
```

| Entidade | Tabela | Exclusão | PK | Observações |
|---|---|---|---|---|
| `Usuario` | `usuarios` (+`papeis_usuario`) | **soft** (`removido_em`) | UUID | telefone único; e-mail/CPF únicos quando presentes; papéis EAGER |
| `Veiculo` | `veiculos` | **soft** (`removido_em`) | UUID | placa única entre ativos (índice parcial sobre `upper(placa)`) |
| `Cidade` | `cidades` | **física** | UUID | dado de referência; sem soft-delete |
| `Viagem` | `viagens` | **física** | UUID | FKs para veículo, motorista, cidade, criado_por |

### 2.2 Tabelas existentes no schema, ainda **sem** entidade

`perfis_passageiro`, `perfis_motorista`, `perfis_gerente`, `escalas_motorista`,
`solicitacoes_transporte`, `assentos_viagem`. Criadas na `V2` para os incrementos futuros —
**não** são validadas pelo Hibernate (sem entidade → não bloqueiam `ddl-auto: validate`).
Ver [Especificação de Produto §6](01-especificacao-produto.md).

### 2.3 Convenções de coluna (Constituição, Art. V e VI)

- **PK UUID** com `DEFAULT gen_random_uuid()` (nativo do PG 13+, sem `pgcrypto`); Java usa
  `GenerationType.UUID`.
- **Enums** como `VARCHAR` + `@Enumerated(EnumType.STRING)`; valores = nomes Java
  MAIÚSCULOS, com `CHECK` no banco.
- **Datas/horas**: `LocalDate`→`DATE`, `LocalTime`→`TIME`, `Instant`→`TIMESTAMPTZ`.
- **Unicidade com soft-delete**: índices únicos **parciais** (`WHERE removido_em IS NULL`).

### 2.4 Migrations (Constituição, Art. IV)

| Versão | Conteúdo | Editar? |
|---|---|---|
| `V1__criar_tabela_produto.sql` | boilerplate | **NUNCA** |
| `V2__criar_schema_caladrius.sql` | schema completo do domínio | **NUNCA** |
| `V3__remover_produto_e_seed_cidades.sql` | drop `produto` + seed de cidades | **NUNCA** |
| `V4+__…` | toda mudança futura | nova migration |

> Qualquer alteração de modelo (campo novo, tabela nova, mapear uma das tabelas "futuras") =
> **nova migration `V4+`** acompanhada da entidade correspondente, validada por
> `docker build -f docker/Dockerfile .` (gate de compilação) e por subir a app com
> `ddl-auto: validate`.

### 2.5 Política de migrations em banco compartilhado

> **Contexto:** o banco `eq14` é da equipe, mas roda num **servidor PostgreSQL compartilhado**
> entre as equipes da disciplina (o compartilhamento é de **servidor/conexões**, não de
> tabelas — por isso o pool limitado a 5, Art. XIV). A equipe **é dona** do seu schema `public`
> e **não controla** backup, restore nem privilégios de superusuário do servidor. O
> versionamento de schema **já existe e é o Flyway** — estas regras o complementam para o
> cenário compartilhado.

1. **Versionar é obrigatório e já está em uso (Flyway).** Schema só muda por migration `V4+`
   versionada; nada de DDL manual no banco. "Ter versionamento" não é uma decisão futura — é
   o estado atual, a ser mantido.
2. **Forward-only.** O Flyway Community **não tem undo**. Migration aplicada **não se edita**
   (o checksum quebra). Correção = **nova** migration. Mantenha cada `V` pequena e coesa.
3. **Migrations aditivas e de baixo privilégio.** Prefira `ADD COLUMN`/`CREATE TABLE`/recriar
   `CHECK`. Evite DDL destrutivo (`DROP`/`TRUNCATE`) sobre dados reais e **não** dependa de
   extensões/superusuário (mantém a linha do `gen_random_uuid()` nativo, Art. VI).
4. **Backup é responsabilidade da equipe.** Como o servidor não é nosso, tire um snapshot
   próprio antes de migrations sensíveis: `pg_dump` do banco `eq14`. O Flyway versiona o
   **schema**, não os **dados**.
5. **Reprodutibilidade verificada.** A cadeia `V1..Vn` deve recriar schema + dados-semente
   (cidades na `V3`) **do zero** num banco vazio. O teste de contexto com **Testcontainers**
   (§8) exerce exatamente isso a cada build — é a prova de que o versionamento "fecha".
6. **Isolamento.** A equipe usa o schema `public` do seu próprio banco (sem colisão com outras
   equipes, que têm bancos distintos no mesmo servidor). Não criar objetos fora do schema da
   equipe.

> **Exemplo (adicionar o papel `SYSADMIN`)** — migration aditiva típica, recriando o `CHECK`:
> ```sql
> -- V4__adicionar_papel_sysadmin.sql
> ALTER TABLE papeis_usuario DROP CONSTRAINT papeis_usuario_papel_check;
> ALTER TABLE papeis_usuario ADD  CONSTRAINT papeis_usuario_papel_check
>       CHECK (papel IN ('PASSAGEIRO', 'MOTORISTA', 'GERENTE', 'SYSADMIN'));
> ```
> (Confirme o nome da constraint com `\d papeis_usuario`; sem nome explícito, o PostgreSQL usa
> o padrão `papeis_usuario_papel_check`.)

---

## 3. Mapa de endpoints

Padrão comum a todos os módulos de gestão: `GET` lista (página ou fragmento conforme
`HX-Request`), `GET .../novo|nova` e `.../{id}/editar` devolvem o **modal**, `POST`/`PUT`
devolvem **linha** (sucesso) ou **modal** (erro), `DELETE` responde `200`/`404`.

| Método | Rota | Spec | Acesso | Resposta |
|---|---|---|---|---|
| GET | `/ping` | SPEC-01 | público | 200 JSON |
| GET | `/login` | SPEC-01 | público | página |
| POST | `/login` | SPEC-01 | público | (Spring Security) |
| GET | `/registrar` | SPEC-01 | público | página |
| POST | `/registrar` | SPEC-01 | público | redirect/ível |
| POST | `/logout` | SPEC-01 | autenticado | redirect `/login?logout` |
| GET | `/` | Painel | autenticado | página (totais) |
| GET | `/usuarios` | SPEC-02 | GERENTE | página/fragmento |
| GET | `/usuarios/fragmento-tabela` | SPEC-02 | GERENTE | fragmento |
| GET | `/usuarios/novo` · `/{id}/editar` | SPEC-02 | GERENTE | modal |
| POST | `/usuarios` · PUT `/usuarios/{id}` | SPEC-02 | GERENTE | linha/modal |
| DELETE | `/usuarios/{id}` | SPEC-02 | GERENTE | 200/404 (soft-delete) |
| GET/POST/PUT/DELETE | `/veiculos/**` | SPEC-03 | GERENTE | idem (soft-delete) |
| GET/POST/PUT/DELETE | `/cidades/**` | SPEC-04 | GERENTE | idem (delete físico) |
| GET | `/viagens` · `/viagens/nova` | SPEC-05 | GERENTE | página/modal |
| POST | `/viagens` | SPEC-05 | GERENTE | linha/modal |
| DELETE | `/viagens/{id}` | SPEC-05 | GERENTE | 200/404 (delete físico) |

---

## 4. Padrão HTMX (Constituição, Art. X)

- O controller inspeciona o header **`HX-Request`**: presente → devolve **fragmento**
  (`templates/{modulo}/fragments/tabela :: tabela`, `... linha :: linha`, `form :: modal`);
  ausente → devolve a **página completa** (`{modulo}/lista`).
- **Erros de formulário** retornam o `form :: modal` com `BindingResult` (validação) ou
  `bindingResult.reject(...)` (regra de negócio capturada do service).
- `GlobalModelAttributes` injeta `requestURI` em todos os templates (destaque do menu).
- `GlobalExceptionHandler` converte `RecursoNaoEncontradoException` em **404**.

Detalhes em [`ARCHITECTURE.md`](../ARCHITECTURE.md) e [`CONVENTIONS.md`](../CONVENTIONS.md).

---

## 5. Segurança (Constituição, Art. VII, VIII, XI)

- **Autenticação no banco**: `CaladriusUserDetailsService` carrega o usuário do PostgreSQL;
  login por **e-mail OU telefone** (detecção via `Documentos.pareceEmail`); `UsuarioAutenticado`
  adapta para `UserDetails` e expõe `id`/`nomeCompleto` para os templates.
- **Senhas**: `BCryptPasswordEncoder` (bean em `SecurityConfig`), usado no login e no
  cadastro/edição.
- **Autorização**: `/usuarios|veiculos|cidades|viagens/**` → `hasRole("GERENTE")`; públicas:
  `/login`, `/registrar`, `/ping`, `/actuator/health`, estáticos; resto autenticado.
- **CSRF**: ativo nos formulários de página (login/cadastro injetam token via Thymeleaf);
  desabilitado **apenas** nos endpoints HTMX de mutação dos quatro módulos.
- **Admin semeado**: `DataInitializer` (idempotente) cria o gerente inicial com BCrypt — não
  no Flyway, para usar o mesmo encoder e evitar `pgcrypto`.

Pipeline SAST/OWASP em [`SECURITY.md`](../SECURITY.md).

> **Nota de manutenção:** `SECURITY.md` ainda descreve o boilerplate ("usuário em memória",
> "Semgrep/Trivy" do Mercado). O comportamento real do CALADRIUS é o descrito acima
> (autenticação no banco). Ao revisar a documentação de segurança, alinhar `SECURITY.md` a
> esta seção.

---

## 6. Configuração e perfis

| Item | dev | prod |
|---|---|---|
| Arquivo | `application-dev.yml` | `application-prod.yml` |
| Banco | `caladrius_dev` (docker-compose.dev) | `eq14` (compartilhado) |
| `ddl-auto` | (dev) | **`validate`** |
| Pool de conexões | padrão | **5** (Art. XIV) |
| Porta | 8080 | 8080 (atrás do Caddy) |

`DataInitializer` roda em qualquer perfil (idempotente). `/ping` e `/actuator/health` são os
contratos de monitoração.

---

## 7. CI/CD (Constituição, Art. XII)

`.github/workflows/deploy.yml`: a cada `push` na `main` → `mvn clean package -DskipTests`
(via `docker/Dockerfile`) → publica imagem no GHCR → implanta. **O gate é a compilação.**
Validar localmente com `docker build -f docker/Dockerfile .` antes de qualquer push.

---

## 8. Estratégia de testes

- **Contexto/integração**: `CaladriusApplicationTests` sobe o contexto com **Testcontainers**
  (PostgreSQL real) → exercita migrations + `ddl-auto: validate` + seed.
- **Unidade de serviço**: `UsuarioServiceTest`, `VeiculoServiceTest` (Mockito) cobrem as
  regras de negócio (unicidade, normalização, soft-delete, validações).
- **Lacuna**: sem testes para `ViagemService`/`CidadeService` e sem testes de controller/web
  layer. Ver [Roadmap](03-tarefas-e-roadmap.md).
- Cada **critério de aceite** das specs é um candidato natural a caso de teste.

---

## 9. Decisões arquiteturais (ADRs)

Resumo das decisões já tomadas (detalhe e justificativa na Constituição e na `CLAUDE.md`):

| ADR | Decisão | Status | Referência |
|---|---|---|---|
| **ADR-01** | Autenticação no banco com login por e-mail **ou** telefone | Aceita | Art. VIII, SPEC-01 |
| **ADR-02** | Enums como `VARCHAR` (não enum nativo do PG) | Aceita | Art. VI |
| **ADR-03** | UUID com `gen_random_uuid()` nativo (sem `pgcrypto`) | Aceita | Art. VI |
| **ADR-04** | Soft-delete em usuários e veículos; índices únicos parciais | Aceita | Art. III, SPEC-02/03 |
| **ADR-05** | `V1` intocada; toda mudança como nova migration | Aceita | Art. IV |
| **ADR-06** | HTMX + fragmentos (sem SPA/JS customizado) | Aceita | Art. X |
| **ADR-07** | Admin semeado em Java (`DataInitializer`), não no Flyway | Aceita | §5 |
| **ADR-08** | Cidades e viagens com remoção **física** (exceção ao soft-delete) | Aceita | SPEC-04/05 |
| **ADR-09** | Pool de conexões de produção limitado a 5 | Aceita | Art. XIV |
| **ADR-10** | Papel `SYSADMIN` **isolado** (least privilege); sessão **dinâmica** (DB) | Aceita | SPEC Config., §5 |
| **ADR-11** | Onboarding por **token de ativação** (convite); meios via `NotificacaoService` | Aceita | SPEC-01, #20 |
| **ADR-12** | `dias_semana` da linha como **tabela filha `linha_dias`** (1-N), não bitmask/coluna | **Aprovada** | SPEC-06 §2.1 |
| **ADR-13** | Endereço do passageiro em **tabela `enderecos` estruturada** (FK cidade), não JSONB | **Aprovada** | SPEC-07 |

**Como registrar uma nova ADR:** ao emendar a Constituição ou tomar uma decisão técnica
relevante, acrescente uma linha aqui (com motivação) e atualize a `CLAUDE.md`.

---

## 10. Roteiro para uma nova capacidade (checklist)

1. **Spec primeiro**: criar/editar `specs/SPEC-*` com US, FR-XX, RN-XX e critérios de aceite.
2. **Constituição**: confirmar conformidade (ou registrar ADR se precisar divergir).
3. **Migration**: nova `V4+` (nunca editar existentes); rodar e validar o schema.
4. **Entidade + mapeamento** batendo com o schema (lembre `ddl-auto: validate`).
5. **DTO** (record) com Bean Validation.
6. **Service** com as regras (`@Transactional`); exceções de domínio.
7. **Controller** + templates (padrão página×fragmento HTMX).
8. **Segurança**: ajustar `SecurityConfig` (RBAC/CSRF) se houver nova rota.
9. **Testes** cobrindo os critérios de aceite.
10. **Gate**: `docker build -f docker/Dockerfile .`; só então `push` na `main`.
11. **Atualizar** `03-tarefas-e-roadmap.md` (mover de ⬜ para ✅).
