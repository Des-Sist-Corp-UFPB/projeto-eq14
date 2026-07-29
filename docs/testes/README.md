# Testes automatizados — CALADRIUS (eq14)

Documentação da **suíte de testes**: o que cada cenário verifica e **por que ele existe**. É o
complemento executável do SDD — cada regra de negócio (`RN-*`) e critério de aceite (`CA-*`) das
[specs](../sdd/specs/) deveria ter, aqui, um teste que o sustenta.

> Um teste que não diz **por que existe** vira custo de manutenção. Por isso todo teste tem
> `@DisplayName` em português descrevendo o comportamento — e não o nome do método.

---

## 1. Números atuais

| Métrica | Valor |
|---|---|
| **Testes** | **346** (JUnit 5), 0 falhas |
| **Cobertura de linhas** | **87,3%** (2863/3278) |
| **Cobertura de instruções** | **87,5%** |
| **Cobertura de métodos** | **86,8%** |
| **Cobertura de ramos** | **69,7%** |
| **Gate no build** | `jacoco:check` — **falha** abaixo de **85%** (linhas e instruções) e **65%** (ramos) |

Cobertura por camada (linhas):

| Pacote | Cobertura | Leitura |
|---|---|---|
| `service` | **95,1%** | onde mora a regra de negócio — é o alvo prioritário |
| `dto` · `observabilidade` | **100%** | |
| `util` | 97,1% | |
| `domain.enums` | 96,2% | |
| `notificacao` | 89,7% | |
| `config` | 82,8% | filtros e listeners cobertos; `SecurityConfig` é exercitado pelos testes web |
| `domain` | 82,4% | entidades: o que não é lido pelas telas fica descoberto |
| `whatsapp` / `whatsapp.bot` | 82,4% / 81,1% | máquina de estados do bot e adaptador da Evolution |
| `controller` | 80,8% | |
| `security` | 75,0% | falta o `CaladriusOidcUserService` (exige simular o fluxo OIDC completo) |
| `exception` | 66,7% | classes de exceção quase sem lógica |

**Excluídos da medição** (e por quê): `DevSeed` (carga de dados de exemplo, `@Profile("dev")` — não
sobe em produção nem nos testes) e `CaladriusApplication` (só o `main()`). A exclusão está declarada
no `pom.xml`, ao lado da regra de 85%.

---

## 2. Como rodar

```bash
mvn test                     # só os testes (exige Docker para os Testcontainers)
mvn verify                   # testes + relatório + GATE de cobertura (85%)
mvn verify -Psecurity        # + SAST (SpotBugs/FindSecBugs) e OWASP Dependency-Check
```

Relatório HTML: `target/site/jacoco/index.html`. Dados brutos: `target/site/jacoco/jacoco.csv`.

Sem Java/Maven na máquina, dá para rodar o mesmo build dentro do contêiner (é o que o CI faz):

```bash
docker run --rm -v "$PWD":/app -w /app -v caladrius-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock --network host \
  maven:3.9.9-eclipse-temurin-21 mvn -B verify
```

> O `-v /var/run/docker.sock` é obrigatório: os testes de integração sobem um **PostgreSQL real**
> via Testcontainers.

---

## 3. Os três tipos de teste (e quando usar cada um)

| Tipo | Ferramenta | Custo | Usar para |
|---|---|---|---|
| **Unitário** | JUnit 5 + Mockito | ~ms | regra de negócio isolada: cálculos, validações, decisões do serviço |
| **Integração web / API** | `@SpringBootTest` + MockMvc + **Testcontainers** | ~s | contrato HTTP de verdade: rota, RBAC, CSRF, redirecionamento, HTMX, SQL real |
| **Contexto** | `@SpringBootTest` | ~s | o app **sobe** com o schema do Flyway (V1→V15) e `ddl-auto: validate` |

**Regra prática do projeto:** regra de negócio nova ⇒ teste **unitário**; rota/tela nova ⇒ teste
**de API**. Um critério de aceite geralmente merece os dois: o unitário prova a decisão, o de API
prova que a decisão chega ao usuário.

### O teste de contexto é o mais barato e o mais importante
`CaladriusApplicationTests` sobe o Spring inteiro contra um PostgreSQL real com **todas as
migrations aplicadas**. Como produção roda `ddl-auto: validate`, **qualquer divergência entre
entidade JPA e schema Flyway derruba este teste** — antes de derrubar o deploy.

### Base comum dos testes de API
`IntegracaoWebTestBase` concentra: o container PostgreSQL (**único**, compartilhado entre as classes,
para reaproveitar o cache do contexto do Spring), o `MockMvc` e os helpers de autenticação
(`comoGerente()`, `comoPassageiro()`, `comoSysadmin()`, `comoMotorista()`, `autenticar(usuario)`) e de
fixture (`persistir(...)`, `persistirVeiculo()`, `cidadeDestino()`, `telefoneUnico()`).

> **Principal sintético × persistido:** `comoGerente()` cria um principal em memória — basta para
> testar RBAC. Quando o endpoint **recarrega o usuário do banco** (criar viagem, gerar convite), é
> preciso `autenticar(persistir(...))`, senão a FK estoura. Está documentado em cada caso.

---

## 4. Índice por área

| Documento | Cobre |
|---|---|
| [01 — Serviços (regra de negócio)](01-servicos.md) | `UsuarioService`, `ViagemService`, `SolicitacaoViagemService`, `VerificacaoService`, `ConviteService`, `RecuperacaoSenhaService`, `LinhaProgramadaService`, `CidadeService`, `VeiculoService`, `EnderecoService`, `NotificacaoService`, `AuditoriaService`, `ConfiguracaoService`, `IdentidadeOauthService`, `OnboardingService`, `Documentos` |
| [02 — Web / API (HTTP, RBAC, HTMX)](02-web-api.md) | controllers de autenticação, usuários, veículos, cidades, viagens, linhas, solicitações, telas administrativas, páginas públicas e controle de acesso |
| [03 — WhatsApp e bot](03-whatsapp-e-bot.md) | `WhatsappService`, `EvolutionApiProvedor`, `BotAtendimentoService`, webhook |
| [04 — Feature toggle](04-feature-toggle.md) | `FeatureFlagService`, `MunicipioService`, modo de manutenção, bot on/off, entitlement (SPEC-13) |
| [05 — Segurança e observabilidade](05-seguranca-e-observabilidade.md) | login flexível, principal, auditoria de acesso, spans de negócio (SPEC-14) |

---

## 5. Convenções da suíte

1. **`@DisplayName` em português**, descrevendo o comportamento e citando a regra
   (`RN-VER-05`, `CA-FLG-01`, `DT-02`) — é o que aparece no relatório e liga o teste à spec.
2. **Um comportamento por teste.** Nome do método curto; a frase fica no `@DisplayName`.
3. **Mockito estrito** (`MockitoExtension`): stub não usado quebra o teste. Quando um stub serve só a
   parte dos cenários, marque-o `lenient()` **com comentário** explicando por quê.
4. **Testes de API não fazem mock de serviço.** Vão do controller ao banco — é o que dá confiança de
   que rota, segurança, transação e SQL combinam.
5. **Estado global volta ao padrão.** Testes que mexem em flags/configuração (estado compartilhado
   pelo contexto do Spring) restauram tudo em `@AfterEach`/`finally` — senão contaminam os seguintes.
6. **Sem `Thread.sleep`.** Tempo é injetado ou o cenário é construído com datas explícitas.
7. **Dados únicos por teste** (`telefoneUnico()`): a base é compartilhada entre as classes e há
   índices únicos parciais.

---

## 6. O que ainda **não** é coberto (dívidas conhecidas)

| Lacuna | Por quê / o que faria |
|---|---|
| `CaladriusOidcUserService` (SPEC-08) | exige simular o fluxo OIDC completo (`OidcUserRequest` + `ClientRegistration`); a lógica de resolução de identidade **já é coberta** por `IdentidadeOauthServiceTest` |
| Ramos (69,7%) | muitos `catch` de degradação e ternários de template ainda sem cenário próprio |
| Isolamento multi-tenant | não existe ainda — vira **critério de saída** da fase 2 da [SPEC-16](../sdd/specs/SPEC-16-organizacao-planos-e-pagamento.md) (CA-PAG-06) |
| Carga/performance | há um template k6 em `loadtest/`, fora do `mvn verify` |
| Front-end (JS/HTMX) | sem testes de navegador; o comportamento HTMX é verificado pelo **fragmento devolvido** no teste de API |
