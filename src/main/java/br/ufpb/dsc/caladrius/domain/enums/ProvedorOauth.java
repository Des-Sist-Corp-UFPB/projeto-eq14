package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Provedor de identidade externa (OAuth2/OIDC) suportado pelo login social.
 *
 * <p>Mapeado como {@code VARCHAR} em {@code identidades_oauth.provedor} (com
 * {@code CHECK}). Por ora só o Google; a modelagem já comporta novos provedores
 * (Apple, Microsoft) sem alteração de esquema além do {@code CHECK} — ver SPEC-08.
 */
public enum ProvedorOauth {

    GOOGLE("Google");

    private final String rotulo;

    ProvedorOauth(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
