package br.ufpb.dsc.caladrius.dto;

/**
 * Configurações de envio do canal WhatsApp (SPEC-10/11), geridas no painel do
 * gerente e persistidas como configuração dinâmica ({@code ConfiguracaoService}).
 *
 * <p>Já teve também uma <em>janela de atendimento</em> (início/fim). Ela foi
 * <strong>removida</strong> em 2026-07-29 (DT-16): o valor era salvo mas nunca
 * aplicado, e a decisão do dono do produto é que a janela só faz sentido quando
 * existir um serviço de suporte humano por trás — até lá, silenciar o bot em
 * horário comercial só atrapalharia quem solicita viagem de madrugada.
 *
 * @param nomeExibicao        nome da secretaria assinado nas mensagens (opcional)
 * @param mensagemConfirmacao modelo da confirmação de viagem, com {@code {data}},
 *                            {@code {hora}} e {@code {destino}}
 */
public record ConfiguracaoEnvioWhatsapp(String nomeExibicao, String mensagemConfirmacao) {
}
