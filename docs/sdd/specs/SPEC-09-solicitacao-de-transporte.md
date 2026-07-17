# SPEC-09 — Solicitação de Transporte (Passageiro)

| | |
|---|---|
| **Área** | `SOL` (solicitações de transporte) |
| **Papéis** | PASSAGEIRO (próprio); GERENTE/MOTORISTA/SYSADMIN veem dados do passageiro nas suas telas |
| **Status geral** | ✅ Implementado (via sistema) — migration **V11** (`solicitacoes_viagem`), tela com 2 abas + testes. WhatsApp: ⏳ próxima etapa |
| **Constituição** | Artigos II (camadas), IV (migrations forward-only), VI (UUID/enums VARCHAR), X (HTMX/Thymeleaf) |
| **Relacionada** | [SPEC-06](SPEC-06-viagens-rotineiras-e-imprevistas.md) (linhas/designação), [SPEC-07](SPEC-07-endereco-do-passageiro.md) (endereço de embarque), [SPEC-01](SPEC-01-autenticacao.md) (RBAC) |
| **Código** | `SolicitacaoViagem`, `StatusSolicitacao`, `SolicitacaoViagemRepository`, `SolicitacaoViagemService`, `SolicitacaoController`, `passageiro/solicitacoes.html` |

---

## 1. Objetivo

Entregar a **função-fim do passageiro**: ver as **linhas disponíveis** e **solicitar transporte**
para uma data, acompanhando depois em que viagem foi **alocado**. É a contraparte da gestão de
viagens (SPEC-06): o gerente cria linhas e designa viagens; o passageiro as consome.

> **Decisão do dono do projeto:** *"O carro irá buscar o passageiro no seu local, então não é
> necessário ter local de partida — apenas motorista, carro, horário e destino. O passageiro não
> pode ver informações de outros passageiros (só a viagem)."*

Esta etapa cobre a solicitação **via sistema**. A solicitação **via WhatsApp** (Evolution API) é a
etapa seguinte e reaproveitará o mesmo `SolicitacaoViagemService`.

---

## 2. Escopo

### 2.1 Inclui
- Tela do passageiro (`/solicitacoes`) com **duas abas**:
  1. **Linhas disponíveis** — **calendário** (seleção de data) + **grade semanal** (panorama das linhas
     ativas por dia da semana, estilo grade de horários). Ao escolher uma data, mostra as linhas que
     **operam naquele dia**, cada uma com um formulário curto (observação opcional) para **solicitar**
     para a data selecionada. Clicar num dia na grade salta o calendário para a próxima ocorrência.
  2. **Minhas viagens** — as solicitações do próprio passageiro, com status; quando **alocada**,
     mostra **motorista, veículo, horário e destino** da viagem (sem local de partida).
- **Etiqueta visual de tipo** (faixa no topo do card + cor do botão/badge): **pré-definida**
  (rotineira) = **azul**; **imprevista** = **laranja**. Hoje toda solicitação parte de uma linha
  (pré-definida/azul); o laranja já fica preparado para quando a viagem alocada for imprevista.
- **Alocação automática**: a solicitação nasce `ALOCADA` se a viagem da linha+data já estiver
  designada; senão fica `PENDENTE` e é alocada assim que o gerente designar (resolvido na listagem).
- **Cancelamento** pelo próprio passageiro.
- **Privacidade**: cada passageiro só vê/cancela as **próprias** solicitações; nenhuma tela do
  passageiro expõe dados de outros passageiros.

### 2.2 Não inclui (futuro)
- Solicitação por **WhatsApp** (próxima etapa).
- Workflow de **aprovação/recusa explícita** pelo gerente (hoje a alocação deriva da designação da
  viagem; `RECUSADA` já existe no enum/CHECK para esse uso futuro).
- **Assentos/capacidade** (`assentos_viagem`) e acompanhante: fora do escopo desta etapa.
- Embarque a partir do **endereço** estruturado (SPEC-07) — o modelo já assume "busca no local",
  mas o roteiro de embarque não é calculado aqui.

---

## 3. Modelagem (migration V11)

Tabela nova **`solicitacoes_viagem`** (aditiva, forward-only — Art. IV). O destino e os horários
**derivam da linha** (e da viagem, quando alocada): não há coluna de origem/partida.

```
solicitacoes_viagem
  id               UUID PK (gen_random_uuid)
  passageiro       UUID NOT NULL REFERENCES usuarios(id)
  linha_programada UUID NOT NULL REFERENCES linhas_programadas(id) ON DELETE CASCADE
  data_desejada    DATE NOT NULL
  viagem           UUID REFERENCES viagens(id) ON DELETE SET NULL   -- preenchida na alocação
  status           VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                   CHECK (status IN ('PENDENTE','ALOCADA','CANCELADA','RECUSADA'))
  observacao       VARCHAR(280)
  criado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW()

ix_solicitacoes_viagem_passageiro (passageiro, criado_em DESC)   -- listagem do passageiro
ux_solicitacao_viagem_unica (passageiro, linha_programada, data_desejada) WHERE status <> 'CANCELADA'
```

> As tabelas placeholder `solicitacoes_transporte` e `assentos_viagem` (criadas especulativamente na
> V2, nunca mapeadas) **não** são usadas e seguem intactas; podem ser removidas num incremento de
> limpeza futuro (DT a registrar).

`StatusSolicitacao` é VARCHAR (Art. VI), espelhando o CHECK.

---

## 4. Regras de negócio

| Regra | Descrição |
|-------|-----------|
| **RN-SOL-01** | Só é possível solicitar uma **linha ativa**. |
| **RN-SOL-02** | A data deve ser **hoje ou futura**. |
| **RN-SOL-03** | A linha precisa **operar no dia da semana** da data escolhida. |
| **RN-SOL-04** | **Sem duplicata**: um passageiro não pode ter duas solicitações ativas (não canceladas) para a mesma linha+data (índice único parcial). |
| **RN-SOL-05** | A solicitação é **alocada** quando existe a viagem da linha+data (na criação ou, depois, na listagem). |
| **RN-SOL-06** | Apenas o **próprio dono** cancela; o cancelamento solta a viagem e libera nova solicitação. |
| **RN-SOL-07** | **Isolamento**: o passageiro só acessa as próprias solicitações; nenhuma exibe dados de outros passageiros. |
| **RN-SOL-08** (✅ realizada na [SPEC-11](SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md)) | **Solicitação específica/sob demanda**: além de pedir uma viagem **já existente** (linha designada pelo gerente — escopo desta spec), o passageiro abre uma solicitação **sob demanda** informando **destino + data + horário + condições** (comorbidade/deficiência). Não casa com linha pré-definida: vira uma demanda que o gestor **avalia, aprova (designando viagem imprevista) ou recusa**. Implementada pela **SPEC-11** (migration V13: `solicitacoes_viagem.tipo`, `linha_programada` nullable, `cidade_destino`), via bot do WhatsApp + painel do gestor. |

---

## 5. RBAC e rotas

`/solicitacoes/**` exige o papel **PASSAGEIRO** (`SecurityConfig`). CSRF permanece **ativo** (os
formulários de página inteira injetam o token via Thymeleaf — não estão na lista de exceção HTMX).

| Método | Rota | Ação |
|--------|------|------|
| GET | `/solicitacoes` | Tela (abas: linhas disponíveis + minhas viagens) |
| POST | `/solicitacoes` | Solicitar (linhaId, data, observacao) |
| POST | `/solicitacoes/{id}/cancelar` | Cancelar a própria solicitação |

---

## 6. Testes

- **`SolicitacaoViagemServiceTest`** (Mockito): pendente vs. alocada, linha inativa, data passada,
  dia sem operação, duplicata, cancelamento próprio vs. de outro, alocação tardia na listagem.
- **`SolicitacaoControllerTest`** (MockMvc + Testcontainers): RBAC (PASSAGEIRO 200; GERENTE/MOTORISTA
  403; anônimo redireciona), fluxo solicitar→ver, e **isolamento** (um passageiro não vê o do outro).

---

## 7. Próximos passos

1. **Solicitação via WhatsApp** (Evolution API) → mesmo `SolicitacaoViagemService`.
2. **Solicitação específica/sob demanda** (**RN-SOL-08**): data + veículo PCD/ambulância/acompanhante,
   gerando demanda imprevista (laranja) para o gestor designar.
3. **Aprovação/recusa** explícita do gestor (usar `RECUSADA`) e visão do gerente das solicitações.
4. **Assentos/capacidade** (`assentos_viagem`) e acompanhante.
5. Embarque a partir do **endereço** estruturado (SPEC-07).
