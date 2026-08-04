#!/usr/bin/env bash
# =============================================================================
# novo-ambiente.sh — provisiona um ambiente ISOLADO por secretaria (SPEC-PLT-02, Opção A)
# =============================================================================
# Gera ambientes/<slug>/.env a partir de docker/.env.tenant.example, com a senha do
# banco SORTEADA (RN-MT-03), e imprime os próximos passos (Caddy + subir o stack).
# NÃO sobe nada nem toca em ambientes já existentes (recusa sobrescrever).
#
# Uso:
#   scripts/novo-ambiente.sh <slug> [--nome "Nome de exibição"] \
#       [--dominio sub.dominio] [--porta 8114] [--imagem ghcr.io/...:tag]
#
# Ex.:
#   scripts/novo-ambiente.sh campina-grande \
#       --nome "Secretaria de Saúde de Campina Grande" \
#       --dominio campina.caladrius.app --porta 8114
#
# Runbook completo: docs/multi-ambiente.md · decisão: docs/sdd/specs/SPEC-PLT-02-*.md
# =============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELO="$RAIZ/docker/.env.tenant.example"
COMPOSE="docker/docker-compose.tenant.yml"
IMAGEM_PADRAO="ghcr.io/des-sist-corp-ufpb/projeto-eq14:latest"

erro() { printf '\033[31merro:\033[0m %s\n' "$*" >&2; exit 1; }
aviso() { printf '\033[33maviso:\033[0m %s\n' "$*" >&2; }

[[ -f "$MODELO" ]] || erro "modelo não encontrado: $MODELO"

SLUG="${1:-}"
[[ -n "$SLUG" ]] || erro "informe o slug. Uso: scripts/novo-ambiente.sh <slug> [--nome ...] [--dominio ...] [--porta ...]"
[[ "$SLUG" != --* ]] || erro "o 1º argumento deve ser o slug (ex.: campina-grande), não uma opção."
shift
[[ "$SLUG" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ ]] || erro "slug inválido '$SLUG' — use kebab-case (a-z, 0-9, hífen)."

NOME=""; DOMINIO=""; PORTA=""; IMAGEM=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --nome)    NOME="${2:-}";    shift 2 ;;
    --dominio) DOMINIO="${2:-}"; shift 2 ;;
    --porta)   PORTA="${2:-}";   shift 2 ;;
    --imagem)  IMAGEM="${2:-}";  shift 2 ;;
    *) erro "opção desconhecida: $1" ;;
  esac
done

NOME="${NOME:-Secretaria $SLUG}"
DOMINIO="${DOMINIO:-$SLUG.caladrius.app}"
IMAGEM="${IMAGEM:-$IMAGEM_PADRAO}"
[[ -n "$PORTA" ]] || erro "informe --porta (única por ambiente; ex.: 8114). Veja as já usadas em ambientes/*/.env."
[[ "$PORTA" =~ ^[0-9]+$ ]] || erro "porta inválida: '$PORTA' (só dígitos)."

DIR="$RAIZ/ambientes/$SLUG"
DEST="$DIR/.env"
[[ -e "$DEST" ]] && erro "ambiente já existe: $DEST — remova-o à mão para recriar."

# Senha do banco sorteada (hex: seguro para URL/Compose, sem caracteres especiais).
if command -v openssl >/dev/null 2>&1; then
  SENHA="$(openssl rand -hex 24)"
else
  SENHA="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
fi

# Alerta (não bloqueia) se a porta já aparece em outro ambiente.
if grep -rEsq "^APP_PORT=${PORTA}$" "$RAIZ/ambientes"/*/.env 2>/dev/null; then
  aviso "a porta $PORTA já aparece em outro ambiente — confira antes de subir."
fi

mkdir -p "$DIR"

# Gera o .env a partir do modelo, trocando só as chaves conhecidas (linha a linha,
# para não depender de sed com barras/caracteres especiais nos valores).
while IFS= read -r linha || [[ -n "$linha" ]]; do
  case "$linha" in
    TENANT=*)                   echo "TENANT=$SLUG" ;;
    TENANT_NOME=*)              echo "TENANT_NOME=$NOME" ;;
    APP_IMAGE=*)                echo "APP_IMAGE=$IMAGEM" ;;
    APP_PORT=*)                 echo "APP_PORT=$PORTA" ;;
    POSTGRES_PASSWORD=*)        echo "POSTGRES_PASSWORD=$SENHA" ;;
    DOMINIO=*)                  echo "DOMINIO=$DOMINIO" ;;
    APP_URL_PUBLICA=*)          echo "APP_URL_PUBLICA=https://$DOMINIO" ;;
    OTEL_SERVICE_NAME=*)        echo "OTEL_SERVICE_NAME=caladrius-$SLUG" ;;
    OTEL_RESOURCE_ATTRIBUTES=*) echo "OTEL_RESOURCE_ATTRIBUTES=deployment.environment=$SLUG" ;;
    *)                          echo "$linha" ;;
  esac
done < "$MODELO" > "$DEST"
chmod 600 "$DEST"

cat <<FIM

✔ Ambiente '$SLUG' criado.
    arquivo : ambientes/$SLUG/.env   (git-ignored — contém a senha do banco)
    domínio : https://$DOMINIO   →   127.0.0.1:$PORTA
    banco   : dedicado (isolado dos demais ambientes)

Próximos passos, no servidor:

  1) Subir o stack isolado desta secretaria:
       docker compose --env-file ambientes/$SLUG/.env -f $COMPOSE up -d

  2) Proxy no Caddy (adicione ao Caddyfile e recarregue — 'caddy reload'):
       $DOMINIO {
           reverse_proxy 127.0.0.1:$PORTA
       }

  3) DNS: aponte $DOMINIO para o IP do servidor.

  4) 1º acesso: o DataInitializer cria o admin-bootstrap DESTE ambiente
     (telefone 83999999999 / senha admin123). TROQUE a senha no 1º login.

  Backup: pg_dump do banco 'caladrius' DESTE ambiente é independente dos demais —
  ver 'Backups' em docs/multi-ambiente.md.
FIM
