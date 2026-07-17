package br.ufpb.dsc.caladrius.whatsapp;

/**
 * Dados da conta WhatsApp conectada no provedor (SPEC-10).
 *
 * @param numero     número da conta (dígitos, como informado pelo provedor)
 * @param nomePerfil nome do perfil do WhatsApp
 */
public record ContaWhatsapp(String numero, String nomePerfil) {
}
