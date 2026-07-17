package br.ufpb.dsc.caladrius.dto;

/**
 * Configurações de envio do canal WhatsApp (SPEC-10/11), geridas no painel do
 * gerente e persistidas como configuração dinâmica ({@code ConfiguracaoService}).
 *
 * @param nomeExibicao        nome da secretaria assinado nas mensagens (opcional)
 * @param mensagemConfirmacao modelo da confirmação de viagem, com {@code {data}},
 *                            {@code {hora}} e {@code {destino}}
 * @param atendimentoInicio   início do horário de atendimento (HH:mm)
 * @param atendimentoFim      fim do horário de atendimento (HH:mm)
 */
public record ConfiguracaoEnvioWhatsapp(String nomeExibicao, String mensagemConfirmacao,
                                        String atendimentoInicio, String atendimentoFim) {
}
