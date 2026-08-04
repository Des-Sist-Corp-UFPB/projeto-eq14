# Spec-Driven Development (SDD) — CALADRIUS

Esta pasta reúne a documentação **orientada a especificação** (Spec-Driven Development)
do CALADRIUS. A ideia central do SDD é simples: **a especificação é a fonte da verdade,
não o código**. Primeiro descrevemos *o que* o sistema deve fazer e *por quê*; só depois
decidimos *como* implementar; e a implementação é considerada correta quando satisfaz a
especificação — não o contrário.

> Estes documentos foram escritos a partir de uma análise do estado **atual** do código
> (engenharia reversa da especificação). Eles descrevem o que o sistema já faz hoje e
> servem de base para os próximos incrementos. Quando código e spec divergirem, a regra é:
> **corrija a spec se a mudança foi intencional; corrija o código se foi um desvio.**

---

## Por que SDD neste projeto

O CALADRIUS é um projeto de disciplina, desenvolvido em equipe e em incrementos. Sem uma
especificação compartilhada, cada incremento corre o risco de:

- reintroduzir decisões já tomadas (ex.: "soft-delete ou delete físico?");
- quebrar contratos implícitos (ex.: o endpoint público `/ping`, o gate de compilação do CI);
- divergir do modelo de dados criado pelo Flyway (o que derruba a aplicação em produção,
  onde o Hibernate roda com `ddl-auto: validate`).

A especificação resolve isso transformando o conhecimento tácito (hoje espalhado entre
`CLAUDE.md`, código e a cabeça da equipe) em **artefatos versionados e revisáveis**.

---

## Organização

```
docs/
├── ARCHITECTURE.md          ← (base) camadas, HTMX, Flyway
├── CONVENTIONS.md           ← (base) migrations, nomenclatura, validação
├── SECURITY.md              ← (base) SAST, OWASP, Spring Security
├── checklist.md             ← requisitos cobrados na disciplina × estado atual
├── testes/                  ← a SUÍTE real: o que cada teste verifica e por quê
└── sdd/
    ├── README.md                       ← este arquivo (índice + fluxo)
    ├── 00-constituicao.md              ← princípios invioláveis do projeto
    ├── 01-especificacao-produto.md     ← visão, personas, glossário, escopo, NFRs
    ├── 02-plano-tecnico.md             ← como as specs viram arquitetura/dados/endpoints (ADRs)
    ├── 03-tarefas-e-roadmap.md         ← rastreabilidade (feito × pendente) + dívidas DT-XX
    ├── cenarios-de-teste.md            ← catálogo de cenários a testar (planejamento)
    └── specs/                              ← uma pasta por ÁREA; numeração reinicia em cada uma
        ├── acesso/       (ACE)  quem entra no sistema e como
        │   ├── SPEC-ACE-01-autenticacao.md                    ← login (e-mail/telefone) + auto-cadastro
        │   ├── SPEC-ACE-02-login-social-google.md             ← OAuth2/OIDC
        │   └── SPEC-ACE-03-verificacao-de-contato-e-recuperacao-de-senha.md ← OTP + link mágico
        ├── cadastros/    (CAD)  entidades de apoio do dia a dia
        │   ├── SPEC-CAD-01-gestao-usuarios.md                 ← CRUD de usuários + papéis (RBAC)
        │   ├── SPEC-CAD-02-gestao-veiculos.md                 ← CRUD de veículos (frota)
        │   ├── SPEC-CAD-03-gestao-cidades.md                  ← CRUD de cidades (origem/destino)
        │   └── SPEC-CAD-04-endereco-do-passageiro.md          ← endereço estruturado (municípios)
        ├── viagens/      (VIA)  o núcleo do domínio
        │   ├── SPEC-VIA-01-gestao-viagens.md                  ← criação/listagem de viagens
        │   ├── SPEC-VIA-02-viagens-rotineiras-e-imprevistas.md ← linhas, painel semanal, designação
        │   └── SPEC-VIA-03-solicitacao-de-transporte.md       ← passageiro solicita pelo sistema
        ├── whatsapp/     (WPP)  o canal conversacional
        │   ├── SPEC-WPP-01-integracao-whatsapp.md             ← porta + Evolution API + painel
        │   └── SPEC-WPP-02-solicitacao-sob-demanda-e-onboarding-whatsapp.md ← bot + aprovação do gestor
        ├── plataforma/   (PLT)  o que é do PRODUTO, não do domínio
        │   ├── SPEC-PLT-01-feature-toggle.md                  ← ✅ flags, manutenção, parâmetros
        │   ├── SPEC-PLT-02-multi-ambiente-por-secretaria.md   ← 🚧 multi-tenancy
        │   └── SPEC-PLT-03-organizacao-planos-e-pagamento.md  ← 🔵 venda self-service + pagamento
        └── operacao/     (OPE)  manter de pé e enxergar
            ├── SPEC-OPE-01-observabilidade-opentelemetry.md   ← ✅ traces/métricas/logs
            └── SPEC-OPE-02-healthcheck-de-banco-e-analytics-de-uso.md ← ✅ /ping com banco + Umami
```

Os três documentos da raiz de `docs/` (`ARCHITECTURE`, `CONVENTIONS`, `SECURITY`) **continuam
válidos** e são a **base técnica** referenciada pelo SDD. O SDD não os substitui: ele adiciona
a camada de *requisitos* (o "o quê/por quê") acima da camada *técnica* que eles já cobrem.

---

## O fluxo SDD (e como cada artefato se encaixa)

```
        ┌─────────────────────┐
        │   00 Constituição   │  Princípios que NENHUMA spec pode violar.
        │  (regras de ouro)   │  Ex.: soft-delete, Flyway imutável, RBAC, PT-BR.
        └──────────┬──────────┘
                   │ governa
                   ▼
        ┌─────────────────────┐
        │ 01 Espec. de Produto│  Visão, personas, glossário, ESCOPO, NFRs.
        │   (o quê / por quê)  │  Responde "que problema resolvemos e para quem".
        └──────────┬──────────┘
                   │ se desdobra em
                   ▼
        ┌─────────────────────┐
        │ specs/<área>/SPEC-…  │  Requisitos funcionais por capacidade:
        │ (requisitos por área)│  user stories, FR-XX, regras, critérios de aceite.
        └──────────┬──────────┘
                   │ é realizada por
                   ▼
        ┌─────────────────────┐
        │   02 Plano Técnico   │  COMO: modelo de dados, endpoints, decisões.
        │   (o como)           │  Liga cada FR à arquitetura em camadas.
        └──────────┬──────────┘
                   │ é executada via
                   ▼
        ┌─────────────────────┐
        │   03 Tarefas/Roadmap │  Estado atual (feito × pendente) e próximos incrementos.
        └─────────────────────┘
```

### Ordem de leitura recomendada

1. **`00-constituicao.md`** — entenda as regras que tudo o mais respeita.
2. **`01-especificacao-produto.md`** — entenda o problema, os papéis e o escopo.
3. **`specs/<área>/SPEC-*`** — mergulhe na capacidade que você vai mexer.
4. **`02-plano-tecnico.md`** — veja como aquilo vira código.
5. **`03-tarefas-e-roadmap.md`** — veja o que falta e o que vem a seguir.

---

## Como evoluir o sistema usando este SDD

Para **qualquer** mudança não trivial (nova capacidade ou alteração de regra):

1. **Atualize a spec primeiro.** Acrescente/edite os requisitos (`FR-XX`), regras de negócio
   e critérios de aceite na `specs/<área>/SPEC-*` correspondente — ou crie uma nova spec.
2. **Cheque a Constituição.** A mudança respeita os princípios invioláveis? Se precisar
   violar um, isso é uma decisão arquitetural: documente-a (e provavelmente abra uma ADR
   no plano técnico) antes de prosseguir.
3. **Atualize o Plano Técnico.** Defina a migration Flyway (sempre nova, nunca editar
   existente), entidade, endpoints e o impacto no RBAC/segurança.
4. **Quebre em tarefas.** Registre em `03-tarefas-e-roadmap.md` e implemente.
5. **Valide contra os critérios de aceite.** Escreva/rode os testes; confira o gate de
   compilação (`docker build -f docker/Dockerfile .`) que o CI exige.

---

## Convenções dos documentos de spec

- **Idioma:** português (mesma convenção do código — ver `CONVENTIONS.md`).
- **Nome do arquivo de spec:** `specs/<pasta>/SPEC-<PREFIXO>-<NN>-<slug>.md`, onde o prefixo
  identifica a **área** (`ACE`, `CAD`, `VIA`, `WPP`, `PLT`, `OPE`) e `NN` reinicia em cada pasta.
  Spec nova entra no fim da **sua** pasta, sem renumerar as demais.
- **Identificadores de requisito:** `FR-<área>-<n>` (functional requirement) e
  `RN-<área>-<n>` (regra de negócio). Ex.: `FR-USU-03`, `RN-VIA-01`. **A área do requisito é
  temática e independe da pasta** (`RN-WPP-*`, `RN-FLG-*`, `RN-VER-*` continuam como estão) — são
  citados por centenas de pontos do código, e por isso **nunca** são renumerados. Uma vez
  publicado, um identificador não é reaproveitado para outra coisa.
- **Critérios de aceite:** no formato **Dado / Quando / Então** (Given/When/Then).
- **Status de cada requisito:** marcado como `✅ Implementado`, `🟡 Parcial` ou
  `⬜ Planejado`, para deixar claro o que já existe no código hoje.
- **Rastreabilidade:** cada spec aponta para os artefatos de código que a realizam
  (controller, service, entidade, migration), permitindo navegar da regra ao código.
