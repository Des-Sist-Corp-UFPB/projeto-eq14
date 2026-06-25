# Relatório de Avaliação — EQ14 (DSC)

| | |
|---|---|
| **Data** | 2026-06-25 |
| **Repositório** | https://github.com/des-sist-corp-ufpb/projeto-eq14 |
| **Aplicação** | https://eq14.dsc.rodrigor.com |
| **Período de atividade** | 2026-06-25 → 2026-06-25 |
| **Total de commits** (sem merges) | 1 |
| **Integrantes** | Luiz Eduardo De Lima Neves (@Edwdilima) |

---

## 1. Tecnologias

- Spring Boot 3.4.5
- Thymeleaf
- Flyway (10 migrations)
- Spring Security
- Testcontainers

---

## 2. Análise Funcional

### Endpoints REST (61 mapeados)

| Método | Path | Arquivo |
|--------|------|---------|
| `GET` | `/admin` | `AdminController.java` |
| `GET` | `/analise` | `AnaliseController.java` |
| `GET` | `/ativar` | `AtivacaoController.java` |
| `POST` | `/ativar` | `AtivacaoController.java` |
| `GET` | `/admin/auditoria` | `AuditoriaController.java` |
| `GET` | `/historico` | `AuditoriaController.java` |
| `GET` | `/login` | `AuthController.java` |
| `GET` | `/registrar` | `AuthController.java` |
| `POST` | `/registrar` | `AuthController.java` |
| `DELETE` | `/cidades/{id}` | `CidadeController.java` |
| `GET` | `/cidades` | `CidadeController.java` |
| `GET` | `/cidades/fragmento-tabela` | `CidadeController.java` |
| `GET` | `/cidades/nova` | `CidadeController.java` |
| `GET` | `/cidades/{id}/editar` | `CidadeController.java` |
| `POST` | `/cidades` | `CidadeController.java` |
| `PUT` | `/cidades/{id}` | `CidadeController.java` |
| `GET` | `/admin/configuracoes` | `ConfiguracaoController.java` |
| `POST` | `/admin/configuracoes` | `ConfiguracaoController.java` |
| `GET` | `/conta/completar` | `ContaController.java` |
| `POST` | `/conta/completar` | `ContaController.java` |
| `POST` | `/conta/suspensao` | `ContaController.java` |
| `GET` | `/admin/convites` | `ConviteController.java` |
| `GET` | `/usuarios/convidar` | `ConviteController.java` |
| `POST` | `/admin/convites` | `ConviteController.java` |
| `POST` | `/usuarios/convidar` | `ConviteController.java` |
| `GET` | `/` | `HomeController.java` |
| `GET` | `/linhas` | `LinhaController.java` |
| `GET` | `/linhas/nova` | `LinhaController.java` |
| `GET` | `/linhas/{id}/editar` | `LinhaController.java` |
| `POST` | `/linhas` | `LinhaController.java` |
| `POST` | `/linhas/{id}/alternar` | `LinhaController.java` |
| `POST` | `/linhas/{id}/editar` | `LinhaController.java` |
| `POST` | `/linhas/{id}/excluir` | `LinhaController.java` |
| `GET` | `/minhas-viagens` | `MotoristaViagemController.java` |
| `POST` | `/minhas-viagens/{id}/status` | `MotoristaViagemController.java` |
| `POST` | `/notificacoes/marcar-lidas` | `NotificacaoController.java` |
| `GET` | `/perfil` | `PerfilController.java` |
| `POST` | `/perfil` | `PerfilController.java` |
| `GET` | `/ping` | `PingController.java` |
| `DELETE` | `/usuarios/{id}` | `UsuarioController.java` |
| `GET` | `/usuarios` | `UsuarioController.java` |
| `GET` | `/usuarios/fragmento-tabela` | `UsuarioController.java` |
| `GET` | `/usuarios/novo` | `UsuarioController.java` |
| `GET` | `/usuarios/{id}/editar` | `UsuarioController.java` |
| `POST` | `/usuarios` | `UsuarioController.java` |
| `PUT` | `/usuarios/{id}` | `UsuarioController.java` |
| `DELETE` | `/veiculos/{id}` | `VeiculoController.java` |
| `GET` | `/veiculos` | `VeiculoController.java` |
| `GET` | `/veiculos/fragmento-tabela` | `VeiculoController.java` |
| `GET` | `/veiculos/novo` | `VeiculoController.java` |
| `GET` | `/veiculos/{id}/editar` | `VeiculoController.java` |
| `POST` | `/veiculos` | `VeiculoController.java` |
| `PUT` | `/veiculos/{id}` | `VeiculoController.java` |
| `DELETE` | `/viagens/{id}` | `ViagemController.java` |
| `GET` | `/viagens` | `ViagemController.java` |
| `GET` | `/viagens/nova` | `ViagemController.java` |
| `GET` | `/viagens/semana` | `ViagemController.java` |
| `POST` | `/viagens` | `ViagemController.java` |
| `POST` | `/viagens/designar` | `ViagemController.java` |
| `POST` | `/viagens/{id}/status` | `ViagemController.java` |
| `GET` | `/whatsapp` | `WhatsappController.java` |

### Entidades / Tabelas (33 encontradas)

- `viagens`
- `veiculos`
- `linhas_programadas`
- `notificacoes`
- `configuracoes_sistema`
- `cidades`
- `enderecos`
- `tokens_ativacao`
- `identidades_oauth`
- `log_auditoria`
- `usuarios`
- `municipios`
- `tokens_ativacao (via V7__criar_tokens_e_notificacoes.sql)`
- `notificacoes (via V7__criar_tokens_e_notificacoes.sql)`
- `linhas_programadas (via V9__criar_linhas_e_evoluir_viagens.sql)`
- `linha_dias (via V9__criar_linhas_e_evoluir_viagens.sql)`
- `usuarios (via V2__criar_schema_caladrius.sql)`
- `papeis_usuario (via V2__criar_schema_caladrius.sql)`
- `perfis_passageiro (via V2__criar_schema_caladrius.sql)`
- `perfis_motorista (via V2__criar_schema_caladrius.sql)`
- `perfis_gerente (via V2__criar_schema_caladrius.sql)`
- `cidades (via V2__criar_schema_caladrius.sql)`
- `veiculos (via V2__criar_schema_caladrius.sql)`
- `escalas_motorista (via V2__criar_schema_caladrius.sql)`
- `viagens (via V2__criar_schema_caladrius.sql)`
- `solicitacoes_transporte (via V2__criar_schema_caladrius.sql)`
- `assentos_viagem (via V2__criar_schema_caladrius.sql)`
- `produto (via V1__criar_tabela_produto.sql)`
- `identidades_oauth (via V10__criar_identidades_oauth.sql)`
- `configuracoes_sistema (via V5__criar_configuracoes_sistema.sql)`
- `municipios (via V8__criar_municipios_e_enderecos.sql)`
- `enderecos (via V8__criar_municipios_e_enderecos.sql)`
- `log_auditoria (via V6__criar_log_auditoria.sql)`

### Migrations (10 arquivos)

- `V10__criar_identidades_oauth.sql`
- `V1__criar_tabela_produto.sql`
- `V2__criar_schema_caladrius.sql`
- `V3__remover_produto_e_seed_cidades.sql`
- `V4__adicionar_papel_sysadmin.sql`
- `V5__criar_configuracoes_sistema.sql`
- `V6__criar_log_auditoria.sql`
- `V7__criar_tokens_e_notificacoes.sql`
- `V8__criar_municipios_e_enderecos.sql`
- `V9__criar_linhas_e_evoluir_viagens.sql`

---

## 3. Análise Arquitetural

| Aspecto | Status | Observação |
|---------|--------|-----------|
| Arquitetura em camadas | ✅ | controller=✅  service=✅  repository=✅ |
| Testes automatizados | ✅ | 20 arquivo(s) de teste |
| Migrations versionadas | ✅ | 10 migration(s) |
| Logging | ✅ | @Slf4j / LoggerFactory / logging.getLogger detectado |
| Autenticação / Segurança | ✅ | Spring Security / JWT / decorator detectado |
| DTOs / Separação de dados | ❌ | não detectado |
| Tratamento global de exceções | ✅ | @ControllerAdvice / @ExceptionHandler detectado |
| Documentação de API (OpenAPI) | ❌ | não detectado |
| Variáveis de ambiente | ✅ | .env / @Value / os.environ detectado |
| Dockerfile / docker-compose | ❌ | não encontrado |

---

## 4. Contribuição por Usuário

### Resumo

| Usuário | Commits | % commits | Linhas adicionadas | Linhas no código atual | % código atual |
|---------|---------|-----------|-------------------|----------------------|----------------|
| Luiz Eduardo De Lima Neves (@Edwdilima) | 1 | 100% | 16.054 | 12.093 | 100% |

### Contribuição por Camada

| Camada | Total linhas | Luiz Eduardo De Lima Neves (@Edwdilima) |
|--------|-------------|---------|
| Controller | 4.818 | 100% |
| Repository | 476 | 100% |
| Service | 3.304 | 100% |
| Test | 380 | 100% |

---

## 5. Contribuição por Funcionalidade

Baseado em `git blame` nos arquivos de controller e service.

| Arquivo | Total linhas | Luiz Eduardo De Lima Neves (@Edwdilima) |
|---------|-------------|---------|
| `form.html` | 571 | 100% |
| `UsuarioService.java` | 306 | 100% |
| `V8__criar_municipios_e_enderecos.sql` | 267 | 100% |
| `ViagemService.java` | 251 | 100% |
| `layout.html` | 244 | 100% |
| `registro.html` | 232 | 100% |
| `lista.html` | 230 | 100% |
| `LinhaProgramadaServiceTest.java` | 209 | 100% |
| `UsuarioServiceTest.java` | 207 | 100% |
| `ViagemServiceTest.java` | 182 | 100% |
| `login.html` | 178 | 100% |
| `tabela.html` | 176 | 100% |
| `IdentidadeOauthServiceTest.java` | 173 | 100% |
| `V2__criar_schema_caladrius.sql` | 172 | 100% |
| `ViagemController.java` | 167 | 100% |
| `ConviteService.java` | 162 | 100% |
| `UsuarioController.java` | 153 | 100% |
| `VeiculoController.java` | 149 | 100% |
| `ConviteServiceTest.java` | 141 | 100% |
| `IdentidadeOauthService.java` | 136 | 100% |
| `LinhaProgramadaService.java` | 133 | 100% |
| `caladrius.css` | 126 | 100% |
| `linha.html` | 125 | 100% |
| `ConfiguracaoServiceTest.java` | 124 | 100% |
| `LinhaController.java` | 123 | 100% |
| `CidadeController.java` | 122 | 100% |
| `ContaController.java` | 119 | 100% |
| `inicio.html` | 118 | 100% |
| `VeiculoService.java` | 117 | 100% |
| `VeiculoServiceTest.java` | 115 | 100% |
| `AuditoriaService.java` | 112 | 100% |
| `NotificacaoServiceTest.java` | 108 | 100% |
| `CidadeService.java` | 103 | 100% |
| `EnderecoService.java` | 102 | 100% |
| `AuditoriaServiceTest.java` | 100 | 100% |
| `CidadeServiceTest.java` | 93 | 100% |
| `ConviteController.java` | 89 | 100% |
| `EnderecoServiceTest.java` | 87 | 100% |
| `ConfiguracaoService.java` | 83 | 100% |
| `ativar.html` | 80 | 100% |
| `VeiculoControllerTest.java` | 80 | 100% |
| `AuthController.java` | 79 | 100% |
| `auditoria.html` | 76 | 100% |
| `semana.html` | 74 | 100% |
| `completar.html` | 74 | 100% |
| `PerfilController.java` | 66 | 100% |
| `NotificacaoService.java` | 64 | 100% |
| `CaladriusOidcUserService.java` | 62 | 100% |
| `ConfiguracaoController.java` | 56 | 100% |
| `passageiros.html` | 56 | 100% |
| `CaladriusUserDetailsService.java` | 55 | 100% |
| `AuditoriaController.java` | 54 | 100% |
| `MotoristaViagemController.java` | 53 | 100% |
| `minhas-viagens.html` | 52 | 100% |
| `configuracoes.html` | 47 | 100% |
| `AtivacaoController.java` | 46 | 100% |
| `HomeController.java` | 45 | 100% |
| `V9__criar_linhas_e_evoluir_viagens.sql` | 44 | 100% |
| `CidadeControllerTest.java` | 43 | 100% |
| `home.html` | 42 | 100% |
| `CaladriusApplication.java` | 41 | 100% |
| `V7__criar_tokens_e_notificacoes.sql` | 39 | 100% |
| `CaladriusApplicationTests.java` | 38 | 100% |
| `V10__criar_identidades_oauth.sql` | 36 | 100% |
| `V6__criar_log_auditoria.sql` | 32 | 100% |
| `NotificacaoController.java` | 31 | 100% |
| `AnaliseController.java` | 30 | 100% |
| `icons.html` | 29 | 100% |
| `PingController.java` | 28 | 100% |
| `AdminController.java` | 25 | 100% |
| `V1__criar_tabela_produto.sql` | 25 | 100% |
| `V3__remover_produto_e_seed_cidades.sql` | 25 | 100% |
| `HomeControllerTest.java` | 24 | 100% |
| `V5__criar_configuracoes_sistema.sql` | 23 | 100% |
| `WhatsappController.java` | 22 | 100% |
| `V4__adicionar_papel_sysadmin.sql` | 21 | 100% |

---

*Relatório gerado automaticamente em 2026-06-25.*
*Os dados de contribuição são baseados em `git log --numstat` (linhas adicionadas) e `git blame` (linhas no código atual), excluindo commits de merge.*