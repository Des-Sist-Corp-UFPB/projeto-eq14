# CALADRIUS — Agendamento de Transporte Municipal de Saúde

Projeto da disciplina **Desenvolvimento de Sistemas Corporativos** — equipe **eq14**.
**Professor**: Rodrigo Rebouças · **UFPB — Campus IV**

---

## Sobre o projeto

O **CALADRIUS** organiza as solicitações de transporte de pacientes que precisam ir a
consultas na cidade metropolitana mais próxima. A plataforma permite ao gestor cadastrar
**usuários** (passageiros, motoristas e gerentes), **veículos** da frota, **cidades**
(origem e destinos) e planejar **viagens** — associando veículo, motorista e destino.

Os papéis do sistema (RBAC):

| Papel | Pode |
|---|---|
| **Passageiro** | Cadastrar-se e solicitar transporte (evolução futura) |
| **Motorista** | Visualizar suas viagens (evolução futura) |
| **Gerente** | Controle total: usuários, veículos, cidades e viagens |

> **Autenticação flexível:** o usuário pode entrar com **e-mail _ou_ telefone** (o sistema
> detecta o formato automaticamente). O telefone é obrigatório no cadastro; o e-mail é
> opcional e também serve para login e recuperação de conta.

> A integração com o WhatsApp (Evolution API) faz parte do escopo de longo prazo, mas
> **não está incluída** neste incremento — o foco atual é o CRUD e a autenticação.

Este projeto foi adaptado do boilerplate da disciplina (Spring Boot), mantendo toda a
arquitetura em camadas (Controller → Service → Repository), o padrão **HTMX + Thymeleaf**
e o pipeline de CI/CD.

---

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21 + Spring Boot 3.4.5 |
| Templates | Thymeleaf + HTMX 2.0 |
| Frontend | Bootstrap 5.3 |
| Banco | PostgreSQL 16 |
| Migrações | Flyway |
| Segurança | Spring Security 6 (autenticação no banco, BCrypt) |
| Build | Maven 3.9 · CI/CD GitHub Actions |

---

## Como rodar (desenvolvimento)

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Docker**.

```bash
# 1) Suba apenas o banco de desenvolvimento (PostgreSQL + Adminer)
docker compose -f docker/docker-compose.dev.yml up postgres adminer

# 2) Em outro terminal, rode a aplicação (perfil dev)
mvn spring-boot:run
```

Ou tudo de uma vez (banco + app + adminer):

```bash
docker compose -f docker/docker-compose.dev.yml up --build
```

### Acesso local

| O que | Endereço |
|-------|----------|
| Aplicação | http://localhost:8080 |
| Health check público | http://localhost:8080/ping |
| Adminer (banco) | http://localhost:8888 |

**Login do administrador (gerente)** — criado automaticamente na primeira execução:

| Campo | Valor |
|-------|-------|
| Telefone | `83999999999` |
| E-mail | `admin@caladrius.local` |
| Senha | `admin123` |

> Entre com o telefone **ou** o e-mail acima. Troque essas credenciais em produção.

---

## Estrutura

```
src/main/java/br/ufpb/dsc/caladrius/
├── config/        # SecurityConfig, DataInitializer (seed admin), handlers
├── controller/    # Controllers MVC + HTMX (Ping, Auth, Home, Veiculo, Cidade, Usuario, Viagem)
├── domain/        # Entidades JPA (Usuario, Veiculo, Cidade, Viagem) + enums
├── dto/           # Records de formulário (Bean Validation)
├── exception/     # Exceções de domínio
├── repository/    # Interfaces Spring Data JPA
├── security/      # UserDetailsService no banco (login por e-mail/telefone)
├── service/       # Lógica de negócio (@Transactional)
└── util/          # Validação de CPF, normalização de telefone/e-mail

src/main/resources/
├── db/migration/  # Flyway: V1 (boilerplate), V2 (schema CALADRIUS), V3 (limpeza + cidades)
└── templates/     # Thymeleaf (auth, inicio, veiculos, cidades, usuarios, viagens)
```

---

## Testes

```bash
mvn test          # requer Docker (Testcontainers sobe um PostgreSQL real)
```

Cobrem o carregamento do contexto (migrações + validação do schema + seed) e a lógica dos
services (`VeiculoService`, `UsuarioService`).

---

## CI/CD e Deploy

O `.github/workflows/deploy.yml` constrói a imagem Docker (`docker/Dockerfile`), publica no
GHCR e implanta no servidor da disciplina a cada `push` na `main`. A aplicação roda atrás de
um proxy (Caddy) e conecta ao **PostgreSQL compartilhado** da disciplina — por isso o pool de
conexões é limitado a 5 (`application-prod.yml`).

Documentação técnica complementar em [`docs/`](docs/) e na [CLAUDE.md](CLAUDE.md).
