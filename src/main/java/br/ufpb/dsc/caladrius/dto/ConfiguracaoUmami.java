package br.ufpb.dsc.caladrius.dto;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Configuração do <strong>analytics de uso (Umami)</strong> — SPEC-17.
 *
 * <p>Carrega o que o fragmento {@code fragments/analytics.html} precisa para montar a
 * tag do rastreador. Segue o padrão de ativação condicional do projeto (SPEC-08/10/14):
 * <strong>sem {@code UMAMI_URL} e {@code UMAMI_WEBSITE_ID}, {@link #isAtivo()} é
 * {@code false} e nenhum script é renderizado</strong> — em dev e nos testes a
 * aplicação sobe idêntica, sem enviar nada (RN-ANL-01).
 *
 * @param scriptUrl  URL completa do rastreador ({@code <base>/script.js}); vazia quando desligado
 * @param websiteId  identificador do site no Umami; vazio quando desligado
 * @param dominios   valor de {@code data-domains} — <strong>{@code null} quando não restrito</strong>,
 *                   para o Thymeleaf simplesmente omitir o atributo
 */
public record ConfiguracaoUmami(String scriptUrl, String websiteId, String dominios) {

    /** Detecta um esquema já presente na URL (`https://`, `http://`, …). */
    private static final Pattern ESQUEMA = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://");

    /** Instância desligada — o que vale em dev, nos testes e sem as variáveis. */
    public static ConfiguracaoUmami desligado() {
        return new ConfiguracaoUmami("", "", null);
    }

    /**
     * Monta a configuração a partir das variáveis de ambiente, normalizando a URL base.
     *
     * @param url       base do Umami (ex.: {@code https://umami.dsc.rodrigor.com})
     * @param websiteId {@code data-website-id} fornecido pelo painel
     * @param dominios  lista para {@code data-domains} (ex.: {@code eq14.dsc.rodrigor.com}); opcional
     */
    public static ConfiguracaoUmami de(String url, String websiteId, String dominios) {
        String base = normalizarUrl(url);
        String id = StringUtils.hasText(websiteId) ? websiteId.trim() : "";
        if (base.isEmpty() || id.isEmpty()) {
            return desligado();
        }
        return new ConfiguracaoUmami(
                base + "/script.js",
                id,
                StringUtils.hasText(dominios) ? dominios.trim() : null);
    }

    /** {@code true} quando há base e website id — só então o script é renderizado. */
    public boolean isAtivo() {
        return !scriptUrl.isEmpty() && !websiteId.isEmpty();
    }

    /**
     * Presume {@code https://} quando o esquema falta e remove a barra final.
     *
     * <p>Mesma armadilha já vivida na integração WhatsApp: os painéis mostram o endereço
     * sem o esquema, é isso que a pessoa cola no {@code .env}, e o resultado seria um
     * {@code src} <em>relativo</em> ({@code umami.exemplo.com/script.js}) — o navegador
     * pediria à própria aplicação, o script não carregaria e não haveria erro visível.
     */
    private static String normalizarUrl(String bruta) {
        String url = bruta == null ? "" : bruta.trim();
        if (url.isEmpty()) {
            return url;
        }
        if (!ESQUEMA.matcher(url).find()) {
            url = "https://" + url;
        }
        int inicioHost = url.indexOf("://") + 3;
        int fim = url.length();
        while (fim > inicioHost && url.charAt(fim - 1) == '/') {
            fim--;
        }
        return url.substring(0, fim);
    }
}
