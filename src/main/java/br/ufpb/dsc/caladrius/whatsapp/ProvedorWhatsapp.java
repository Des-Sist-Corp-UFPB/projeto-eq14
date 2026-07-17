package br.ufpb.dsc.caladrius.whatsapp;

import java.util.Optional;

/**
 * Porta de acesso ao WhatsApp (SPEC-10/ADR-14) — cobre <strong>conexão</strong>
 * e <strong>envio</strong>, tudo o que o restante do sistema precisa do canal.
 *
 * <p>A Evolution API ({@link EvolutionApiProvedor}) é apenas o primeiro
 * adaptador: trocar de provedor (ex.: API oficial da Meta) significa escrever
 * outro adaptador desta porta, sem tocar em bot, notificações ou telas. O bean
 * é <strong>condicional</strong> às variáveis de ambiente ({@code EVOLUTION_URL}
 * e {@code EVOLUTION_API_KEY} — ver {@code WhatsappConfig}); sem elas não há
 * provedor e o canal permanece como stub (RN-WPP-02).
 */
public interface ProvedorWhatsapp {

    /** Estado atual da conexão da conta no provedor. */
    StatusConexaoWhatsapp statusConexao();

    /**
     * Cria/conecta a instância no provedor (registrando o webhook de eventos)
     * e devolve a situação — com o QR code em base64 quando o pareamento está
     * pendente.
     */
    ConexaoWhatsapp iniciarConexao();

    /** Encerra a sessão (logout) da conta conectada. */
    void desconectar();

    /**
     * (Re)registra o webhook desta instância apontando para a URL pública atual.
     * Idempotente — útil ao reiniciar a app (ex.: mudou a URL pública/túnel) sem
     * refazer o pareamento. No-op se não houver URL pública configurada.
     */
    void registrarWebhook();

    /** Número e nome do perfil conectado; vazio se não houver conta pareada. */
    Optional<ContaWhatsapp> contaConectada();

    /**
     * Envia uma mensagem de texto ao telefone (dígitos, sem DDI — o adaptador
     * acrescenta o DDI 55 se o provedor exigir).
     *
     * @throws WhatsappException em falha de comunicação com o provedor
     */
    void enviarTexto(String telefone, String texto);
}
