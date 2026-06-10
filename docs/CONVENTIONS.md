# Convenções do Projeto

## Estrutura de Migrations Flyway

```
V{número}__{descrição_com_underscores}.sql
V1__criar_tabela_produto.sql            (boilerplate — não editar)
V2__criar_schema_caladrius.sql
V3__remover_produto_e_seed_cidades.sql
V4__criar_tabela_solicitacao.sql        (exemplo de próxima)
```

- Nunca editar uma migration já commitada
- Descrição em português, snake_case
- Incrementar o número sequencialmente

## Conventional Commits

```
feat: adicionar cadastro de veículos com soft-delete
fix: corrigir validação de CPF no cadastro de usuário
docs: atualizar README com credenciais do admin
refactor: extrair normalização de telefone para Documentos
test: adicionar teste unitário para VeiculoService
chore: atualizar dependências do pom.xml
```

## Nomenclatura Java

| Elemento | Convenção | Exemplo |
|---|---|---|
| Package | lowercase | `br.ufpb.dsc.caladrius.service` |
| Classe | PascalCase | `ProdutoService` |
| Método | camelCase | `buscarPorId()` |
| Constante | UPPER_SNAKE | `MAX_NOME_LENGTH` |
| Variável | camelCase | `produtoForm` |

## Padrão de Fragment HTMX

Templates em `templates/{entidade}/fragments/`:
- `tabela.html` — fragment do `<tbody>` ou lista completa
- `linha.html` — fragment de uma linha/item
- `form.html` — fragment do formulário (modal)

## Validação

- DTOs usam Bean Validation (`@NotBlank`, `@Size`, etc.)
- Controller usa `@Valid` e `BindingResult`
- Erros de validação retornam fragment com mensagens Bootstrap

## Segurança — Boas Práticas

- Usar `th:text` (escaping automático) ao invés de `th:utext`
- Nunca concatenar strings em queries JPA (use parâmetros nomeados)
- Variáveis sensíveis em `.env` (nunca hardcoded)
- CSRF: habilitado por padrão, desabilitado apenas para endpoints HTMX específicos
