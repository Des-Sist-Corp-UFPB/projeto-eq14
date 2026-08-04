package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.MensagemWhatsapp;
import br.ufpb.dsc.caladrius.domain.enums.DirecaoMensagem;
import br.ufpb.dsc.caladrius.dto.ConfiguracaoEnvioWhatsapp;
import br.ufpb.dsc.caladrius.repository.MensagemWhatsappRepository;
import br.ufpb.dsc.caladrius.whatsapp.ConexaoWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.ContaWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.MensagemRecebida;
import br.ufpb.dsc.caladrius.whatsapp.ProvedorWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.StatusConexaoWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.WhatsappException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Fachada do canal WhatsApp (SPEC-10): concentra o acesso à porta
 * {@link ProvedorWhatsapp}, o log de mensagens ({@code mensagens_whatsapp} —
 * RN-WPP-10) e o estado de conexão que alimenta o painel do gerente.
 *
 * <p>O provedor é <strong>opcional</strong> ({@link ObjectProvider}): sem as
 * variáveis de ambiente da Evolution, {@link #configurada()} é {@code false},
 * o envio vira no-op e o painel informa "não configurada" (RN-WPP-02).
 *
 * <p>O estado da conexão reportado pelo webhook ({@code CONNECTION_UPDATE} /
 * {@code QRCODE_UPDATED}) fica em <strong>cache</strong> em memória — o
 * polling HTMX do painel lê daqui, sem repetir consultas à Evolution a cada
 * 3 segundos.
 */
@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    /** Quantas mensagens o painel exibe. */
    private static final int LIMITE_PAINEL = 30;

    // Chaves das configurações de envio (persistidas via ConfiguracaoService).
    static final String CFG_NOME = "whatsapp.nome_exibicao";
    static final String CFG_MENSAGEM = "whatsapp.msg_confirmacao";

    private static final String MENSAGEM_PADRAO =
            "Olá! Sua viagem foi confirmada para {data} às {hora}, destino {destino}.";

    private final ObjectProvider<ProvedorWhatsapp> provedor;
    private final MensagemWhatsappRepository mensagemRepository;
    private final ConfiguracaoService configuracaoService;

    /** Último estado reportado pelo webhook; nulo até o primeiro evento. */
    private volatile ConexaoWhatsapp estadoReportado;

    public WhatsappService(ObjectProvider<ProvedorWhatsapp> provedor,
                           MensagemWhatsappRepository mensagemRepository,
                           ConfiguracaoService configuracaoService) {
        this.provedor = provedor;
        this.mensagemRepository = mensagemRepository;
        this.configuracaoService = configuracaoService;
    }

    /** {@code true} quando a integração (Evolution) está configurada no ambiente. */
    public boolean configurada() {
        return provedor.getIfAvailable() != null;
    }

    /**
     * Ao subir a app: se a conta já estiver conectada, re-registra o webhook para
     * a {@code APP_URL_PUBLICA} atual. Isso conserta o caso comum em dev — a URL
     * do túnel mudou entre reinícios — sem exigir novo pareamento; em produção é
     * um no-op inofensivo (re-registra a mesma URL estável). Best-effort: falha
     * na Evolution não impede a app de subir (RN-WPP-01).
     *
     * <p>O {@code catch} é de {@link RuntimeException} de propósito, não de
     * {@link WhatsappException}: este método roda no {@code ApplicationReadyEvent}, e
     * uma exceção aqui <strong>derruba a aplicação inteira</strong>. Foi o que aconteceu
     * em 2026-07-29 com uma {@code EVOLUTION_URL} sem esquema — a causa foi corrigida
     * (normalização + a porta embrulhando tudo em {@code WhatsappException}), mas a rede
     * de proteção fica: nenhum defeito da integração pode impedir o app de subir.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reRegistrarWebhookSeConectado() {
        ProvedorWhatsapp whatsapp = provedor.getIfAvailable();
        if (whatsapp == null) {
            return;
        }
        try {
            if (whatsapp.statusConexao() == StatusConexaoWhatsapp.CONECTADO) {
                whatsapp.registrarWebhook();
                log.info("WhatsApp conectado — webhook re-registrado para a URL pública atual.");
            }
        } catch (RuntimeException e) {
            log.warn("Não foi possível re-registrar o webhook do WhatsApp na inicialização "
                    + "(a aplicação segue normalmente): {}", e.toString());
        }
    }

    // ------------------------------------------------------------ envio

    /**
     * Envia um texto e registra no log. Falha do provedor <strong>não propaga</strong>
     * (RN-WPP-01): registra em log e devolve {@code false} — o canal in-app é a
     * garantia de entrega.
     *
     * @return {@code true} se o envio foi aceito pelo provedor
     */
    @Transactional
    public boolean enviarTexto(String telefone, String texto) {
        ProvedorWhatsapp whatsapp = provedor.getIfAvailable();
        if (whatsapp == null) {
            // O CORPO da mensagem NÃO vai para o log de nível INFO: ele carrega
            // segredos de uso único (link `/ativar?token=…` do convite/"Acesso à
            // plataforma" e códigos OTP — SPEC-11/SPEC-12) e dado pessoal sensível
            // (condições de saúde da solicitação). Em produção o log é exportado ao
            // Loki central da disciplina (SPEC-14), fora do nosso controle: publicar
            // o token ali anularia o hash com que ele é guardado no banco.
            // O texto completo continua disponível em DEBUG, que só se liga em dev.
            // O destinatário também é achatado: ele chega do webhook (JID do provedor)
            // e um "\n" ali forjaria uma linha inteira de log.
            String destino = telefone == null ? "" : telefone.replaceAll("[\\r\\n]+", " ");
            log.info("[WHATSAPP/stub — integração não configurada] para {}: {} caracteres não enviados",
                    destino, texto == null ? 0 : texto.length());
            log.debug("[WHATSAPP/stub] conteúdo para {}: {}", destino, umaLinha(texto));
            return false;
        }
        try {
            whatsapp.enviarTexto(telefone, texto);
        } catch (WhatsappException e) {
            log.warn("Falha ao enviar WhatsApp para {} — o fluxo segue (RN-WPP-01): {}",
                    telefone, e.getMessage());
            return false;
        }
        mensagemRepository.save(new MensagemWhatsapp(DirecaoMensagem.ENVIADA, telefone, texto, null));
        return true;
    }

    // ------------------------------------------------------------ recebimento (webhook)

    /**
     * Registra uma mensagem recebida no log, garantindo a idempotência do
     * webhook (RN-WPP-03): evento repetido (mesmo id no provedor) devolve
     * {@code false} e não deve ser reprocessado.
     *
     * @return {@code true} se a mensagem é nova (deve ir ao bot)
     */
    @Transactional
    public boolean registrarRecebida(MensagemRecebida mensagem) {
        if (mensagem.idProvedor() != null && mensagemRepository
                .existsByIdProvedorAndDirecao(mensagem.idProvedor(), DirecaoMensagem.RECEBIDA)) {
            return false;
        }
        mensagemRepository.save(new MensagemWhatsapp(
                DirecaoMensagem.RECEBIDA, mensagem.telefone(), mensagem.texto(), mensagem.idProvedor()));
        return true;
    }

    /** Atualiza o cache de estado com o que o webhook reportou (§4.3 da SPEC-10). */
    public void registrarEstadoConexao(StatusConexaoWhatsapp status, String qrCodeBase64) {
        this.estadoReportado = new ConexaoWhatsapp(status, qrCodeBase64);
    }

    // ------------------------------------------------------------ painel

    /**
     * Estado da conexão para o painel: usa o cache alimentado pelo webhook e,
     * na falta dele (app recém-subida), consulta o provedor. Falha na consulta
     * aparece como DESCONECTADO — o painel nunca quebra por causa da Evolution.
     */
    public ConexaoWhatsapp estadoConexao() {
        ProvedorWhatsapp whatsapp = provedor.getIfAvailable();
        // Sem integração configurada, a conexão nunca está ativa — ignora qualquer
        // estado reportado antes (RN-WPP-02).
        if (whatsapp == null) {
            return ConexaoWhatsapp.desconectado();
        }
        // Fonte da verdade é o estado REAL na Evolution (consultado ao vivo): assim
        // o painel se autocorrige — quando a conta parear, ele passa a CONECTADO
        // mesmo que o webhook (CONNECTION_UPDATE) não tenha chegado. O cache do
        // webhook serve só para reaproveitar a imagem do QR enquanto pareia.
        try {
            return switch (whatsapp.statusConexao()) {
                case CONECTADO -> ConexaoWhatsapp.conectado();
                case AGUARDANDO_QR -> ConexaoWhatsapp.aguardandoQr(qrCacheado());
                case DESCONECTADO -> ConexaoWhatsapp.desconectado();
            };
        } catch (WhatsappException e) {
            log.warn("Falha ao consultar o estado da conexão WhatsApp: {}", e.getMessage());
            // Não conseguiu falar com a Evolution: cai no último estado reportado.
            return estadoReportado != null ? estadoReportado : ConexaoWhatsapp.desconectado();
        }
    }

    /** QR mais recente em cache (do {@code conectar()} ou do evento QRCODE_UPDATED). */
    private String qrCacheado() {
        ConexaoWhatsapp reportado = estadoReportado;
        return reportado != null && reportado.status() == StatusConexaoWhatsapp.AGUARDANDO_QR
                ? reportado.qrCodeBase64() : null;
    }

    /** Inicia a conexão (cria instância/webhook e obtém o QR) e atualiza o cache. */
    public ConexaoWhatsapp conectar() {
        ProvedorWhatsapp whatsapp = provedor.getObject();
        ConexaoWhatsapp conexao = whatsapp.iniciarConexao();
        this.estadoReportado = conexao;
        return conexao;
    }

    /** Encerra a sessão e atualiza o cache. */
    public void desconectar() {
        provedor.getObject().desconectar();
        this.estadoReportado = ConexaoWhatsapp.desconectado();
    }

    /** Conta conectada (número/perfil); vazio sem provedor, sem pareamento ou em falha. */
    public Optional<ContaWhatsapp> contaConectada() {
        ProvedorWhatsapp whatsapp = provedor.getIfAvailable();
        if (whatsapp == null) {
            return Optional.empty();
        }
        try {
            return whatsapp.contaConectada();
        } catch (WhatsappException e) {
            log.warn("Falha ao consultar a conta WhatsApp conectada: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Últimas mensagens (enviadas e recebidas) para o painel (RN-WPP-10). */
    @Transactional(readOnly = true)
    public List<MensagemWhatsapp> ultimasMensagens() {
        return mensagemRepository.findByOrderByCriadoEmDesc(PageRequest.of(0, LIMITE_PAINEL));
    }

    // ------------------------------------------------------------ configurações de envio

    /** Configurações de envio atuais (com os valores padrão quando não definidas). */
    public ConfiguracaoEnvioWhatsapp configuracaoEnvio() {
        return new ConfiguracaoEnvioWhatsapp(
                configuracaoService.get(CFG_NOME).orElse(""),
                configuracaoService.get(CFG_MENSAGEM).filter(StringUtils::hasText).orElse(MENSAGEM_PADRAO));
    }

    /** Persiste as configurações de envio (upsert de cada chave). */
    @Transactional
    public void salvarConfiguracaoEnvio(ConfiguracaoEnvioWhatsapp cfg) {
        configuracaoService.salvar(CFG_NOME, cfg.nomeExibicao() == null ? "" : cfg.nomeExibicao().trim());
        configuracaoService.salvar(CFG_MENSAGEM,
                StringUtils.hasText(cfg.mensagemConfirmacao()) ? cfg.mensagemConfirmacao().trim() : MENSAGEM_PADRAO);
    }

    /**
     * Renderiza a <strong>mensagem de confirmação</strong> configurada,
     * substituindo {@code {data}}, {@code {hora}} e {@code {destino}} e assinando
     * com o nome de exibição (se houver). Usada na notificação de aprovação de
     * viagem (SPEC-11).
     */
    public String mensagemConfirmacao(String destino, String data, String hora) {
        ConfiguracaoEnvioWhatsapp cfg = configuracaoEnvio();
        String texto = cfg.mensagemConfirmacao()
                .replace("{data}", data == null ? "" : data)
                .replace("{hora}", hora == null ? "" : hora)
                .replace("{destino}", destino == null ? "" : destino);
        if (StringUtils.hasText(cfg.nomeExibicao())) {
            texto = texto + "\n\n— " + cfg.nomeExibicao().trim();
        }
        return texto;
    }

    // ------------------------------------------------------------ apoio

    /**
     * Achata quebras de linha antes de mandar um texto de origem externa ao log.
     * Sem isso, uma mensagem recebida pelo webhook poderia injetar linhas inteiras
     * no log e forjar eventos (CWE-117, <em>log injection</em> — apontado pelo
     * FindSecBugs como {@code CRLF_INJECTION_LOGS}).
     */
    static String umaLinha(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[\\r\\n]+", " ⏎ ");
    }
}
