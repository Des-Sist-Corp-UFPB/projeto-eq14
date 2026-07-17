package br.ufpb.dsc.caladrius.whatsapp;

import java.time.Instant;

/**
 * Mensagem recebida pelo WhatsApp, já <strong>normalizada</strong> (SPEC-10):
 * o bot e o restante do sistema só conhecem este record — nunca o payload do
 * provedor (Evolution).
 *
 * @param idProvedor  id da mensagem no provedor (base da idempotência — RN-WPP-03)
 * @param telefone    telefone do remetente normalizado (dígitos, sem DDI 55)
 * @param nomeContato nome do contato como aparece no WhatsApp (pode ser nulo)
 * @param texto       texto da mensagem
 * @param recebidaEm  instante do recebimento
 */
public record MensagemRecebida(String idProvedor, String telefone, String nomeContato,
                               String texto, Instant recebidaEm) {
}
