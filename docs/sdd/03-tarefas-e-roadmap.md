# Tarefas e Roadmap — CALADRIUS

> Rastreabilidade entre as [specs](specs/) e o estado real do código, mais o roteiro dos
> próximos incrementos. Legenda: ✅ Implementado · 🟡 Parcial · ⬜ Planejado · 🔵 Em avaliação.

---

## 1. Estado atual por capacidade

| Capacidade | Spec | Estado | Observação |
|---|---|---|---|
| Autenticação (login e-mail/telefone) | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | mensagem genérica, só ativos logam |
| Auto-cadastro de passageiro | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | papel `PASSAGEIRO`, status `ATIVO` |
| Logout + health `/ping` | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | `/ping` público |
| CRUD de usuários + papéis | [SPEC-02](specs/SPEC-02-gestao-usuarios.md) | ✅ | soft-delete; busca multi-campo |
| CRUD de veículos | [SPEC-03](specs/SPEC-03-gestao-veiculos.md) | ✅ | soft-delete; placa única |
| CRUD de cidades | [SPEC-04](specs/SPEC-04-gestao-cidades.md) | ✅ | remoção física; seed na V3 |
| Viagens: criar/listar/excluir | [SPEC-05](specs/SPEC-05-gestao-viagens.md) | 🟡 | sem edição/transição de status |
| Painel inicial (totais) | (produto §4) | ✅ | contagens por repositório |
| Verificação de contato + recuperação de senha | [SPEC-12](specs/SPEC-12-verificacao-de-contato-e-recuperacao-de-senha.md) | ✅ | OTP/WhatsApp + link de e-mail, migration V14 |
| Observabilidade (OpenTelemetry) | [SPEC-14](specs/SPEC-14-observabilidade-opentelemetry.md) | ✅ | **em produção** (`dsc-eq14` no Grafana); agente auto (traces/métricas/logs) + 2 spans de negócio |
| **Feature toggle (flags + parâmetros + entitlement)** | [SPEC-13](specs/SPEC-13-feature-toggle.md) | ✅ | **implementado** — `/admin/features`, modo de manutenção (503), bot on/off, `municipios.pagamento_habilitado` (**V15**) |
| **Central de logs (`/logs`)** | (#19 + evolução) | ✅ | item de 1º nível no menu; trilha completa com **etiqueta de área** (`AreaSistema`, derivada — sem migration), filtro por área e busca. `/historico` passou a mostrar **só o ciclo das solicitações** |
| Multi-ambiente por secretaria | [SPEC-15](specs/SPEC-15-multi-ambiente-por-secretaria.md) | 🚧 | proposta + protótipo (Opção A / silo); isolamento físico, sem código/migration |
| Organização, planos e pagamento | [SPEC-16](specs/SPEC-16-organizacao-planos-e-pagamento.md) | 🔵 | **proposta em avaliação** — cadastro de gestor com checkout (Mercado Pago) + `Organizacao`; aciona a **Opção C** da SPEC-15 |

---

## 2. Dívidas técnicas e lacunas (do código atual)

Itens já identificados nas specs, ordenados por relevância. Cada um deve virar uma spec/tarefa
antes de ser implementado.

| ID | Lacuna | Origem | Estado |
|---|---|---|---|
| **DT-01** | Excluir cidade referenciada por viagem pode violar a FK | [SPEC-04 §7](specs/SPEC-04-gestao-cidades.md) | ✅ Resolvido — `CidadeService` remove viagens em cascata + aviso no popup |
| **DT-02** | Sem trava contra excluir/suspender o **último gerente** ou a si mesmo | [SPEC-02 §9](specs/SPEC-02-gestao-usuarios.md) | ✅ Resolvido — guarda do último gerente, bloqueio de auto-deleção, `solicitarSuspensao` |
| **DT-03** | Viagens não checam conflito de veículo/motorista sobrepostos | [SPEC-06 §6](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado na SPEC-06 (RN-VIA-08) — aguarda #21 |
| **DT-04** | `listarAtivos()` de veículos não filtra por `DISPONIVEL` | [SPEC-03 §8](specs/SPEC-03-gestao-veiculos.md) | ✅ Resolvido — `listarDisponiveis()` filtra `DISPONIVEL` |
| **DT-05** | Sem edição/transição de status de viagem | [SPEC-06](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Coberto pela SPEC-06 — aguarda #21 |
| **DT-06** | `SECURITY.md` descrevia o boilerplate (Mercado/in-memory) | [Plano §5](02-plano-tecnico.md) | ✅ Resolvido — `SECURITY.md` alinhado à auth no banco |
| **DT-07** | Faltam testes de `ViagemService`/camada web | [Plano §8](02-plano-tecnico.md) | 🟡 Parcial — `CidadeServiceTest` criado; falta `ViagemService`/web |
| **DT-08** | Data de viagem no passado não é bloqueada | [SPEC-06 §6](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado (RN-VIA-09, só imprevistas) — aguarda #21 |
| **DT-09** | `retorno_previsto` não coletado / tipo inconsistente | [SPEC-06 §2](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado — vira `horario_retorno` (LocalTime), ida/volta — aguarda #21 |
| **DT-10** | Gestão de sessão (timeout/cookie) não especificada | [Plano §2.5](02-plano-tecnico.md) | 🟡 A formalizar — config **dinâmica** via SPEC de Configuração do Sistema (#18) |
| **DT-11** | Teste de contexto (Testcontainers) falhava com Docker Engine novo | [Cenários](cenarios-de-teste.md) | ✅ Resolvido — Testcontainers **1.20.4 → 1.21.4** (negocia a API ≥ 1.40) |
| **DT-12** | **Sem *lockout* do login por senha** — o OTP tem trava de tentativas (RN-VER-05), mas o `formLogin` aceita tentativas ilimitadas | [SPEC-12 §9](specs/SPEC-12-verificacao-de-contato-e-recuperacao-de-senha.md) | ⬜ Aberta — a auditoria já registra `LOGIN_FALHA`; falta bloquear (por conta e/ou IP) |
| **DT-13** | **Cobertura de testes sem gate**: o JaCoCo relatava mas não media contra meta | [checklist](../../checklist.md) | ✅ Resolvido — `jacoco:check` com limiar **85% de linhas** no `verify` (falha o build abaixo disso) |
| **DT-14** | Tabelas **dormentes** no schema (`solicitacoes_transporte` da V2, `assentos_viagem`, `escalas_motorista`, `perfis_*`) sem entidade nem uso | [Plano §2.2](02-plano-tecnico.md) | ⬜ Aberta — limpar (drop) ou mapear ao entrar o Incremento B/C; hoje só confundem quem lê o schema |
| **DT-15** | **SPEC-08 × SPEC-12**: conta criada por Google não passa pela verificação de contato (RN-VER-08) | [SPEC-12](specs/SPEC-12-verificacao-de-contato-e-recuperacao-de-senha.md) | ⬜ Aberta — o e-mail do Google já vem verificado; falta decidir o telefone |
| **DT-16** | **Janela de atendimento do WhatsApp** (início/fim): era persistida mas **nunca aplicada** — o bot respondia fora do horário e a tela dava a impressão contrária | [SPEC-11](specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md) | 🅿️ **Em standby — removida do sistema (2026-07-29)** por decisão do dono do produto: a janela só faz sentido **quando existir um serviço de suporte humano** por trás. Enquanto o atendimento é 100% bot, silenciá-lo à noite deixaria sem resposta justamente quem solicita viagem de madrugada. Campos, DTO, chaves de config e testes removidos; **reintroduzir junto do suporte humano** (aí a janela define quando o bot diz "um atendente responde a partir das 8h", e não quando ele emudece) |
| **DT-17** | **Home (`/`) mostra os totais do sistema para qualquer papel** | [CLAUDE.md](../../../CLAUDE.md) | ⬜ Aberta — esconder de não-gerente e dar landing por papel |
| **DT-18** | **Cache de feature flags é por instância** (`FeatureFlagService`) — com várias réplicas, desligar uma flag não propaga | [SPEC-13 §4 D2](specs/SPEC-13-feature-toggle.md) | ⬜ Aberta (aceita) — hoje roda 1 container por ambiente; revisar se houver réplica |
| **DT-21** | **Central de logs ainda não é por organização**: `/logs` mostra a trilha inteira do deploy. Com a [SPEC-16](specs/SPEC-16-organizacao-planos-e-pagamento.md), cada secretaria deve ver **apenas os seus** eventos | (#19 + SPEC-16) | ⬜ Aberta — o ponto de leitura já é único (`AuditoriaService.listar`), então o filtro entra em um lugar só; o `log_auditoria` precisará da coluna `organizacao` (V17) |
| **DT-22** | **Log de auditoria sem retenção**: a tabela cresce indefinidamente e a paginação lista todas as páginas de uma vez no rodapé | (#19) | ⬜ Aberta — definir política (ex.: arquivar > 12 meses) e trocar a paginação por "anterior/próxima" quando passar de N páginas |
| **DT-20** | **Pareamento do WhatsApp não expira sozinho**: a instância fica em `connecting` na Evolution renovando o QR indefinidamente. Mitigado com o botão **"Cancelar pareamento"** (2026-07-29), mas não há **timeout automático** nem encerramento ao fim da sessão do gestor | [SPEC-10 §9](specs/SPEC-10-integracao-whatsapp.md) | ⬜ Aberta — avaliar um limite (ex.: desistir após N minutos sem leitura). **Não** amarrar à sessão HTTP: o pareamento é do *deploy*, não do usuário — o logout de um gestor não pode derrubar o pareamento que outro iniciou |
| **DT-19** | **Painel `/whatsapp` desperdiça a coluna da direita**: os cards ("Configurações de envio", "Canais de mensagem", "Últimas mensagens") são empilhados em ~2/3 da largura, deixando o resto vazio. Pedido do dono do produto: pôr **"Canais de mensagem" ao lado** das configurações, em duas colunas | [SPEC-10 §9](specs/SPEC-10-integracao-whatsapp.md) | ⬜ Aberta — só layout (grid Bootstrap), sem mudança de comportamento; fazer junto da entrega de "Canais de mensagem", que hoje é um placeholder "Em breve" |
| **DT-23** | **O bot responde mensagens antigas** (*history sync* do WhatsApp): o `WhatsappWebhookController` monta a `MensagemRecebida` com `Instant.now()` e **descarta o `data.messageTimestamp`** da Evolution. No primeiro pareamento o Baileys sincroniza o histórico recente do chat e a Evolution reemite cada mensagem como `messages.upsert` — sem o carimbo do provedor, o app não distingue mensagem nova de antiga e o bot responde a todas, em sequência. Observado em **2026-07-29**: uma única mensagem gerou **3 respostas** (saudação + menu e, com a máquina de estados já em `MENU`, duas `OPCAO_INVALIDA`). A idempotência não protege — o índice `ux_mensagem_whatsapp_provedor` só barra o **mesmo** `id_provedor`, e aqui os `id` são distintos | [SPEC-10 §4.3](specs/SPEC-10-integracao-whatsapp.md) | ⬜ Aberta — ler `data.messageTimestamp` (epoch em segundos), preencher `MensagemRecebida.recebidaEm` com ele e **descartar no webhook** o que for mais velho que uma janela curta (~5 min): a mensagem entra em `mensagens_whatsapp` (rastro preservado) mas **não** aciona o bot. ~15 linhas em um arquivo, **sem migration**; de quebra o `criado_em` do log deixa de mentir |
| **DT-25** | **Navegação por HTMX não gera pageview no Umami**: o rastreador conta no *load* e em mudança de história do navegador; boa parte da navegação do CALADRIUS troca fragmento sem navegar (painel semanal, tabelas, status do WhatsApp). A métrica fica enviesada para as telas que dão *full page load* | [SPEC-17 §10](specs/SPEC-17-healthcheck-de-banco-e-analytics-de-uso.md) | ⬜ Aberta (decisão consciente) — **não** instrumentar `htmx:afterSwap` de forma genérica: o polling de `/whatsapp/status` roda a cada ~3 s e geraria **~20 pageviews falsos por minuto** por gestor com o painel aberto. Se for cobrir, usar `htmx:pushedIntoHistory`, que só dispara quando a URL muda |
| **DT-26** | **Sem eventos de negócio no Umami**: só há pageview. Não dá para medir conversão do funil (quantos abrem `/registrar` e concluem, quantas solicitações saem do bot × do sistema) | [SPEC-17 §10](specs/SPEC-17-healthcheck-de-banco-e-analytics-de-uso.md) | ⬜ Aberta — `umami.track('nome-do-evento')` nos pontos de conclusão. **Nunca** passar identificador de pessoa como propriedade do evento: o Umami é sem dado pessoal por padrão e isso o quebraria (RN-ANL-04 e LGPD) |
| **DT-27** | **O timeout do healthcheck não cobre a aquisição da conexão**: `queryTimeout` limita a execução do `SELECT`, mas não a espera pelo pool (Hikari, `connectionTimeout` default **30 s**). Com o pool esgotado ou o banco inacessível na rede, o `/ping` pode demorar até 30 s antes de responder `database: "down"` | [SPEC-17 §4.2](specs/SPEC-17-healthcheck-de-banco-e-analytics-de-uso.md) | ⬜ Aberta — avaliar `spring.datasource.hikari.connection-timeout` menor (afeta a aplicação inteira, decidir com cuidado — o pool de prod é de só 5 conexões num banco compartilhado) ou limitar a chamada do indicador no tempo. Hoje o risco é baixo: o banco fica na mesma rede Docker |
| **DT-28** | **`unhealthy` não reinicia o container**: com o `BancoHealthIndicator`, banco fora ⇒ `/actuator/health` 503 ⇒ container `unhealthy` — mas o Docker sozinho não age sobre healthcheck (só em Swarm/orquestrador), e `restart: unless-stopped` reage à **saída do processo**, não ao estado de saúde. O sinal existe e é correto, porém é apenas observável | [SPEC-17 §3](specs/SPEC-17-healthcheck-de-banco-e-analytics-de-uso.md) | ⬜ Aberta (aceita) — reiniciar por banco fora seria até nocivo (o banco é do professor e compartilhado; o container subiria em laço sem resolver nada). O caminho real é **alerta** a partir da telemetria da SPEC-14, não restart |
| **DT-29** | **Compose da SPEC-15 sem as variáveis do Umami**: `docker-compose.tenant.yml` não repassa `UMAMI_*`, então um ambiente provisionado por secretaria sobe sem analytics | [SPEC-15](specs/SPEC-15-multi-ambiente-por-secretaria.md) | ⬜ Aberta — decidir antes **um site por secretaria** (isola os dados, exige criar o site no painel a cada provisionamento) ou **um site só** com `data-tag` por ambiente. Enquanto o multi-ambiente é protótipo, não bloqueia |
| **DT-30** | **Relatório de cobertura não era publicado no repositório**: o gate de 85% rodava no `verify`, mas as [orientações da avaliação](../../ORIENTACOES-AVALIACAO-2026-06-29.md) exigem a pasta `cobertura/` commitada **e** o percentual no README — e o README ainda declarava **~72%**, número anterior ao trabalho de cobertura. Item avaliado como **não atendido** por falta de evidência, não por falta de teste | [orientações](../../ORIENTACOES-AVALIACAO-2026-06-29.md) | ✅ Resolvido (2026-07-31) — `cobertura/jacoco/` commitada e README com o número real: **87,7% de linhas**, 388 testes verdes. **Regerar a cada entrega avaliada** (comando no README §Cobertura de Testes) |
| **DT-31** | **`/logs` estava quebrado em produção desde `56efdcd`**: o commit da central de logs entregou controller, enum `AreaSistema`, item de menu e a CSS das etiquetas (`.cal-tag--*`), mas **não o template `logs/lista.html`**. A rota estourava `TemplateInputException` → **500**, e o item aparecia no menu de GERENTE/SYSADMIN. Os 5 testes de `LogControllerTest` reprovavam desde então | (#19) | ✅ Resolvido (2026-07-31) — template criado conforme o contrato que já existia no controller, no teste e na CSS. **A causa raiz continua aberta (DT-32)** |
| **DT-32** | **O CI não roda os testes**: `deploy.yml` constrói a imagem com `mvn clean package -DskipTests`, então o único gate é a **compilação**. Foi por isso que a DT-31 chegou à produção e sobreviveu a três commits — o `jacoco:check` de 85% só roda no `mvn verify` local, que ninguém executa a cada push | [`deploy.yml`](../../.github/workflows/deploy.yml) | ⬜ Aberta — acrescentar um job de `mvn verify` no workflow (precisa de Docker no runner para o Testcontainers, que o `ubuntu-latest` tem). Deixar o deploy dependente desse job: hoje um teste vermelho não impede nada de ir ao ar |
| **DT-24** | **A cadeia do webhook falha em silêncio**: `EvolutionApiProvedor.registrarWebhook()` faz `return` mudo quando `APP_URL_PUBLICA` está vazia, e o `WhatsappWebhookController` responde **403 a tudo** quando `WHATSAPP_WEBHOOK_TOKEN` está vazio. Em ambos os casos o painel continua **verde ("Conectado")** — o status só prova o sentido CALADRIUS → Evolution. O sintoma para o gestor é "mando mensagem e não acontece nada", **sem um único log que explique o porquê** (diagnosticado em 2026-07-29) | [SPEC-10 §8](specs/SPEC-10-integracao-whatsapp.md) | ⬜ Aberta — `log.warn` ao pular o registro por falta de `APP_URL_PUBLICA` e no boot quando há provedor mas o token do webhook está vazio; no card de status do `/whatsapp`, distinguir "**recebimento não configurado**" de "não conectado" |

> **Nota sobre DT-03/05/08/09**: continuam marcadas como "aguarda #21", mas o essencial já entrou
> com a SPEC-06 (ciclo de status, painel semanal, designação). O que de fato falta é a **validação
> de conflito de veículo/motorista** (DT-03) e o **bloqueio de data no passado** (DT-08).

---

## 3. Roadmap dos próximos incrementos

Os incrementos seguem a ordem sugerida na [`CLAUDE.md`](../../CLAUDE.md) ("Próximos Passos").
Cada um deve começar por uma **nova spec** em `specs/` e respeitar o
[checklist do Plano Técnico §10](02-plano-tecnico.md).

> **Em desenvolvimento (sequência aprovada pelo dono do projeto):**
> 1. **[SPEC-07](specs/SPEC-07-endereco-do-passageiro.md) — Endereço do passageiro** (tabela
>    `enderecos` estruturada, **migration V8**) → vem **antes** das viagens.
> 2. **[SPEC-06](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) — Viagens rotineiras/imprevistas**
>    (`linhas_programadas` + `linha_dias` + alteração de `viagens`, **migration V9**). Decisões
>    confirmadas em [SPEC-06 §2.1](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) (ADR-12/13).

### Incremento A — Solicitações de transporte (passageiro) ✅
- **Entregue** em duas etapas: **por linha** ([SPEC-09](specs/SPEC-09-solicitacao-de-transporte.md), V11)
  e **sob demanda** ([SPEC-11](specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md), V13 —
  destino+data+horário+condições, sem linha; avaliação/aprovação do gestor; onboarding pelo WhatsApp).
- **Nota**: em vez de mapear a `solicitacoes_transporte` (V2), estendeu-se `solicitacoes_viagem`
  com um `tipo` (ADR-15). A tabela `solicitacoes_transporte` segue **dormente** (DT de limpeza futura).

### Incremento B — Alocação automática por prioridade ⬜
- **Objetivo**: alocar passageiros a **assentos** de viagem por prioridade (horário-limite de
  chegada), respeitando capacidade e acessibilidade do veículo.
- **Base pronta no schema**: `assentos_viagem` (V2).
- **Depende de**: Incremento A (solicitações) e SPEC-05 (viagens).
- **Resolve junto**: DT-04 (só alocar veículos disponíveis/compatíveis).

### Incremento B.1 — Aprovação/recusa do gestor (sob demanda) ✅
- **Entregue** na [SPEC-11](specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md): painel
  `/gestao/solicitacoes` (GERENTE) avalia demandas, **aprova** (aloca a uma viagem imprevista) ou
  **recusa** (com motivo); o passageiro é notificado por WhatsApp. Assentos/capacidade seguem no
  Incremento B (alocação por prioridade — o gestor avalia a prioridade manualmente por ora).

### Incremento C — Escalas de motorista e telas por papel ⬜
- **Objetivo**: registrar janelas de disponibilidade do motorista e dar a ele a visão das
  próprias viagens; perfis detalhados (passageiro/motorista/gerente).
- **Base pronta no schema**: `escalas_motorista`, `perfis_*` (V2).
- **Resolve junto**: DT-03 (validar disponibilidade ao criar viagem).

### Incremento D — Integração WhatsApp (Evolution API) ✅ (código) / 🟡 (infra)
- **Status**: **código implementado e testado (2026-07-14)** — [SPEC-10](specs/SPEC-10-integracao-whatsapp.md)
  (ADR-14), migration **V12** aplicada, 182 testes verdes. **Pendente**: subir a Evolution na
  **VPS da equipe** (SPEC-10 §8) e configurar as variáveis de ambiente no deploy
  (`EVOLUTION_URL`, `EVOLUTION_API_KEY`, `WHATSAPP_WEBHOOK_TOKEN`, `APP_URL_PUBLICA`) — sem elas
  o canal segue como stub e o painel informa "não configurada" (RN-WPP-02).
- **Entregue**: porta `ProvedorWhatsapp` + adaptador `EvolutionApiProvedor` (bean condicional),
  envio real no `NotificacaoWhatsappCanal` (via fachada `WhatsappService` + log
  `mensagens_whatsapp`), webhook `POST /webhooks/whatsapp` (token + idempotência), **bot de
  atendimento** (`whatsapp/bot/`, máquina de estados em `conversas_bot`) reaproveitando o
  `SolicitacaoViagemService` (SPEC-09), e painel `/whatsapp` do gerente (QR + polling HTMX,
  status, desconectar, teste, últimas mensagens).

### Incremento E — Verificação de contato e recuperação de senha ✅ (core) / 🟡 (SPEC-08)
- **Objetivo**: verificar **telefone** (OTP/WhatsApp) e **e-mail** (link/e-mail) no cadastro, e um
  fluxo de **"esqueci a senha"** (OTP/WhatsApp) — modelo **híbrido** ([SPEC-12](specs/SPEC-12-verificacao-de-contato-e-recuperacao-de-senha.md), **ADR-16**).
- **Status**: ✅ **implementado e testado (2026-07-21)** — migration **V14**, **223 testes verdes**,
  app sobe validando o schema. `VerificacaoService` (OTP), `RecuperacaoSenhaService` (reset com
  seletor de método + anti-enumeração), verificação de e-mail por link, cadastro→PENDENTE+OTP com
  degradação (RN-VER-07) e flag de DEV. **Pendente**: integração SPEC-08 (RN-VER-08) e **lockout do
  login por senha** (nova DT).
- **Reusa**: `TokenAtivacao`/`ConviteService` (ADR-11) e o `NotificacaoService` multicanal.
- **Depende de**: canal WhatsApp = infra da Evolution (Incremento D); canal e-mail = integrar
  `JavaMailSender`/SMTP (hoje `NotificacaoEmailCanal` é stub). O caminho feliz (telefone/WhatsApp)
  independe do e-mail.
- **Resolve junto**: os três itens da **SPEC-01 §9** (reset, verificação de contato, lockout dos
  códigos). Fica de fora o **lockout do login por senha** (nova DT sugerida).

### Incremento F — Observabilidade (OpenTelemetry) ✅ (em produção)
- **Status**: **em produção (2026-07-24)** — [SPEC-14](specs/SPEC-14-observabilidade-opentelemetry.md)
  (ADR-18), **sem migration**, 199 testes verdes e `docker build` verde (agente pinado **v2.30.0**).
  **Ativado pelo portal da disciplina** (editor de `.env` que recria o container) — `dsc-eq14` recebendo
  no Grafana. Runbook + armadilhas da ativação em [SPEC-14 §11](specs/SPEC-14-observabilidade-opentelemetry.md).
- **Entregue**: agente Java (auto: HTTP/JDBC/JVM/**logs**) exportando **OTLP** ao backend **central**
  da disciplina (Grafana+Tempo+Prometheus+**Loki**); camada manual `RastreamentoService` +
  `TelemetriaConfig`; **2 spans de negócio** (`solicitar-sob-demanda` + `aprovar-solicitacao`, atributos
  de domínio + logs estruturados) com testes de unidade e de **cenário real**; infra dev (`grafana/otel-lgtm`) e prod
  (Dockerfile embute o agente; compose + `.env.example` com `OTEL_*`/`JAVA_TOOL_OPTIONS`).

### Incremento G — Feature toggle ✅
- **Objetivo**: ligar/desligar funcionalidades em runtime pela área admin — **bot on/off**, **modo de
  manutenção**, **entitlement de pagamento por município** e **config toggles** (parâmetros de negócio)
  — [SPEC-13](specs/SPEC-13-feature-toggle.md), **ADR-17**.
- **Status**: ✅ **implementado** — migration **V15** (`municipios.pagamento_habilitado`); flags e
  parâmetros como linhas em `configuracoes_sistema` (ADR-10, sem tabela nova).
- **Entregue**: `FeatureFlagService` (cache + default seguro + auditoria), catálogos `ChaveFeature` e
  `ParametroSistema`, `ManutencaoFilter` (503 + página pública, libera SYSADMIN e `/ping`), gate do bot
  no `WhatsappWebhookController`, `MunicipioService` (entitlement) e as telas `/admin/features` e
  `/admin/municipios`. `VerificacaoService`/`ConviteService` passaram a ler os parâmetros do toggle.
- **Resolve**: move o item "Feature toggle" do `docs/checklist.md` de 🟡 para ✅.

### Incremento H — Multi-ambiente por secretaria (multi-tenancy) 🚧
- **Objetivo**: isolar **cada secretaria/cliente** no **seu próprio ambiente** (app + banco dedicado +
  subdomínio), sem um ver os dados do outro — [SPEC-15](specs/SPEC-15-multi-ambiente-por-secretaria.md), **ADR-19**.
- **Decisão**: **Opção A (instância por cliente / silo)** — isolamento **físico**, **zero código de
  tenant**, **sem migration**. Descartadas por ora: schema-por-tenant (B) e linha-a-linha (C — reservada
  para quando exigir **painel único multi-secretaria**; ver [SPEC-15 §7](specs/SPEC-15-multi-ambiente-por-secretaria.md)).
- **Status**: 🚧 **proposta + protótipo** — `docker/docker-compose.tenant.yml` (modelo de stack isolado),
  `docker/.env.tenant.example`, `scripts/novo-ambiente.sh` (provisionamento) e runbook em
  [`docs/multi-ambiente.md`](../multi-ambiente.md). **Aditivo**: nada é referenciado pelo CI atual.
- **Falta**: operar de fato (VPS própria com Postgres por ambiente) e, se um dia precisar de visão
  consolidada entre secretarias, avaliar a migração para a Opção C.

### Incremento I — Organização, planos e pagamento 🔵 (em avaliação)
- **Objetivo**: **venda self-service** — quem se cadastra como gestor escolhe um plano, paga e só
  então vira `GERENTE` da sua **organização** (secretaria) —
  [SPEC-16](specs/SPEC-16-organizacao-planos-e-pagamento.md), **ADR-20** (proposta).
- **Regra que sustenta tudo (RN-PAG-01)**: o papel `GERENTE` vem **só** da confirmação
  servidor-a-servidor do pagamento (webhook + reconsulta na API) — nunca do formulário nem do
  redirect de retorno do checkout. Sem isso, o seletor "sou gestor" seria escalonamento de privilégio.
- **Faseamento** (SPEC-16 §9): **fase 1** = `Organizacao`/`Assinatura`/`Pagamento` + checkout +
  webhook (**V16**); **fase 2** = tenant (`organizacao`) nas tabelas operacionais com `@TenantId` +
  **testes de isolamento** (**V17**). ⚠️ Entre as fases, gestores de secretarias diferentes ainda se
  enxergam — a fase 2 é **obrigatória** antes de cliente real.
- **Impacto no login**: pequeno (o `UsuarioAutenticado` passa a carregar a organização; a tela de
  login não muda). O caro é a fase 2 — ver [SPEC-16 §6](specs/SPEC-16-organizacao-planos-e-pagamento.md).
- **Aciona a Opção C** da [SPEC-15 §7](specs/SPEC-15-multi-ambiente-por-secretaria.md) (auto-serviço de
  cadastro + painel único eram exatamente os gatilhos previstos).
- **Bloqueado por**: responder as decisões em aberto da SPEC-16 (planos/preços, resolução da
  organização do passageiro, destino da `configuracoes_sistema`).

---

## 4. Backlog técnico transversal (não-funcional)

| Item | Descrição |
|---|---|
| Alinhar `SECURITY.md` | Atualizar para o modelo real (auth no banco), removendo resíduos do boilerplate (DT-06). |
| Cobertura de testes | ✅ **Resolvido (DT-07/DT-13)** — **346 testes**, **87,3% de linhas** (87,5% instruções, 69,7% ramos), com **gate de 85%** no `mvn verify` (`jacoco:check`). Serviços a 95,1%; controllers a 80,8%. Cenários documentados em [`docs/testes/`](../testes/). Falta: `CaladriusOidcUserService` e subir a cobertura de **ramos**. |
| Regras de integridade | Proteções DT-01 (cidade referenciada) e DT-02 (último gerente). |
| Sincronizar status do veículo | Marcar `EM_VIAGEM` ao alocar; liberar ao concluir/cancelar. |
| Observabilidade | ✅ **Em produção** pela **[SPEC-14](specs/SPEC-14-observabilidade-opentelemetry.md)** (ADR-18): traces/métricas/**logs** via OpenTelemetry ao backend central (`dsc-eq14` no Grafana). |

---

## 5. Como manter este documento

- Ao concluir um item, **mover** de ⬜/🟡 para ✅ e atualizar a spec correspondente
  (status dos `FR-XX`).
- Ao descobrir uma lacuna, **registrar** como `DT-XX` apontando a origem na spec.
- Ao iniciar um incremento, **criar a spec primeiro** (a fonte da verdade do SDD) e só então
  abrir as tarefas de implementação.
