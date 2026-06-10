# Memória do Projeto — CALADRIUS (eq14)

## Identidade do Projeto
- **Nome**: CALADRIUS — Agendamento de Transporte Municipal de Saúde
- **Equipe**: eq14
- **Disciplina**: Desenvolvimento de Sistemas Corporativos
- **Professor**: Rodrigo Rebouças — UFPB Campus IV
- **Origem**: adaptado do boilerplate "Sistema Mercado" do professor (mesma stack e arquitetura).

## Domínio (resumo)
Pacientes solicitam transporte para consultas na cidade metropolitana mais próxima; o gestor
organiza **viagens**, **veículos**, **motoristas** e **cidades**. Sistema baseado em papéis
(RBAC): **PASSAGEIRO**, **MOTORISTA**, **GERENTE**.

**Escopo atual (este incremento):** CRUD de Usuários, Veículos, Cidades e criação/listagem de
Viagens + autenticação. **Fora do escopo por enquanto:** integração WhatsApp (Evolution API),
solicitações de transporte, escalas e alocação automática (tabelas já existem no schema para
evolução futura).

## Stack Técnica
| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.4.5 |
| Templates | Thymeleaf + HTMX | 3.x + 2.0 |
| Frontend | Bootstrap | 5.3 |
| Banco | PostgreSQL | 16 |
| Migrations | Flyway | 11.x |
| Segurança | Spring Security | 6.x (autenticação no banco, BCrypt) |
| Testes | JUnit 5 + Mockito + Testcontainers | - |

## Estrutura de Pacotes
```
br.ufpb.dsc.caladrius
├── config/          # SecurityConfig, GlobalModelAttributes, DataInitializer, GlobalExceptionHandler
├── controller/      # Ping, Auth, Home, Veiculo, Cidade, Usuario, Viagem (MVC + HTMX)
├── domain/          # Entidades JPA (Usuario, Veiculo, Cidade, Viagem)
│   └── enums/       # Papel, StatusUsuario, TipoVeiculo, StatusVeiculo, TipoCidade, StatusViagem
├── dto/             # Records de formulário (Bean Validation)
├── exception/       # RecursoNaoEncontradoException, RegraNegocioException
├── repository/      # Interfaces Spring Data JPA
├── security/        # UsuarioAutenticado (UserDetails), CaladriusUserDetailsService
├── service/         # Lógica de negócio (@Transactional)
└── util/            # Documentos (CPF, normalização de telefone, detecção de e-mail)
```

## Configuração e Acesso
- **Porta interna**: 8080 · **Perfil padrão**: `dev` · **Health check público**: `GET /ping` (200 JSON).
- **Banco dev**: `caladrius_dev` (docker-compose.dev.yml). **Banco prod**: `eq14` (compartilhado).
- **Pool de conexões em prod limitado a 5** (`application-prod.yml`) — o banco é compartilhado entre equipes.
- **Login (admin/gerente)**: telefone `83999999999` **ou** e-mail `admin@caladrius.local`, senha `admin123`
  (criado pelo `DataInitializer` na primeira execução).

## Comandos Essenciais
```bash
# Banco de dev + app local
docker compose -f docker/docker-compose.dev.yml up postgres adminer
mvn spring-boot:run

# Build/testes
mvn clean package -DskipTests        # build (igual ao do Dockerfile/CI)
mvn test                             # testes (requer Docker p/ Testcontainers)
mvn verify -Psecurity                # SAST: SpotBugs + FindSecBugs + OWASP Dependency-Check

# Imagem de produção (mesmo build do CI)
docker build -f docker/Dockerfile -t caladrius:latest .
```

## Decisões Arquiteturais

### Autenticação no banco com login por e-mail OU telefone
`CaladriusUserDetailsService` detecta o formato (contém "@" → e-mail; senão → telefone, normalizado
para dígitos) e carrega o usuário do PostgreSQL. Substitui o `InMemoryUserDetailsManager` do
boilerplate. Conforme o redesenho v3 da equipe. Senhas com BCrypt.

### Enums como VARCHAR (não enum nativo do PostgreSQL)
As colunas de enum são `VARCHAR` mapeadas com `@Enumerated(EnumType.STRING)` (valores = nomes das
enums Java, em MAIÚSCULAS, com `CHECK` nas tabelas centrais). É a opção mais robusta com o Hibernate
`ddl-auto: validate` — evita o atrito de mapear enums nativas do Postgres.

### UUID com `gen_random_uuid()` nativo
PKs são UUID. A função `gen_random_uuid()` é nativa no PostgreSQL 13+ — **não exige a extensão
pgcrypto** (evita problema de permissão no banco compartilhado da disciplina). O Hibernate gera o
UUID via `GenerationType.UUID`.

### Soft-delete
`usuarios` e `veiculos` usam `removido_em` (nunca DELETE físico); as consultas filtram
`removido_em IS NULL`. Unicidade (telefone/e-mail/CPF/placa) via índices únicos **parciais** (só
entre ativos).

### Migrations Flyway — V1 intocada
`V1__criar_tabela_produto.sql` (do boilerplate) **NÃO** é editada (já aplicada no banco compartilhado;
o Flyway compara checksum). `V2` cria o schema CALADRIUS; `V3` remove a tabela `produto` e semeia
cidades. Toda alteração futura = nova migration.

## Convenções de Código
- Nomes/identificadores em **português** (domínio, métodos, colunas, comentários).
- Endpoints REST em português (`/veiculos`, `/viagens`...).
- Records Java para DTOs; `@Transactional(readOnly = true)` em consultas.
- Padrão HTMX: controller devolve página completa em requisição normal e **fragmento** quando há
  header `HX-Request`. Templates por módulo em `templates/{modulo}/` com `fragments/{tabela,linha,form}.html`.
- Commits no padrão Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`.

## CI/CD (não quebrar!)
- `.github/workflows/deploy.yml` faz **build da imagem** (`docker/Dockerfile` → `mvn clean package
  -DskipTests`), publica no GHCR e implanta. **O gate é a COMPILAÇÃO** — valide com
  `docker build -f docker/Dockerfile .` antes de dar push.
- Em prod a app roda com `ddl-auto: validate`: **toda entidade JPA precisa bater com o schema do
  Flyway**, senão a app não sobe.

## Documentação Técnica
| Documento | Conteúdo |
|-----------|----------|
| [README.md](README.md) | Visão geral, como rodar, acesso, estrutura |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Camadas, HTMX, Flyway |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Migrations, nomenclatura, validação |
| [docs/SECURITY.md](docs/SECURITY.md) | SAST, OWASP, configuração do Spring Security |

## Próximos Passos Sugeridos
1. Solicitações de transporte (passageiro) + listagem de pendentes.
2. Alocação automática por prioridade (horário-limite de chegada) → assentos de viagem.
3. Escalas de motorista; perfis (passageiro/motorista/gerente) detalhados.
4. (Opcional) Integração WhatsApp via Evolution API — ainda em avaliação pela equipe.
5. Migrar autenticação de sessão para considerar perfis e telas por papel (motorista/passageiro).
