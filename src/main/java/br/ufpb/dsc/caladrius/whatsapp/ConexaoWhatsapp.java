package br.ufpb.dsc.caladrius.whatsapp;

/**
 * Situação da conexão devolvida pela porta {@link ProvedorWhatsapp} (SPEC-10):
 * o estado e, quando o pareamento está em andamento, o QR code a exibir.
 *
 * @param status       estado da conexão
 * @param qrCodeBase64 imagem do QR em base64 (data URI ou base64 puro); nula fora do pareamento
 */
public record ConexaoWhatsapp(StatusConexaoWhatsapp status, String qrCodeBase64) {

    public static ConexaoWhatsapp conectado() {
        return new ConexaoWhatsapp(StatusConexaoWhatsapp.CONECTADO, null);
    }

    public static ConexaoWhatsapp desconectado() {
        return new ConexaoWhatsapp(StatusConexaoWhatsapp.DESCONECTADO, null);
    }

    public static ConexaoWhatsapp aguardandoQr(String qrCodeBase64) {
        return new ConexaoWhatsapp(StatusConexaoWhatsapp.AGUARDANDO_QR, qrCodeBase64);
    }
}
