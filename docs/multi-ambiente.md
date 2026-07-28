# Multi-ambiente por secretaria — runbook operacional

> **Decisão e avaliação das opções**: [`docs/sdd/specs/SPEC-15-multi-ambiente-por-secretaria.md`](sdd/specs/SPEC-15-multi-ambiente-por-secretaria.md)
> · **ADR-19** ([plano técnico §9](sdd/02-plano-tecnico.md)). Este documento é o **como operar**.

O CALADRIUS isola clientes pela **Opção A (instância por cliente / silo)**: **cada secretaria roda o
seu próprio _stack_** — app + banco dedicado + volume + subdomínio — totalmente separado dos demais.
**Não há conceito de "tenant" no código**; o isolamento é **físico**.

## Modelo mental

```
        Campina Grande                 Caruaru
   ┌───────────────────────┐   ┌───────────────────────┐
   │ caladrius-campina-*    │   │ caladrius-caruaru-*    │   ← Compose projects distintos
   │  app  ↔  db (dedicado) │   │  app  ↔  db (dedicado) │   ← bancos/volumes SEPARADOS
   │  127.0.0.1:8114        │   │  127.0.0.1:8115        │   ← portas distintas (loopback)
   └───────────┬───────────┘   └───────────┬───────────┘
               │                            │
        campina.caladrius.app        caruaru.caladrius.app     ← Caddy faz o proxy por subdomínio
```

| Isolado **por secretaria** | Compartilhado (do provedor) |
|---|---|
| Banco de dados + volume (dados) | O **host** (VM/servidor) |
| Containers (app + db), rede Docker | O **Caddy** (um proxy, N vhosts) |
| Subdomínio + porta no host | A **imagem** da app (a mesma do CI) |
| Segredos (`.env`): senha do banco, WhatsApp, Google, OTel | — |
| Admin-bootstrap (`DataInitializer` no 1º boot) | — |

## Pré-requisitos do host (uma vez)

- **Docker** + Docker Compose v2.
- **Caddy** (proxy reverso com TLS automático) já instalado e rodando.
- **DNS**: um registro por secretaria (`campina.caladrius.app`, `caruaru.caladrius.app`) — ou um
  wildcard `*.caladrius.app` apontando para o host.
- Este repositório clonado no host (para os arquivos `docker/docker-compose.tenant.yml` e
  `scripts/novo-ambiente.sh`).

## Provisionar uma nova secretaria

```bash
# 1) Gera ambientes/<slug>/.env (senha do banco sorteada). NÃO sobe nada.
scripts/novo-ambiente.sh campina-grande \
    --nome "Secretaria de Saúde de Campina Grande" \
    --dominio campina.caladrius.app \
    --porta 8114

# 2) Sobe o stack ISOLADO desta secretaria.
docker compose --env-file ambientes/campina-grande/.env \
    -f docker/docker-compose.tenant.yml up -d

# 3) Publica no Caddy (adicione ao Caddyfile e recarregue).
#    campina.caladrius.app {
#        reverse_proxy 127.0.0.1:8114
#    }
sudo caddy reload --config /etc/caddy/Caddyfile
```

> **Porta**: escolha uma **única** por ambiente (8114, 8115, 8116…). O script **avisa** se a porta
> já aparece em outro `ambientes/*/.env`. A porta é só no **loopback** (`127.0.0.1`): quem expõe
> publicamente é o Caddy.

## Primeiro acesso

No 1º boot, o `DataInitializer` cria o **admin-bootstrap deste ambiente** (telefone `83999999999`,
senha `admin123`). **Troque a senha no primeiro login.** A partir daí, novos gestores nascem por
**convite** (SPEC-01) — cada secretaria tem a sua própria cadeia de gestores.

## Atualizar a versão em todos os ambientes (rollout)

A imagem é **a mesma** para todos (a que o CI publica no GHCR). Atualizar = `pull` + `up -d` por
ambiente. Recomenda-se **canário**: atualize **uma** secretaria, valide, depois as demais.

```bash
# Canário: uma secretaria primeiro.
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml pull
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml up -d

# Validou? Propague para todas:
for env in ambientes/*/.env; do
  echo ">> atualizando $(dirname "$env")"
  docker compose --env-file "$env" -f docker/docker-compose.tenant.yml pull
  docker compose --env-file "$env" -f docker/docker-compose.tenant.yml up -d
done
```

Cada ambiente aplica o **Flyway** no **seu** banco no boot (migrations forward-only, Art. IV). Como
os bancos são independentes, uma migration nova roda isoladamente em cada secretaria.

## Backups (por secretaria, independentes)

```bash
# Dump do banco de UMA secretaria.
docker exec caladrius-campina-grande-db \
    pg_dump -U caladrius caladrius | gzip > backup-campina-$(date +%F).sql.gz

# Todas as secretarias (ex.: em cron diário).
for env in ambientes/*/.env; do
  slug=$(basename "$(dirname "$env")")
  docker exec "caladrius-${slug}-db" pg_dump -U caladrius caladrius \
      | gzip > "backup-${slug}-$(date +%F).sql.gz"
done
```

Restaurar: `gunzip -c backup-campina-AAAA-MM-DD.sql.gz | docker exec -i caladrius-campina-grande-db psql -U caladrius caladrius`.

## Encerrar / rescindir um cliente

```bash
# 1) Backup final (arquive fora do host).
docker exec caladrius-campina-grande-db pg_dump -U caladrius caladrius | gzip > final-campina.sql.gz
# 2) Derruba o stack E apaga o volume/dados DESTA secretaria (não afeta as outras — RN-MT-07).
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml down -v
# 3) Remove a config e o vhost do Caddy.
rm -rf ambientes/campina-grande        # contém o .env com segredos
#    (apague o bloco do Caddyfile e 'caddy reload')
```

## Operar / inspecionar

```bash
# Status de um ambiente:
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml ps
# Logs da app:
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml logs -f app
# Ambientes provisionados neste host:
ls ambientes/
```

## Segurança

- Cada `ambientes/<slug>/.env` tem **segredos** (senha do banco, tokens): fica com permissão `600`,
  é **git-ignored** e **nunca** é versionado (Art. XI / RN-MT-03).
- A porta da app é só no **loopback**; a porta do Postgres **não é publicada** (interna ao stack).
- Um incidente (queda, corrupção, upgrade ruim) fica **contido** em uma secretaria — as demais não
  são afetadas.

## Limites / quando reavaliar

A Opção A é ótima para **poucas dezenas** de secretarias. Se um dia surgir **painel único
multi-secretaria**, **auto-serviço de cadastro** de clientes, **muitos** tenants pequenos ou
**relatórios agregados**, reavalie a **Opção C (linha a linha)** — ver
[SPEC-15 §7](sdd/specs/SPEC-15-multi-ambiente-por-secretaria.md).
