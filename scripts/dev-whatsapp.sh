#!/usr/bin/env bash
# ===========================================================================
#  dev-whatsapp.sh — sobe o túnel (cloudflared) + a app já com a URL pública
#  correta, para testar a integração WhatsApp localmente (SPEC-10/11).
#
#  Resolve a dor recorrente: a URL do túnel "quick" muda a cada execução; se a
#  app subir com uma URL antiga, o webhook da Evolution aponta para um túnel
#  morto e o bot nunca recebe mensagem. Aqui a URL é sempre a do túnel atual.
#
#  Uso:
#    export EVOLUTION_URL="https://sua-evolution..."      # obrigatório
#    export EVOLUTION_API_KEY="..."                        # obrigatório (segredo)
#    ./scripts/dev-whatsapp.sh
#
#  Opcionais (têm padrão): EVOLUTION_INSTANCIA=caladrius,
#  WHATSAPP_WEBHOOK_TOKEN (gerado se ausente).
#
#  Requer: cloudflared em ~/.local/bin ou no PATH.
# ===========================================================================
set -euo pipefail

: "${EVOLUTION_URL:?defina EVOLUTION_URL antes de rodar}"
: "${EVOLUTION_API_KEY:?defina EVOLUTION_API_KEY antes de rodar}"
export EVOLUTION_INSTANCIA="${EVOLUTION_INSTANCIA:-caladrius}"
export WHATSAPP_WEBHOOK_TOKEN="${WHATSAPP_WEBHOOK_TOKEN:-$(openssl rand -hex 32)}"

CLOUDFLARED="$(command -v cloudflared || echo "$HOME/.local/bin/cloudflared")"
[ -x "$CLOUDFLARED" ] || { echo "cloudflared não encontrado (instale ou ajuste o PATH)"; exit 1; }

LOG="$(mktemp)"
echo "→ subindo o túnel cloudflared…"
pkill -f "cloudflared tunnel --url" 2>/dev/null || true
"$CLOUDFLARED" tunnel --url http://localhost:8080 >"$LOG" 2>&1 &
TUNNEL_PID=$!
trap 'echo; echo "→ encerrando o túnel (PID $TUNNEL_PID)"; kill "$TUNNEL_PID" 2>/dev/null || true' EXIT

# Espera a URL pública aparecer no log do cloudflared.
for _ in $(seq 1 30); do
    URL="$(grep -o 'https://[a-zA-Z0-9.-]*trycloudflare.com' "$LOG" | head -1 || true)"
    [ -n "$URL" ] && break
    sleep 1
done
[ -n "${URL:-}" ] || { echo "não consegui obter a URL do túnel; veja $LOG"; exit 1; }

export APP_URL_PUBLICA="$URL"
echo "→ túnel: $APP_URL_PUBLICA"
echo "→ subindo a app (mvn spring-boot:run)…"
echo "  No painel /whatsapp, ao conectar (ou já conectado) o webhook aponta para esta URL."
exec mvn spring-boot:run
