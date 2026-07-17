# SPEC-10 — Integração WhatsApp (conexão, envio, webhook e bot de atendimento)

| | |
|---|---|
| **Área** | `WPP` (integração WhatsApp) |
| **Papéis** | GERENTE (painel de conexão/envio); PASSAGEIRO (atendido pelo bot); SYSADMIN (segredos/variáveis de ambiente) |
| **Status geral** | ✅ **Implementada (código, 2026-07-14)** — migration **V12** + testes (adaptador, bot, webhook, painel). **Pendente**: infra da VPS (Evolution) + variáveis de ambiente no deploy (§8); sem elas o canal opera como stub (RN-WPP-02) |
| **Constituição** | Artigos II (camadas), IV (migrations forward-only), VI (UUID/enums VARCHAR), X (HTMX/Thymeleaf), XIV (banco compartilhado) |
| **Relacionada** | [SPEC-09](SPEC-09-solicitacao-de-transporte.md) (o bot reaproveita o `SolicitacaoViagemService`), [SPEC-01](SPEC-01-autenticacao.md) (identificação por telefone), ADR-11 (`NotificacaoService`), **ADR-14** (esta decisão) |
| **Código** | pacote `whatsapp/` (`ProvedorWhatsapp`, `EvolutionApiProvedor`, `MensagemRecebida`, `ProcessadorMensagemRecebida`), `whatsapp/bot/` (`BotAtendimentoService`, `MensagensBot`), `WhatsappService` (fachada: envio + log + estado), `WhatsappController` (painel), `WhatsappWebhookController`, `WhatsappConfig` (bean condicional), `ConversaBot`/`MensagemWhatsapp` (V12), evolução do `NotificacaoWhatsappCanal` |

---

## 1. Objetivo

Tirar o canal WhatsApp do estado de **stub** e entregar três capacidades:

1. **Enviar** mensagens (notificações de alocação, lembretes de viagem, respostas do bot).
2. **Receber** mensagens via **webhook** e responder de forma automatizada — um **bot de
   atendimento** que permite ao passageiro **solicitar transporte, consultar e cancelar viagens
   pelo WhatsApp**, reaproveitando o `SolicitacaoViagemService` (SPEC-09).
3. **Gerenciar a conexão dentro do próprio sistema**: a seção `/whatsapp` do gerente deixa de ser
   placeholder e vira o painel de conexão — exibição do **QR code**, status e dados da conta
   conectada, desconexão e teste de envio. A área de gerência do provedor externo fica
   **dissolvida** no CALADRIUS (ver §9).

Dois princípios regem o desenho:

- **Troca de provedor sem dor** — todo acesso ao WhatsApp passa por uma **porta**
  (`ProvedorWhatsapp`); a Evolution API é só o **primeiro adaptador**. Trocar para a API oficial
  da Meta (ou outra) significa escrever outro adaptador, sem tocar em bot, notificações ou telas.
- **Bot desacoplado da API** — o bot consome **mensagens normalizadas** e responde pela porta;
  ele não conhece payloads da Evolution. Dá para evoluir os fluxos de atendimento sem mexer na
  camada de integração (e vice-versa).

---

## 2. Decisão de integração — API oficial (Meta Cloud API) × Evolution API

**Decisão: Evolution API** (self-hosted, em VPS própria da equipe), **protegida pela porta
`ProvedorWhatsapp`** para permitir migração futura à API oficial. Registrada como **ADR-14**.

### 2.1 Comparativo

| Aspecto | **API oficial (WhatsApp Business Platform / Cloud API — Meta)** | **Evolution API (terceiros, baseada em Baileys)** |
|---|---|---|
| **Como conecta a conta** | **Sem QR code.** Exige conta no Meta Business, criação de um app WhatsApp Business, **verificação do negócio** e registro de um **número dedicado** (o número passa a ser exclusivo da API — deixa de funcionar no aplicativo do celular). Token permanente gerado no painel da Meta. | **QR code**, como o WhatsApp Web: escaneia-se com um número comum (chip normal, inclusive WhatsApp pessoal/Business do aparelho). A sessão fica guardada no servidor Evolution e reconecta sozinha; pode expirar e pedir **novo pareamento**. |
| **Burocracia/tempo até funcionar** | Dias (verificação de negócio, aprovação de *display name*). Sem CNPJ/negócio verificado, opera só em modo de teste (número de teste + até 5 destinatários). | **Minutos** — sobe o container, cria a instância, escaneia o QR. |
| **Custo** | Infra zero (hospedada pela Meta), mas **mensagens de template são cobradas** (desde 2025, preço **por mensagem** para templates de utilidade/marketing). Conversas de atendimento iniciadas pelo usuário são gratuitas. | **Gratuita** (Apache 2.0). Custo = a **VPS** que hospeda o container (+ Postgres próprio). |
| **Restrições de envio** | **Janela de 24 h**: mensagem livre só até 24 h após a última mensagem do usuário; fora dela, apenas **templates pré-aprovados** pela Meta. | **Sem janela nem templates** — envia qualquer texto a qualquer momento. |
| **Risco** | Nenhum risco de banimento; SLA e suporte da Meta; multi-dispositivo estável. | **Viola os Termos de Serviço do WhatsApp** (engenharia reversa do protocolo Web). Número pode ser **banido**, sobretudo com volume/spam. Depende da manutenção do Baileys acompanhar mudanças do WhatsApp; quedas de sessão acontecem. |
| **Webhook (receber)** | Configurado no painel da Meta, com *verify token* e assinatura `X-Hub-Signature-256`. | Configurado **via API** (`/webhook/set/{instância}`), apontando para a URL que quisermos, com headers customizados (usamos um token secreto próprio — §5.3). |
| **Operação** | Nada a hospedar. | **Nós hospedamos**: container Node.js + Postgres próprio, atualizações de versão, backup da sessão, HTTPS. |

### 2.2 Por que a Evolution (neste projeto)

- Projeto **acadêmico, sem CNPJ** nem negócio verificável na Meta — a API oficial ficaria presa ao
  modo de teste (5 destinatários), inviável para demonstrar o atendimento real.
- **Sem custo por mensagem** e sem burocracia de templates para o fluxo conversacional do bot.
- Volume baixíssimo (transporte municipal de saúde) → risco de banimento baixo se o bot só
  **responde** a quem o procura e envia notificações a usuários cadastrados (opt-in implícito).
- A Evolution também suporta o tipo de integração **WhatsApp Cloud API oficial** — mesmo mantendo
  a Evolution, há um caminho de migração; e a porta `ProvedorWhatsapp` cobre o caso de trocar de
  provedor por completo.

**Mitigação do risco**: usar um **chip dedicado** ao projeto (nunca o número pessoal de alguém da
equipe), enviar apenas mensagens transacionais/respostas, e manter o canal in-app/e-mail como
redundância (RN-WPP-01).

---

## 3. Escopo

### 3.1 Inclui
- **Porta `ProvedorWhatsapp`** + adaptador **`EvolutionApiProvedor`** (`RestClient` do Spring),
  ativado por **bean condicional** às variáveis de ambiente (mesmo padrão do `OAuth2ClientConfig`
  da SPEC-08): sem `EVOLUTION_URL`/`EVOLUTION_API_KEY`, o app sobe normalmente e o canal segue
  como stub.
- **Envio real** no `NotificacaoWhatsappCanal` (a interface `CanalNotificacao` e todos os
  chamadores ficam **intactos** — o stub só passa a delegar à porta).
- **Webhook de recebimento** (`POST /webhooks/whatsapp`) com token secreto, idempotente,
  normalizando o payload da Evolution para o record `MensagemRecebida`.
- **Bot de atendimento** modular (máquina de estados persistida em `conversas_bot`): menu,
  solicitar transporte (linha → data → confirmação), minhas viagens, cancelar, falar com a gestão.
- **Painel `/whatsapp`** do gerente: status da conexão, QR code (HTMX com polling), dados da conta,
  desconectar, envio de teste, últimas mensagens (log `mensagens_whatsapp`).
- **Migration V12**: `conversas_bot` + `mensagens_whatsapp`.
- **Infra**: Evolution API em container numa **VPS separada** (fora do servidor da disciplina),
  com HTTPS e chave de API — ver §8.

### 3.2 Não inclui (futuro)
- **Aprovação/recusa** de solicitações pelo gestor e **assentos/capacidade** (continuam como na SPEC-09).
- Envio de **mídia** (imagens/PDF de comprovante), listas/botões interativos do WhatsApp.
- ~~**Auto-cadastro** de passageiro pelo WhatsApp~~ → passou a ser **escopo da [SPEC-11](SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md)** (implementado).
- Atendimento humano via painel (chat do gerente respondendo pelo sistema) — a opção "falar com a
  gestão" apenas notifica o gerente e pausa o bot (RN-WPP-08).
- Campanhas/broadcast (aumentaria o risco de banimento — ver §2.2).

---

## 4. Arquitetura

### 4.1 Visão geral

```
                        CALADRIUS (servidor da disciplina)                 VPS da equipe
┌──────────────────────────────────────────────────────────────┐    ┌─────────────────────────┐
│  NotificacaoService ──► NotificacaoWhatsappCanal ─┐          │    │  Evolution API (Docker)  │
│                                                   ▼          │    │   ├─ sessão Baileys      │
│  BotAtendimentoService ──────────────► ProvedorWhatsapp ─────┼───►│   ├─ Postgres próprio    │
│        ▲    (responde pela porta)      (porta/interface)     │HTTPS│   └─ /manager (restrito) │
│        │                                    ▲                │+apikey                        │
│        │ MensagemRecebida (normalizada)     │ implementa     │    └────────────┬────────────┘
│  WhatsappWebhookController ◄────────────────┴─ EvolutionApi- │◄────────────────┘
│  (POST /webhooks/whatsapp + token)             Provedor      │  webhook (HTTPS + token)
│                                                              │
│  WhatsappController (/whatsapp — painel do GERENTE:          │         ┌──────────┐
│  QR code, status, conta, desconectar, teste, log)            │         │ WhatsApp │⇄ passageiro
└──────────────────────────────────────────────────────────────┘         └──────────┘
```

### 4.2 A porta `ProvedorWhatsapp` (pacote `whatsapp/`)

Cobre **conexão** e **envio** — tudo o que o restante do sistema precisa do WhatsApp:

```java
public interface ProvedorWhatsapp {
    StatusConexaoWhatsapp statusConexao();     // CONECTADO | AGUARDANDO_QR | DESCONECTADO
    ConexaoWhatsapp iniciarConexao();          // cria/conecta a instância; devolve QR (base64) se houver
    void desconectar();                        // encerra a sessão (logout)
    ContaWhatsapp contaConectada();            // número e nome do perfil conectado (ou vazio)
    void enviarTexto(String telefone, String texto);
}
```

Records de apoio (DTOs da porta, sem nada da Evolution): `ConexaoWhatsapp(status, qrCodeBase64)`,
`ContaWhatsapp(numero, nomePerfil)`, `MensagemRecebida(idProvedor, telefone, nomeContato, texto,
recebidaEm)`.

**`EvolutionApiProvedor`** implementa a porta com `RestClient` (header `apikey`); mapeamento:

| Operação da porta | Endpoint Evolution v2 |
|---|---|
| `iniciarConexao()` | `POST /instance/create` (se não existir) → `GET /instance/connect/{instância}` (devolve QR em base64); registra o webhook via `POST /webhook/set/{instância}` (URL pública + header do token, eventos `MESSAGES_UPSERT`, `CONNECTION_UPDATE`, `QRCODE_UPDATED`) |
| `statusConexao()` | `GET /instance/connectionState/{instância}` (`open`→CONECTADO, `connecting`→AGUARDANDO_QR, `close`→DESCONECTADO) |
| `contaConectada()` | `GET /instance/fetchInstances` (número/nome do perfil) |
| `desconectar()` | `DELETE /instance/logout/{instância}` |
| `enviarTexto()` | `POST /message/sendText/{instância}` — body `{ "number": "...", "text": "..." }` |

> Os paths exatos devem ser conferidos contra a versão implantada (doc oficial:
> `docs.evoapicloud.com`); qualquer divergência fica **encapsulada no adaptador**.

**Ativação condicional** (padrão da SPEC-08): o bean `EvolutionApiProvedor` só é criado com
`EVOLUTION_URL` + `EVOLUTION_API_KEY` definidas. Sem ele, `NotificacaoWhatsappCanal` mantém o
comportamento de stub (log) e o painel `/whatsapp` mostra "integração não configurada".

### 4.3 Recebimento — webhook

`WhatsappWebhookController` (`POST /webhooks/whatsapp`):

1. Valida o header `X-Webhook-Token` contra `WHATSAPP_WEBHOOK_TOKEN` (senão **403**). Rota
   `permitAll` + fora do CSRF (é chamada servidor-a-servidor pela Evolution).
2. Trata por tipo de evento:
   - `MESSAGES_UPSERT` → ignora mensagens de **grupo** (`@g.us`) e **enviadas por nós** (`fromMe`);
     extrai o texto (`conversation`/`extendedTextMessage.text`), normaliza o telefone (§4.5),
     registra em `mensagens_whatsapp` (idempotência por `id_provedor`) e entrega ao bot.
   - `CONNECTION_UPDATE` / `QRCODE_UPDATED` → atualiza o estado em memória que alimenta o painel
     (evita repetir polling na Evolution).
3. Responde **200 imediatamente**; o processamento do bot ocorre na mesma requisição (volume
   baixo), mas atrás da interface `ProcessadorMensagemRecebida` — trocar para fila/assíncrono
   depois não muda o controller.

### 4.4 Bot modular (pacote `whatsapp/bot/`)

O webhook entrega a mensagem à interface **`ProcessadorMensagemRecebida`**; o
**`BotAtendimentoService`** é a implementação. O bot **não importa nada da Evolution** — recebe
`MensagemRecebida`, responde via `ProvedorWhatsapp.enviarTexto(...)`. Máquina de estados
persistida em `conversas_bot` (sobrevive a redeploy):

```
INICIO ──► MENU ──1──► ESCOLHER_LINHA ──► ESCOLHER_DATA ──► CONFIRMAR ──► MENU
             │──2──► (minhas viagens — lista e volta ao MENU)
             │──3──► CANCELAR (escolhe solicitação ativa) ──► MENU
             │──4──► HUMANO (notifica o gerente in-app; bot pausado — RN-WPP-08)
             └──0/expiração──► ENCERRADA
```

- Respostas curtas, numeradas ("*Responda com o número da opção*"); entrada inválida repete a
  etapa com instrução.
- **Solicitar transporte** reaproveita **integralmente** o `SolicitacaoViagemService` (SPEC-09):
  linhas ativas que operam no dia, data futura, duplicata, isolamento — RN-SOL-01..07 valem
  automaticamente; erros de regra (`RegraNegocioException`) viram mensagens amigáveis do bot.
- Textos das mensagens centralizados numa classe/`messages` própria do bot — ajustar o roteiro
  não toca integração nem serviços.

### 4.5 Identificação por telefone

O remetente chega como JID (`5583999999999@s.whatsapp.net`). Normalização (no `util/` existente):
remover o sufixo, remover o DDI `55`, e tratar o **9º dígito** (JIDs antigos podem vir sem o `9`
após o DDD — comparar também a variante com `9` inserido). O resultado casa com
`usuarios.telefone` (dígitos, ativo — soft-delete respeitado). Número não cadastrado → mensagem
padrão orientando a procurar a secretaria de saúde (**não** cria conta, não revela nada).

---

## 5. Modelagem (migration V12 — aditiva, forward-only)

```
conversas_bot                              -- estado da máquina de estados do bot
  id               UUID PK (gen_random_uuid)
  telefone         VARCHAR(20) NOT NULL    -- normalizado (dígitos, sem DDI)
  usuario          UUID REFERENCES usuarios(id)          -- resolvido na identificação
  etapa            VARCHAR(20) NOT NULL DEFAULT 'INICIO'
                   CHECK (etapa IN ('INICIO','MENU','ESCOLHER_LINHA','ESCOLHER_DATA',
                                    'CONFIRMAR','CANCELAR','HUMANO','ENCERRADA'))
  linha_programada UUID REFERENCES linhas_programadas(id) ON DELETE SET NULL  -- contexto
  data_desejada    DATE                                                        -- contexto
  atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()

ux_conversa_bot_telefone UNIQUE (telefone)               -- uma conversa por número

mensagens_whatsapp                         -- log (alimenta o painel + idempotência)
  id           UUID PK (gen_random_uuid)
  direcao      VARCHAR(10) NOT NULL CHECK (direcao IN ('RECEBIDA','ENVIADA'))
  telefone     VARCHAR(20) NOT NULL
  conteudo     VARCHAR(1000) NOT NULL      -- truncado se maior
  id_provedor  VARCHAR(80)                 -- id da mensagem no provedor
  criado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()

ix_mensagens_whatsapp_criado (criado_em DESC)
ux_mensagem_whatsapp_provedor UNIQUE (id_provedor) WHERE direcao = 'RECEBIDA'  -- idempotência
```

Contexto da conversa em **colunas tipadas** (não JSONB), coerente com a decisão da V8/ADR-13.
Enums `EtapaConversa` e `DirecaoMensagem` como VARCHAR (Art. VI). Sem extensões nem superusuário
(Art. XIV — política do banco compartilhado, plano técnico §2.5).

---

## 6. Regras de negócio

| Regra | Descrição |
|-------|-----------|
| **RN-WPP-01** | **Envio nunca derruba o chamador**: falha na Evolution (timeout, 4xx/5xx, desconectado) é registrada em log/auditoria e o fluxo de negócio segue — o canal in-app continua sendo a garantia de entrega. |
| **RN-WPP-02** | **Sem configuração, sem quebra**: ausentes as variáveis de ambiente, o app sobe, o canal WhatsApp é no-op (stub) e o painel informa "não configurada". |
| **RN-WPP-03** | O webhook **só** processa requisições com o token secreto correto (senão 403, sem corpo); eventos repetidos são ignorados (idempotência por `id_provedor`). |
| **RN-WPP-04** | O bot **ignora** mensagens de grupos e mensagens enviadas pela própria conta (`fromMe`). |
| **RN-WPP-05** | Identificação por **telefone normalizado** de usuário **ativo**. ~~Número desconhecido recebe orientação padrão, sem criar cadastro.~~ **Atualizada pela [SPEC-11](SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md):** número desconhecido agora é **guiado ao auto-cadastro** (onboarding), criando um passageiro ATIVO sem senha. |
| **RN-WPP-06** | O bot age **em nome do passageiro identificado** e só sobre os dados dele (mesmo isolamento da RN-SOL-07); solicitações criadas passam pelas mesmas validações da SPEC-09 (RN-SOL-01..06). |
| **RN-WPP-07** | Conversa **expira após 30 min** de inatividade (`atualizado_em`): a próxima mensagem recomeça do menu, com aviso. |
| **RN-WPP-08** | Opção **"falar com a gestão"**: gera notificação **in-app** para os gerentes (via `NotificacaoService`) e coloca a conversa em `HUMANO` — o bot silencia para aquele número por 4 h (ou até expirar), evitando atropelar um atendimento humano feito pelo aparelho. |
| **RN-WPP-09** | Painel `/whatsapp` é exclusivo do **GERENTE** (RBAC já existente); a **apikey nunca aparece na UI** nem em logs — segredos só por variável de ambiente (SYSADMIN/deploy). |
| **RN-WPP-10** | Toda mensagem enviada/recebida é registrada em `mensagens_whatsapp` (conteúdo truncado em 1000 chars) para o painel e para auditoria de atendimento. |

---

## 7. RBAC e rotas

| Método | Rota | Acesso | Ação |
|--------|------|--------|------|
| GET | `/whatsapp` | GERENTE | Painel: status, conta conectada, QR, teste, últimas mensagens |
| POST | `/whatsapp/conectar` | GERENTE | Cria/conecta a instância e registra o webhook; devolve fragmento com o QR |
| GET | `/whatsapp/status` | GERENTE | **Fragmento HTMX** (polling ~3 s): QR atualizado / estado da conexão |
| POST | `/whatsapp/desconectar` | GERENTE | Logout da sessão |
| POST | `/whatsapp/teste` | GERENTE | Envia mensagem de teste para um número informado |
| POST | `/webhooks/whatsapp` | Público + `X-Webhook-Token` | Recebe eventos da Evolution (mensagens, conexão, QR) |

`SecurityConfig`: `/whatsapp/**` já exige GERENTE (nada muda); adicionar `/webhooks/whatsapp` ao
`permitAll` **e** à lista de exceção do CSRF. Padrão HTMX do projeto (página completa × fragmento
por `HX-Request`) nos endpoints do painel.

---

## 8. Infra — Evolution API em VPS separada

A Evolution **não** roda no servidor da disciplina (não controlamos aquela infra além do deploy da
nossa imagem, e o container da Evolution precisa de Postgres próprio e persistência de sessão).
Sobe numa **VPS da equipe**:

- `docker compose` na VPS: **evolution-api** + **postgres dedicado** (volume persistente — guarda
  credenciais da sessão Baileys; **nunca** o banco compartilhado da disciplina) + proxy reverso
  (Caddy/Traefik/nginx) com **TLS/Let's Encrypt**. Só a porta 443 exposta.
- `AUTHENTICATION_API_KEY` forte (a `apikey` exigida em toda chamada).
- Comunicação nos dois sentidos, sempre HTTPS:
  - **CALADRIUS → VPS**: chamadas da porta (`enviarTexto`, conexão/QR) com header `apikey`.
  - **VPS → CALADRIUS**: webhook para `https://eq14.dsc.rodrigor.com/webhooks/whatsapp` com o
    header `X-Webhook-Token` (o adaptador configura isso ao conectar — §4.2).
- O **manager** da Evolution (`/manager`) fica apenas como acesso administrativo de emergência,
  protegido pela apikey — o dia a dia é pelo painel `/whatsapp` (§9).

**Variáveis de ambiente do CALADRIUS** (deploy do professor, mesmas mãos que `GOOGLE_CLIENT_ID`):

| Variável | Uso |
|---|---|
| `EVOLUTION_URL` | Base URL da VPS (ex.: `https://evo.exemplo.com`) |
| `EVOLUTION_API_KEY` | `apikey` das chamadas à Evolution |
| `EVOLUTION_INSTANCIA` | Nome da instância (default `caladrius`) |
| `WHATSAPP_WEBHOOK_TOKEN` | Segredo validado no `POST /webhooks/whatsapp` |
| `APP_URL_PUBLICA` | URL pública do app, usada ao registrar o webhook (`https://eq14.dsc.rodrigor.com`) |

---

## 9. Painel `/whatsapp` — o "manager dissolvido"

A área de gerência da Evolution é só um cliente do REST dela; tudo o que precisamos no dia a dia
cabe no CALADRIUS, chamando a **porta** (nunca a Evolution direto da view):

- **Card de status**: `CONECTADO` (verde — número, nome do perfil), `AGUARDANDO_QR` (amarelo),
  `DESCONECTADO`/não configurada (cinza).
- **Conectar**: botão gera o QR; a imagem entra como `<img src="data:image/png;base64,...">` num
  fragmento HTMX com **polling ~3 s** (`GET /whatsapp/status`) — o QR expira e é renovado pela
  Evolution (`QRCODE_UPDATED`); ao conectar, o fragmento troca sozinho para o card verde.
- **Desconectar** (com confirmação) e **teste de envio** (número + texto).
- **Últimas mensagens** (de `mensagens_whatsapp`, RECEBIDA/ENVIADA) — visão do que o bot anda
  fazendo, sem sair do sistema.

O gerente **nunca vê** URL/apikey da Evolution (RN-WPP-09); para ele, o WhatsApp é uma
funcionalidade nativa do CALADRIUS.

---

## 10. Testes

- **`EvolutionApiProvedorTest`** (`MockRestServiceServer`): mapeamento dos endpoints, header
  `apikey`, tradução dos estados (`open/connecting/close`), falha HTTP → exceção da porta.
- **`BotAtendimentoServiceTest`** (Mockito, provedor mockado): fluxo completo de solicitação
  (menu → linha → data → confirmação), entrada inválida, número desconhecido (RN-WPP-05),
  expiração (RN-WPP-07), regras da SPEC-09 propagadas como resposta amigável, isolamento
  (RN-WPP-06), pausa em `HUMANO` (RN-WPP-08).
- **`WhatsappWebhookControllerTest`** (MockMvc + Testcontainers): token ausente/errado → 403;
  `MESSAGES_UPSERT` válido → resposta do bot + log; grupo/`fromMe` ignorados (RN-WPP-04);
  evento duplicado ignorado (RN-WPP-03).
- **`WhatsappControllerTest`** (MockMvc): RBAC (GERENTE 200; demais 403; anônimo redireciona);
  painel "não configurada" sem o provedor (RN-WPP-02).
- **Teste de contexto** existente passa a validar o schema V1→V12.

---

## 11. Próximos passos (pós-implementação)

1. **Aprovação/recusa** pelo gestor com notificação WhatsApp do resultado (fecha o ciclo da SPEC-09).
2. **Lembrete automático** de viagem (véspera/dia) via canal WhatsApp.
3. Mensagens interativas (listas/botões) e mídia, se a Evolution se mostrar estável.
4. Atendimento humano pelo painel (responder conversas `HUMANO` de dentro do sistema).
5. Reavaliar a **API oficial** (se o projeto ganhar CNPJ/verificação): novo adaptador da mesma porta.
