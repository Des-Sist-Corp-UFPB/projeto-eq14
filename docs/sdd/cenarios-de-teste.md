# Cenários de Teste — CALADRIUS

> Catálogo de cenários (Dado / Quando / Então) para montar os testes. Legenda:
> ✅ **já automatizado** (existe teste de unidade) · 🟡 **sugerido** (montar você).
> Os 25 testes de unidade atuais passam (`mvn test` dos `*ServiceTest`); o teste de
> contexto com Testcontainers depende de Docker compatível (ver nota no fim).

---

## 1. Lacunas (DT-01/02/04)

### DT-01 — Exclusão de cidade em cascata (`CidadeServiceTest`) ✅
- **CA** *Dado* uma cidade com 3 viagens, *quando* excluída, *então* as 3 viagens são
  removidas antes da cidade e o método retorna `3`.
- **CA** *Dado* uma cidade sem viagens, *quando* excluída, *então* `deleteByCidadeDestino_Id`
  **não** é chamado e só a cidade é apagada (retorna `0`).
- **CA** *Dado* um id inexistente, *quando* excluir, *então* `RecursoNaoEncontradoException`.

### DT-02 — Trava do último gerente / suspensão (`UsuarioServiceTest`) ✅
- **CA** *Dado* o id do próprio usuário, *quando* `excluir(id, id)`, *então* rejeita
  ("própria conta").
- **CA** *Dado* um único gerente ativo, *quando* `excluir`/`atualizar(INATIVO)`/
  `solicitarSuspensao`, *então* rejeita ("último gerente").
- **CA** *Dado* dois gerentes ativos, *quando* excluir um, *então* `removidoEm` é preenchido.
- **CA** *Dado* um passageiro ativo, *quando* `solicitarSuspensao`, *então* status `SUSPENSO`.

### DT-04 — Veículos disponíveis (`VeiculoServiceTest`) ✅
- **CA** *Dado* veículos em vários status, *quando* `listarDisponiveis()`, *então* consulta
  apenas `removidoEm IS NULL` **e** `status = DISPONIVEL`.

---

## 2. Viagens (SPEC-05) — payloads e cenários 🟡

`POST /viagens` (form-urlencoded; forma do `ViagemForm`). **JSON equivalente** para fixtures:
```json
{
  "veiculoId": "UUID de veículo DISPONIVEL",
  "motoristaId": "UUID de usuário com papel MOTORISTA",
  "cidadeDestinoId": "UUID de cidade (ex.: João Pessoa)",
  "dataViagem": "2026-06-17",
  "horarioSaida": "08:00",
  "horarioChegada": "10:30"
}
```
| Cenário | Então |
|---|---|
| Válido | `200`, status **PLANEJADA**, `criadoPor` = gerente logado; auditoria `VIAGEM_CRIADA` |
| `horarioChegada` ≤ `horarioSaida` | erro: *"O horário de chegada deve ser após o horário de saída"* |
| `motoristaId` de um PASSAGEIRO | erro: *"O usuário selecionado não possui o papel de motorista"* |
| `veiculoId` soft-deletado | `RecursoNaoEncontradoException` |
| Campo faltando | validação Bean (*"Selecione o veículo"* etc.) |

> **Sugestão de `ViagemServiceTest`** (ainda não existe): cobrir as 5 linhas acima com Mockito
> (mockar `VeiculoRepository`/`UsuarioRepository`/`CidadeRepository`/`ViagemRepository` +
> `AuditoriaService`).

---

## 3. Convite/ativação (#20) (`ConviteServiceTest`) ✅
- **CA** *Dado* telefone livre, *quando* `convidar(...)`, *então* cria usuário **PENDENTE** com
  o papel, salva token e retorna link `/ativar?token=...`.
- **CA** *Dado* telefone já cadastrado, *quando* convidar, *então* rejeita ("Telefone").
- **CA** *Dado* token válido, *quando* `ativar(token, senha≥6)`, *então* status **ATIVO**, hash
  trocado, token marcado usado.
- **CA** token inexistente → "inválido"; token expirado → "expirado"; senha < 6 → "6 caracteres".

🟡 **Sugeridos (web/integração):**
- `POST /usuarios/convidar` exige GERENTE; `POST /admin/convites` exige SYSADMIN (403 caso contrário).
- `GET /ativar?token=...` é público; `POST /ativar` ativa e redireciona para `/login?ativado`.

---

## 4. SYSADMIN + RBAC (#17) 🟡
- *Dado* usuário sem SYSADMIN, *quando* `GET /admin/**`, *então* **403/negado**.
- *Dado* usuário SYSADMIN, *quando* `GET /admin`, *então* `200`.
- *Dado* o admin semeado, *então* possui papéis **GERENTE + SYSADMIN**.
- Migration **V4**: `papeis_usuario` aceita `SYSADMIN` no CHECK (teste de contexto/migração).

---

## 5. Sessão dinâmica (#18 / DT-10) 🟡
- *Dado* `sessao.timeout_minutos = 45`, *quando* nova sessão é criada, *então*
  `maxInactiveInterval == 45*60`. (Teste de integração com login + inspeção da sessão.)
- `ConfiguracaoService.getTimeoutSessaoMinutos()` cai no padrão **30** se ausente/ inválido;
  `setTimeoutSessaoMinutos` faz clamp em [1, 1440]. (Unidade — fácil de montar.)
- `getCidadeSedeId()` ignora valor em branco.

---

## 6. Auditoria (#19) 🟡
- *Dado* um login válido, *então* há registro `LOGIN_SUCESSO` (SEGURANCA); login inválido →
  `LOGIN_FALHA` (resultado FALHA); logout → `LOGOUT`.
- *Dado* criar/editar/excluir um cadastro, *então* há registro **OPERACAO** correspondente
  (`*_CRIADO/ATUALIZADO/EXCLUIDO`).
- *Dado* GERENTE em `/historico`, *então* vê **apenas** eventos OPERACAO; SYSADMIN em
  `/admin/auditoria` vê **todos**.
- `AuditoriaServiceTest` (unidade): `registrarOperacao` lê o principal do `SecurityContext`;
  `listarOperacao` filtra por categoria OPERACAO.

---

## 7. Notificações (#20) 🟡
- *Dado* `notificarInApp(uid, ...)`, *então* `contarNaoLidas(uid)` aumenta e a notificação
  aparece em `naoLidas(uid)`.
- *Dado* `marcarTodasLidas(uid)`, *então* `contarNaoLidas(uid) == 0`.
- Canais: `NotificacaoInAppCanal` persiste; `Email`/`Whatsapp` são stubs (apenas logam) —
  testar que `enviar(..., EMAIL, WHATSAPP)` não persiste no sino e que IN_APP persiste.

---

## 8. SPEC-06 (viagens rotineiras) ⬜
Cenários (CA-VIA-08..14) já estão escritos na
[SPEC-06 §7](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md). Os testes serão montados junto
da implementação (#21), **após sua aprovação da spec**.

---

## Nota — Testcontainers (DT-11 ✅ resolvido)
O teste de contexto (`CaladriusApplicationTests`) **agora roda**: o bump de Testcontainers
**1.20.4 → 1.21.4** corrigiu a negociação da API com Docker Engines novos (a 1.20.4 enviava a
API 1.32, rejeitada por engines com mínimo 1.40). Suíte completa: **26 testes ✓**
(25 de unidade + 1 de contexto que sobe um PostgreSQL real e valida migrations V1→V7 +
`ddl-auto: validate`).
