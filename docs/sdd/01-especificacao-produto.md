# Especificação de Produto — CALADRIUS

> Este documento descreve **o quê** o CALADRIUS resolve e **para quem**, antes de qualquer
> decisão de implementação. É a ponte entre a [Constituição](00-constituicao.md) (princípios)
> e as [specs de feature](specs/) (requisitos detalhados). O *como* fica no
> [Plano Técnico](02-plano-tecnico.md).

---

## 1. Visão

> **CALADRIUS organiza o transporte municipal de saúde**: permite que um município de origem
> leve seus pacientes, de forma planejada e rastreável, a consultas e procedimentos nas
> cidades metropolitanas mais próximas — substituindo o controle informal (papel, planilha,
> grupo de WhatsApp) por um sistema com papéis, cadastros e viagens.

O nome remete ao *Caladrius*, ave mítica associada à cura — coerente com o domínio de saúde.

### Problema

Pequenos municípios precisam transportar pacientes para consultas em centros metropolitanos.
Hoje esse processo costuma ser manual e frágil: difícil saber quem vai em qual viagem, qual
veículo e motorista foram designados, e qual a capacidade disponível. Isso gera retrabalho,
furos de agenda e falta de rastreabilidade.

### Proposta de valor

- **Cadastro centralizado** de pessoas (passageiros, motoristas, gestores), frota e cidades.
- **Planejamento de viagens** associando veículo + motorista + destino + data/horários.
- **Base para evolução**: solicitações de transporte do paciente, alocação automática por
  prioridade e escalas de motorista (schema já preparado — ver §6).

---

## 2. Personas e papéis (RBAC)

O sistema é baseado em papéis. Um mesmo usuário pode acumular papéis.

| Papel | Quem é | O que faz **hoje** | O que fará **no futuro** |
|---|---|---|---|
| **GERENTE** (gestor) | Servidor da secretaria de saúde responsável pelo transporte | Controle total: CRUD de usuários, veículos e cidades; cria e lista viagens | Aprovar solicitações, gerar alocação automática, montar escalas |
| **MOTORISTA** | Condutor da frota municipal | É **selecionável** como motorista de uma viagem | Visualizar suas próprias viagens e escalas |
| **PASSAGEIRO** | Paciente que precisa de transporte | **Auto-cadastra-se** pela tela pública de registro | Solicitar transporte, acompanhar status, indicar acompanhante |

> **Observação importante (estado atual):** após o login, **todos** os módulos de gestão
> exigem o papel `GERENTE`. Passageiro e motorista já existem como conceito e como dados
> (um passageiro consegue se cadastrar, um motorista consegue ser designado a uma viagem),
> mas **ainda não têm telas próprias** — isso é escopo de incrementos futuros
> (ver [SPEC-01](specs/SPEC-01-autenticacao.md) e o [Roadmap](03-tarefas-e-roadmap.md)).

---

## 3. Glossário do domínio

| Termo | Definição |
|---|---|
| **Usuário** | Raiz de identidade do sistema. Tem nome, telefone (obrigatório/único), e-mail e CPF (opcionais/únicos), senha (BCrypt), status e papéis. |
| **Papel** | Função de acesso: `PASSAGEIRO`, `MOTORISTA`, `GERENTE`. |
| **Status do usuário** | `PENDENTE`, `ATIVO`, `INATIVO`, `SUSPENSO`. Só `ATIVO` autentica. |
| **Veículo** | Item da frota: placa, marca, modelo, ano, tipo, capacidade (assentos), acessibilidade e status. |
| **Tipo de veículo** | `CARRO`, `VAN`, `MICRO_ONIBUS`, `ONIBUS`, `AMBULANCIA`. |
| **Status do veículo** | `DISPONIVEL`, `EM_VIAGEM`, `MANUTENCAO`, `INATIVO`. |
| **Cidade** | Município no sistema. **Origem** (de onde partem os pacientes) ou **Metropolitana** (destino de consultas). |
| **Viagem** | Deslocamento planejado: associa um veículo, um motorista, uma cidade de destino, data e horários de saída/chegada. |
| **Status da viagem** | `PLANEJADA` → `CONFIRMADA` → `EM_ANDAMENTO` → `CONCLUIDA` (ou `CANCELADA`). |
| **Soft-delete** | Exclusão lógica (`removido_em`) usada em usuários e veículos. |
| **Capacidade** | Número de assentos de um veículo; insumo central da futura alocação automática. |
| **Solicitação de transporte** | (Futuro) Pedido do passageiro para ir a uma consulta, com horário-limite de chegada. |
| **Assento de viagem** | (Futuro) Vínculo de um passageiro (e eventual acompanhante) a uma viagem. |
| **Escala de motorista** | (Futuro) Janela de disponibilidade de um motorista em uma data. |

---

## 4. Capacidades (mapa de features)

Cada capacidade tem uma spec detalhada em [`specs/`](specs/).

| # | Capacidade | Spec | Papel | Status |
|---|---|---|---|---|
| 1 | **Autenticação & auto-cadastro** — login por e-mail/telefone; registro público de passageiro; logout | [SPEC-01](specs/SPEC-01-autenticacao.md) | Público / Todos | ✅ Implementado |
| 2 | **Gestão de usuários** — CRUD + atribuição de papéis + soft-delete | [SPEC-02](specs/SPEC-02-gestao-usuarios.md) | GERENTE | ✅ Implementado |
| 3 | **Gestão de veículos** — CRUD da frota + soft-delete | [SPEC-03](specs/SPEC-03-gestao-veiculos.md) | GERENTE | ✅ Implementado |
| 4 | **Gestão de cidades** — CRUD de origem/destinos | [SPEC-04](specs/SPEC-04-gestao-cidades.md) | GERENTE | ✅ Implementado |
| 5 | **Gestão de viagens** — criar e listar viagens | [SPEC-05](specs/SPEC-05-gestao-viagens.md) | GERENTE | 🟡 Parcial (sem edição) |
| 6 | **Painel inicial** — totais de cada cadastro | (coberto em SPEC-02/03/04/05) | Autenticado | ✅ Implementado |
| 7 | **Solicitações de transporte** | — (planejado) | PASSAGEIRO | ⬜ Planejado |
| 8 | **Alocação automática por prioridade** | — (planejado) | GERENTE | ⬜ Planejado |
| 9 | **Escalas de motorista / telas por papel** | — (planejado) | MOTORISTA | ⬜ Planejado |
| 10 | **Integração WhatsApp (Evolution API)** | — (em avaliação) | — | ⬜ Fora do escopo atual |

---

## 5. Escopo

### Dentro do escopo (incremento atual)

- Autenticação no banco com login flexível (e-mail **ou** telefone) e logout.
- Auto-cadastro público de passageiro.
- CRUD de **Usuários** (com papéis e status) — soft-delete.
- CRUD de **Veículos** — soft-delete.
- CRUD de **Cidades** — remoção física (dados de referência).
- **Criação e listagem** de **Viagens**.
- Painel inicial com contagens.
- Health check público `/ping`.
- RBAC: módulos de gestão restritos ao `GERENTE`.

### Fora do escopo (por enquanto)

- **Solicitações de transporte** pelo passageiro (tabela `solicitacoes_transporte` existe,
  mas ainda não há entidade/tela).
- **Alocação automática** de passageiros a assentos por prioridade/horário-limite.
- **Escalas de motorista** (tabela `escalas_motorista` existe, sem entidade/tela).
- **Assentos de viagem** (tabela `assentos_viagem` existe, sem entidade/tela).
- **Perfis detalhados** de passageiro/motorista/gerente (tabelas `perfis_*` existem, sem
  entidade/tela).
- **Telas próprias** para passageiro e motorista após o login.
- **Edição** e **mudança de status** de viagem (hoje só criar/listar/excluir).
- **Integração WhatsApp** via Evolution API (em avaliação pela equipe).

> O schema já contempla várias dessas evoluções (ver §6); a ausência é de
> **entidade/serviço/tela**, não de modelo de dados.

---

## 6. Modelo de dados preparado para o futuro

A migration `V2` criou tabelas que **ainda não têm entidade JPA mapeada**, deliberadamente,
para reduzir o atrito dos próximos incrementos:

| Tabela | Para que serve | Quando entra |
|---|---|---|
| `perfis_passageiro` | Dados clínicos/mobilidade do passageiro | Incremento de perfis |
| `perfis_motorista` | CNH, categoria, matrícula, admissão | Incremento de perfis |
| `perfis_gerente` | Matrícula e cargo do gestor | Incremento de perfis |
| `escalas_motorista` | Janelas de disponibilidade | Incremento de escalas |
| `solicitacoes_transporte` | Pedidos de transporte do passageiro | Incremento de solicitações |
| `assentos_viagem` | Passageiro/acompanhante por viagem | Incremento de alocação |

Essas tabelas **não** são validadas pelo Hibernate (não há entidade), então não bloqueiam o
`ddl-auto: validate`. Ao mapeá-las no futuro, a entidade deve bater exatamente com a coluna
já existente (ou ser introduzida via nova migration `V4+`).

---

## 7. Requisitos não-funcionais (NFRs)

| Categoria | Requisito | Como é atendido hoje |
|---|---|---|
| **NFR-SEG-1** Autenticação | Todo acesso (exceto rotas públicas) exige login | Spring Security + `CaladriusUserDetailsService` |
| **NFR-SEG-2** Autorização | Módulos de gestão restritos a `GERENTE` | `SecurityConfig` (`hasRole("GERENTE")`) |
| **NFR-SEG-3** Senhas | Hash forte e adaptativo | BCrypt (`BCryptPasswordEncoder`) |
| **NFR-SEG-4** Injeção | Sem SQL concatenado | JPA com parâmetros nomeados |
| **NFR-SEG-5** XSS | Escaping automático na view | `th:text` no Thymeleaf |
| **NFR-SEG-6** CSRF | Proteção em formulários de página | CSRF on; off só nos endpoints HTMX |
| **NFR-SEG-7** SAST | Análise estática no pipeline | SpotBugs+FindSecBugs+OWASP (`-Psecurity`) |
| **NFR-INT-1** Integridade de schema | Código e banco nunca divergem | `ddl-auto: validate` + Flyway |
| **NFR-INT-2** Histórico | Não perder usuários/veículos | Soft-delete |
| **NFR-DAT-1** Identidade | PKs globais e não sequenciais | UUID `gen_random_uuid()` |
| **NFR-PER-1** Recursos compartilhados | Não esgotar o banco da disciplina | Pool de produção = 5 |
| **NFR-OPS-1** Health check | Monitoração externa | `GET /ping` público (200 JSON) |
| **NFR-OPS-2** Deploy | Entrega contínua na `main` | GitHub Actions → GHCR → servidor |
| **NFR-UX-1** Reatividade sem SPA | UI fluida sem recarregar página | HTMX + fragmentos Thymeleaf |
| **NFR-UX-2** Paginação | Listas grandes navegáveis | `Pageable` (10 itens/página) |
| **NFR-MNT-1** Idioma único | Domínio legível pela equipe | Tudo em português |
| **NFR-MNT-2** Testabilidade | Regra de negócio testável | Services isolados + Testcontainers |

---

## 8. Premissas e restrições

- **Banco de produção compartilhado** entre equipes (`eq14`) → pool limitado, migrations
  cuidadosas, sem dependência de extensões (`pgcrypto`).
- **Perfil padrão `dev`**; produção usa `application-prod.yml`.
- **Admin semeado** na primeira execução (`DataInitializer`): telefone `83999999999` /
  e-mail `admin@caladrius.local` / senha `admin123` — **trocar em produção**.
- **Cidades de referência** semeadas pela `V3` (Patos como origem; João Pessoa, Campina
  Grande e Cajazeiras como metropolitanas).
- O projeto é **acadêmico** (disciplina DSC/UFPB Campus IV, prof. Rodrigo Rebouças); o
  pipeline e o ambiente são os fornecidos pela disciplina.

---

## 9. Métricas de sucesso (acadêmico)

- Build de produção verde (gate de compilação do CI).
- Aplicação sobe com `ddl-auto: validate` (schema íntegro).
- Todas as capacidades do escopo atual funcionando ponta a ponta (login → CRUD → viagem).
- SAST sem achados High/Critical não justificados.
- Specs e código em sincronia (rastreabilidade FR ↔ código).
