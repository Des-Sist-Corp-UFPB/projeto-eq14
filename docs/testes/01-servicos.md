# 01 — Testes de serviço (regra de negócio)

Testes **unitários** (JUnit 5 + Mockito, sem banco e sem Spring) da camada onde mora a decisão de
negócio. São os mais numerosos e os mais rápidos: `service` está em **95,1%** de cobertura de linhas.

Cada seção lista os cenários e **o que quebraria em produção** se o teste não existisse.

---

## `UsuarioServiceTest` — 19 cenários (SPEC-01/02, DT-02)

Auto-cadastro, CRUD do gerente e as travas que impedem o sistema de ficar sem gestor.

| Cenário | Protege |
|---|---|
| normaliza telefone, faz hash da senha e concede `PASSAGEIRO` | senha nunca gravada em claro; telefone sempre em dígitos |
| com WhatsApp configurado, nasce `PENDENTE` e dispara OTP | SPEC-12 — conta só ativa após verificar |
| **sem** WhatsApp configurado, nasce `ATIVO` sem OTP | **RN-VER-07 (degradação graciosa)**: sem canal de entrega, exigir código trancaria todo mundo fora |
| rejeita telefone / e-mail / CPF já cadastrados | unicidade — o índice do banco é a última linha de defesa, não a primeira |
| rejeita CPF inválido | dígito verificador (`Documentos.cpfValido`) |
| `criar` exige senha ao criar pelo gerente | conta sem senha utilizável não deve existir por essa via |
| `criar` sem papéis aplica `PASSAGEIRO`/`ATIVO` | default previsível |
| **bloqueia excluir a própria conta** | **DT-02** — evita o gestor se trancar para fora |
| **bloqueia remover/inativar/suspender o último gerente ativo** | **DT-02** — sistema sem gestor é sistema morto |
| permite remover gerente quando há outro ativo | a trava não pode ser rígida demais |
| `atualizar`: senha em branco mantém o hash; informada é re-hasheada | editar cadastro não pode zerar a senha nem gravá-la em claro |
| `completarPerfil` grava telefone e limpa `perfilIncompleto` | SPEC-08 — conta do Google sai do limbo |

## `ViagemServiceTest` — 15 cenários (SPEC-05/06)

Criação de viagem imprevista, materialização a partir de linha (designação), ciclo de status.

| Cenário | Protege |
|---|---|
| caminho feliz salva `IMPREVISTA` + `PLANEJADA` | contrato básico |
| motorista sem o papel `MOTORISTA` é rejeitado | RBAC também **dentro** do domínio, não só na rota |
| chegada não posterior à saída é rejeitada | viagem impossível no calendário |
| **conflito de veículo** na imprevista é rejeitado | RN-VIA-08 — o mesmo veículo em duas viagens sobrepostas |
| **conflito de horário do motorista** na designação é rejeitado | RN-VIA-08 — dupla escala |
| designar materializa `ROTINEIRA` com a origem da linha | SPEC-06 — a linha é template, a viagem é a instância |
| linha desabilitada / que não opera no dia / já designada na data | três formas de designação inválida |
| `alterarStatus`: **motorista não altera viagem de outro** | isolamento por dono — o motorista só governa a própria viagem |
| `alterarStatus`: motorista altera a própria; gerente altera qualquer uma | a permissão é assimétrica de propósito |
| `painelSemana` monta 7 dias (domingo→sábado) | a grade da tela não pode "pular" dia |
| `excluir` remove e audita · `listarDoMotorista` delega | trilha e consulta |

## `SolicitacaoViagemServiceTest` — 18 cenários (SPEC-09/11)

O fluxo do passageiro: pedir por linha, pedir sob demanda, cancelar, e a avaliação do gestor.

| Cenário | Protege |
|---|---|
| sem viagem designada → `PENDENTE`; com viagem → `ALOCADA` | **alocação automática** — o passageiro não espera o gestor quando já há viagem |
| pendente **vira ALOCADA** quando a viagem passa a existir | a listagem reconcilia sozinha |
| linha inativa / data no passado / dia em que a linha não opera | as três recusas de solicitação por linha |
| duplicada (mesma linha+data) é rejeitada | evita dobrar a demanda por engano |
| **passageiro não cancela solicitação de outro** | **isolamento por dono** (o teste de API repete isso via HTTP) |
| o próprio dono cancela; já cancelada é no-op | idempotência do cancelamento |
| `solicitarSobDemanda` nasce `SOB_DEMANDA` + `PENDENTE` com destino/horário | SPEC-11 — pedido sem linha |
| duplicata sob demanda (mesmo destino+data) rejeitada | **RN-AVU-07** |
| `aprovar` aloca a viagem, marca `ALOCADA` e **notifica** | **RN-AVU-03** — aprovar sem avisar não serve |
| `recusar` exige motivo e notifica | recusa sem motivo é ruído para o passageiro |
| `linhasQueOperamEm` / `gradeSemanal` | a grade que a tela do passageiro consome |

## `VerificacaoServiceTest` — 11 cenários (SPEC-12, TDD)

Engine de OTP. **Escrito antes da implementação.**

| Cenário | Protege |
|---|---|
| persiste o **hash**, nunca o código cru | Art. XI — vazamento do banco não entrega códigos válidos |
| invalida os códigos anteriores da mesma finalidade | um código vigente por vez |
| código correto e no prazo marca usado | uso único |
| código errado incrementa tentativas e lança **mensagem genérica** | **RN-VER-04** — não revelar se o código existe |
| **na 5ª tentativa errada faz lockout** | **RN-VER-05** — trava a força bruta de 6 dígitos |
| código expirado / sem código ativo são rejeitados | janela de validade |
| **reenvio dentro do cooldown de 60s é rejeitado** | **RN-VER-06** — evita rajada de SMS/WhatsApp |
| `confirmarTelefone` promove `PENDENTE → ATIVO` | RN-VER-02 |

> Desde a SPEC-13, validade/tentativas/cooldown vêm do **feature toggle**. O teste injeta um
> `FeatureFlagService` que devolve os **defaults do código** — exatamente o comportamento sem
> configuração no banco (RN-FLG-02), o que preserva estes cenários intactos.

## `RecuperacaoSenhaServiceTest` — 9 cenários (SPEC-12)

| Cenário | Protege |
|---|---|
| usuário ativo recebe OTP por WhatsApp **ou** e-mail | os dois métodos de reset |
| **identificador desconhecido não emite código e não lança** | **anti-enumeração** — a tela responde igual para quem existe e para quem não existe |
| usuário inativo não recebe código | conta suspensa não se recupera sozinha |
| `redefinir` valida o código e grava a senha com BCrypt | o reset de fato troca a senha |
| conta ativa **sem senha** (cadastro pelo bot) define a primeira senha | **RN-REC-05** — passageiro do WhatsApp ganha acesso à web |
| senha curta é rejeitada **antes** de validar o código | não consome o código por erro de formulário |
| código inválido propaga e não grava a senha | |

## `ConviteServiceTest` — 11 cenários (#20, ADR-11, SPEC-12)

| Cenário | Protege |
|---|---|
| convidar cria `PENDENTE` com o papel e gera token; devolve o link | onboarding por convite |
| rejeita telefone já cadastrado | |
| ativar com token válido define a senha, ativa a conta e **consome** o token | uso único |
| token inexistente / expirado / senha curta são rejeitados | as três recusas de ativação |
| **token de verificação de e-mail não pode definir senha** | SPEC-12 — finalidades não se misturam (seria escalonamento) |
| `enviarVerificacaoEmail` cria token `VERIFICAR_EMAIL`; sem e-mail é no-op | |
| `verificarEmail` marca `email_verificado_em`; finalidade errada é rejeitada | |

## `LinhaProgramadaServiceTest` — 9 cenários (SPEC-06)

Criação com validação de horários (chegada > saída, retorno > chegada), exigência de pelo menos um
dia da semana, origem default = **cidade-sede**, `alternarAtiva`, e a trava de exclusão: **linha com
viagens materializadas não é excluída** (desabilita-se) — senão o histórico perderia a referência.

## `CidadeServiceTest` — 7 cenários (DT-01)

`excluir` remove **as viagens vinculadas em cascata** antes da cidade (a FK impedia a exclusão — era
a DT-01) e devolve a contagem, que a tela exibe no aviso de confirmação. Também: `trim` do nome, UF
em maiúsculas, e não-encontrado.

## `VeiculoServiceTest` — 8 cenários (SPEC-03, DT-04)

Normalização de placa (sem hífen, maiúsculas), unicidade **entre ativos** (na criação e na
atualização), **soft-delete** (`removidoEm`), e `listarDisponiveis()` filtrando `DISPONIVEL` —
a correção da **DT-04**, que antes oferecia veículo em manutenção para designação.

## `EnderecoServiceTest` — 4 cenários (SPEC-07)

Cria quando há dados (normalizando o CEP), **não persiste nada** com formulário vazio, e
**atualiza o mesmo registro** quando já existe (1-para-1 com o usuário — sem duplicar endereço).

## `NotificacaoServiceTest` — 5 cenários

Roteamento multicanal: envia pelos canais pedidos, **ignora tipo sem canal registrado** (o sistema
não quebra se um canal for removido), in-app com o id do usuário, contagem e marcação de lidas.

## `AuditoriaServiceTest` — 5 cenários (#19)

As três categorias (`SEGURANCA`, `OPERACAO`, `SISTEMA`) e as duas visões: SYSADMIN vê tudo, GERENTE
vê só `OPERACAO`.

## `ConfiguracaoServiceTest` — 9 cenários (DT-10, ADR-10)

Timeout de sessão com **default 30** para ausente/inválido e clamp em [1, 1440]; cidade-sede com
UUID, branco = ausente, `null` grava vazio; `salvar` faz upsert.

## `IdentidadeOauthServiceTest` — 7 cenários (SPEC-08)

A resolução de identidade em 3 passos: vínculo existente → **e-mail verificado** adota a conta →
auto-provisão de `PASSAGEIRO` com perfil incompleto. O cenário-chave de segurança:
**e-mail NÃO verificado não adota conta existente** — senão bastaria criar uma conta no provedor com
o e-mail da vítima para assumir o cadastro dela.

## `OnboardingServiceTest` — 3 cenários (SPEC-11)

Cadastro pelo bot: cria `PASSAGEIRO` **ATIVO sem senha** (RN-ONB-02 — quem entra pelo WhatsApp não
precisa de senha) e salva o endereço; recusa CPF inválido e telefone já cadastrado.

## `DocumentosTest` — 6 cenários (util)

`apenasDigitos` (nulo vira vazio), `pareceEmail` (o detector que decide e-mail × telefone no login),
`telefoneDeJid` (SPEC-10), `variantesTelefoneBr` (o 9º dígito — número antigo × novo) e
`cpfValido` (aceita com/sem máscara; rejeita tamanho errado, dígitos repetidos e verificador ruim).
