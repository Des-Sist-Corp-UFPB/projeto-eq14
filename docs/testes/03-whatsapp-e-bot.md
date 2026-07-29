# 03 — WhatsApp: fachada, adaptador, bot e webhook

Testes da integração com o WhatsApp ([SPEC-10](../sdd/specs/SPEC-10-integracao-whatsapp.md) e
[SPEC-11](../sdd/specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md)). A arquitetura é
**porta + adaptador** (ADR-14), e a suíte reflete isso:

| Peça | Teste | Tipo |
|---|---|---|
| Fachada (`WhatsappService`) | `WhatsappServiceTest` | unitário |
| Adaptador (`EvolutionApiProvedor`) | `EvolutionApiProvedorTest` | unitário com `MockRestServiceServer` |
| Bot (`BotAtendimentoService`) | `BotAtendimentoServiceTest` | unitário |
| Webhook (`WhatsappWebhookController`) | `WhatsappWebhookControllerTest` | API (MockMvc + Testcontainers) |
| Painel (`WhatsappController`) | `WhatsappControllerTest` | API |

> **O tema que atravessa todos:** o sistema tem de funcionar **sem** a integração. Em produção a
> Evolution ainda não subiu, e mesmo depois ela pode cair. Vários cenários abaixo existem só para
> provar que nada quebra nesse caso.

---

## `WhatsappServiceTest` — 22 cenários

### Envio
| Cenário | Protege |
|---|---|
| **sem integração configurada, o envio é no-op** (devolve `false`, não registra nada) | **RN-WPP-02** — sem `EVOLUTION_URL`/`API_KEY` a app sobe e opera como stub |
| envio bem-sucedido devolve `true` e registra a mensagem `ENVIADA` | log de mensagens (RN-WPP-10) |
| **falha do provedor NÃO propaga** — devolve `false` e não registra envio | **RN-WPP-01** — Evolution fora do ar não pode derrubar a aprovação de uma viagem |

### Recebimento
| Cenário | Protege |
|---|---|
| mensagem nova é registrada e liberada para o bot | |
| **evento repetido devolve `false`** e não registra de novo | **RN-WPP-03 (idempotência)** — provedores reenviam o mesmo evento |
| mensagem sem id do provedor é registrada | não dá para deduplicar sem id; melhor registrar que perder |

### Estado da conexão (painel)
| Cenário | Protege |
|---|---|
| sem provedor, **sempre DESCONECTADO** — mesmo com estado reportado antes | RN-WPP-02: cache antigo não pode "fingir" que está conectado |
| o estado vem do provedor (fonte da verdade) | o painel se autocorrige quando a conta pareia |
| `AGUARDANDO_QR` reaproveita o QR que o webhook trouxe | não refaz o pareamento a cada polling (3 s) |
| falha na consulta cai no **último estado reportado** | o painel nunca quebra por causa da Evolution |
| falha **sem** estado anterior ⇒ `DESCONECTADO` | nunca lança |
| `contaConectada`: devolve a conta; em falha, vazio | |
| no boot, conta conectada **re-registra o webhook**; falha não impede a app de subir | conserta o caso comum em dev (URL do túnel mudou) |

### Configurações de envio
Padrões quando nada está configurado; **salvar em branco cai nos padrões** (nunca grava mensagem ou
horário vazios); trim nos valores; e `mensagemConfirmacao` substituindo `{data}`/`{hora}`/`{destino}`
e assinando com o nome de exibição — inclusive com **valores nulos** (vira vazio, sem `NullPointerException`).

---

## `EvolutionApiProvedorTest` — 10 cenários

Adaptador HTTP testado com `MockRestServiceServer` (sem rede). É o **único** ponto que conhece o
formato da Evolution — por isso o teste checa o contrato literal:

| Cenário | Protege |
|---|---|
| `statusConexao` envia a `apikey` e traduz `open → CONECTADO` | o header de autenticação não pode sumir |
| `connecting → AGUARDANDO_QR`, `close → DESCONECTADO` | tradução completa do vocabulário do provedor |
| **instância inexistente (404) → DESCONECTADO, sem exceção** | primeira execução, antes de existir instância |
| erro 5xx → `WhatsappException` | falha real vira a exceção **da porta**, não a do RestClient |
| `enviarTexto` faz `POST sendText` com o número **prefixado por 55** | o DDI é responsabilidade do adaptador, não do domínio |
| falha HTTP no envio → `WhatsappException` | |
| `iniciarConexao` cria instância, **registra o webhook com token** e devolve o QR | RN-WPP-03 — o webhook nasce autenticado |
| `desconectar` faz `DELETE logout` | |
| `contaConectada` acha a instância e normaliza o número do JID | |
| sem pareamento (sem `ownerJid`) → vazio | |

---

## `BotAtendimentoServiceTest` — 14 cenários (SPEC-11)

A máquina de estados da conversa (`conversas_bot`). O bot é **desacoplado**: só fala com serviços e
com a porta — por isso é testável sem WhatsApp nenhum.

### Onboarding de número desconhecido (RN-ONB-01)
- número desconhecido é guiado ao cadastro (o bot pede o nome);
- **fluxo completo** nome → endereço → CPF **cria o passageiro** e abre o menu;
- **CPF inválido faz o bot repetir a etapa** — não avança nem perde o que já foi digitado.

### Menu
- primeira mensagem de quem já é cadastrado abre saudação + menu;
- opção inválida repete o menu (sem travar a conversa);
- **opção 3** lista as linhas ativas — e, **sem linhas cadastradas**, avisa em vez de mostrar vazio;
- **opção 4** gera o link de acesso à plataforma (usando a URL pública) e volta ao menu;
- **opção 2** lista as solicitações e, havendo ativas, oferece o cancelamento numerado.

### Solicitação sob demanda
- destino → data → horário → condições → **confirmar** cria a solicitação;
- **data no passado repete a etapa** da data;
- **regra de negócio (duplicata) vira resposta amigável** e volta ao menu — o usuário do WhatsApp
  nunca vê uma exceção.

### Cancelamento e sessão
- cancelar pelo número escolhido delega ao serviço **em nome do passageiro** (o isolamento por dono
  continua valendo — o bot não é um atalho para os dados de outra pessoa);
- **conversa expirada (30 min) recomeça com aviso** — **RN-WPP-07**.

---

## `WhatsappWebhookControllerTest` — 6 cenários (API)

O único endpoint público de entrada servidor-a-servidor.

| Cenário | Protege |
|---|---|
| **sem token → 403; token errado → 403**, e nada é processado | **RN-WPP-03** — o `X-Webhook-Token` é a autenticação (a rota está fora do CSRF) |
| mensagem válida: 200, registra no log e **o bot abre a conversa no MENU** | ponta a ponta: HTTP → log → bot → `conversas_bot` |
| evento repetido (mesmo id) é ignorado | idempotência com o banco de verdade |
| **grupos e `fromMe` são ignorados** | **RN-WPP-04** — o bot não responde em grupo nem a si mesmo |
| **bot desligado**: registra a mensagem, **não aciona o bot**, responde 200 | **CA-FLG-03** (SPEC-13) — o kill switch não perde mensagem |
| evento de conexão é aceito (atualiza o painel) | |

---

## `WhatsappControllerTest` — 4 cenários (API)

O painel do gerente: RBAC (**RN-WPP-09** — só GERENTE; demais 403; anônimo → login), a tela
informando "integração não configurada" **sem quebrar** (RN-WPP-02), o fragmento de status usado pelo
polling HTMX, e o teste de envio sem integração devolvendo **flash de erro** em vez de exceção.
