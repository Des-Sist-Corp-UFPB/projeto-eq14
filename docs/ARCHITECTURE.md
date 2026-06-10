# Arquitetura do Sistema

## Visão Geral

```
Browser
  │
  ▼
Controller (Spring MVC)
  │  Recebe requests HTTP, valida DTOs, delega ao Service
  ▼
Service (@Transactional)
  │  Lógica de negócio, orquestra operações
  ▼
Repository (Spring Data JPA)
  │  Abstração do banco, queries automáticas
  ▼
PostgreSQL
```

## Padrão HTMX: Server-Side Rendering Reativo

Em vez de uma SPA (React/Vue), usamos **HTMX**: o servidor retorna fragmentos HTML que o HTMX injeta na página sem reload completo.

```
Browser                         Servidor
  │                               │
  │  GET /produtos/novo           │
  │──────────────────────────────►│
  │                               │  Retorna apenas o fragmento HTML do form
  │◄──────────────────────────────│  (não a página inteira)
  │                               │
  │  HTMX injeta o fragment       │
  │  no elemento alvo (#modal)    │
```

**Vantagens para este projeto**:
- Sem JavaScript customizado
- Templates no servidor (Thymeleaf) com acesso direto ao contexto Spring
- Fácil de entender e depurar

## Flyway: Gerenciamento de Schema

```
V1__criar_tabela_produto.sql            ← boilerplate (NÃO editar — já aplicada no banco compartilhado)
V2__criar_schema_caladrius.sql          ← schema do CALADRIUS (usuarios, veiculos, cidades, viagens...)
V3__remover_produto_e_seed_cidades.sql  ← remove a tabela produto + cidades de referência
V4__...                                 ← próximas alterações sempre como nova migration
```

**Regra de ouro**: Nunca edite uma migration já aplicada (o Flyway compara checksums). Crie sempre uma nova.

> Em produção a app roda com `ddl-auto: validate`: o Hibernate confere se as entidades batem com o
> schema criado pelo Flyway. Se não baterem, a aplicação **não sobe**.

## Camadas

### Controller
- Recebe requisição HTTP
- Valida DTO com `@Valid`
- Chama Service
- Retorna template Thymeleaf (página completa ou fragment)
- NÃO contém lógica de negócio

### Service
- Anotado com `@Service` e `@Transactional`
- Contém toda a lógica de negócio
- Lança exceções de domínio (`RecursoNaoEncontradoException`, `RegraNegocioException`)
- Usa Repository para persistência

### Repository
- Interface que estende `JpaRepository`
- Queries derivadas do nome do método (Spring Data)
- Para queries complexas: `@Query` com JPQL

### Domain (Entidade)
- Classe JPA mapeada para tabela do banco
- NÃO deve conter lógica de negócio complexa
- `@PrePersist`/`@PreUpdate` para auditorias automáticas
