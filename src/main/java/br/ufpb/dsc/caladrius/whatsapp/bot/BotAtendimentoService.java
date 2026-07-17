package br.ufpb.dsc.caladrius.whatsapp.bot;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.ConversaBot;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.EtapaConversa;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.ConversaBotRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.service.ConviteService;
import br.ufpb.dsc.caladrius.service.OnboardingService;
import br.ufpb.dsc.caladrius.service.SolicitacaoViagemService;
import br.ufpb.dsc.caladrius.service.WhatsappService;
import br.ufpb.dsc.caladrius.util.Documentos;
import br.ufpb.dsc.caladrius.whatsapp.MensagemRecebida;
import br.ufpb.dsc.caladrius.whatsapp.ProcessadorMensagemRecebida;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Bot de atendimento do WhatsApp (SPEC-10/SPEC-11) — máquina de estados persistida
 * em {@code conversas_bot}. Permite ao passageiro <strong>cadastrar-se, solicitar
 * transporte sob demanda, consultar e cancelar</strong> viagens e obter
 * <strong>acesso à plataforma</strong>, tudo pela conversa.
 *
 * <p><strong>Desacoplamento (requisito do projeto):</strong> o bot consome
 * {@link MensagemRecebida} normalizada (via {@link ProcessadorMensagemRecebida})
 * e responde pela fachada {@link WhatsappService} — <em>nada aqui conhece a
 * Evolution API</em>. Toda a lógica de negócio vem de serviços de domínio
 * ({@link OnboardingService}, {@link SolicitacaoViagemService},
 * {@link ConviteService}); trocar o provedor de WhatsApp é escrever outro
 * adaptador da porta {@code ProvedorWhatsapp}, sem tocar neste arquivo.
 */
@Service
public class BotAtendimentoService implements ProcessadorMensagemRecebida {

    /** Inatividade que expira a conversa — a próxima mensagem recomeça (RN-WPP-07). */
    static final Duration EXPIRACAO = Duration.ofMinutes(30);

    private static final DateTimeFormatter DATA_COMPLETA = DateTimeFormatter.ofPattern("d/M/uuuu");
    private static final DateTimeFormatter DATA_SEM_ANO = DateTimeFormatter.ofPattern("d/M");

    private final ConversaBotRepository conversaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SolicitacaoViagemService solicitacaoService;
    private final OnboardingService onboardingService;
    private final ConviteService conviteService;
    private final WhatsappService whatsappService;
    private final String appUrlPublica;

    public BotAtendimentoService(ConversaBotRepository conversaRepository,
                                 UsuarioRepository usuarioRepository,
                                 SolicitacaoViagemService solicitacaoService,
                                 OnboardingService onboardingService,
                                 ConviteService conviteService,
                                 WhatsappService whatsappService,
                                 @Value("${APP_URL_PUBLICA:}") String appUrlPublica) {
        this.conversaRepository = conversaRepository;
        this.usuarioRepository = usuarioRepository;
        this.solicitacaoService = solicitacaoService;
        this.onboardingService = onboardingService;
        this.conviteService = conviteService;
        this.whatsappService = whatsappService;
        this.appUrlPublica = appUrlPublica;
    }

    @Override
    @Transactional
    public void processar(MensagemRecebida mensagem) {
        String telefone = mensagem.telefone();
        String texto = mensagem.texto() == null ? "" : mensagem.texto().trim();
        Usuario usuario = identificar(telefone).orElse(null);

        ConversaBot conversa = conversaRepository.findByTelefone(telefone).orElseGet(() -> {
            ConversaBot nova = new ConversaBot();
            nova.setTelefone(telefone);
            return nova;
        });

        // Conversa expirada por inatividade: recomeça, com aviso (RN-WPP-07).
        if (emAtendimento(conversa) && expirou(conversa)) {
            responder(telefone, MensagensBot.AVISO_EXPIRADA);
            conversa.limparContexto();
            conversa.setEtapa(EtapaConversa.INICIO);
        }

        if (usuario == null) {
            // Número desconhecido → cadastro guiado (SPEC-11, RN-ONB-01).
            atenderOnboarding(conversa, telefone, texto);
        } else {
            conversa.setUsuario(usuario);
            atender(conversa, usuario, texto);
        }
        conversaRepository.save(conversa);
    }

    // ============================================================ onboarding

    private void atenderOnboarding(ConversaBot conversa, String telefone, String texto) {
        switch (conversa.getEtapa()) {
            case ONBOARDING_NOME -> {
                if (!StringUtils.hasText(texto)) {
                    responder(telefone, "Por favor, informe o seu *nome completo*.");
                    return;
                }
                conversa.setCadNome(texto);
                responder(telefone, MensagensBot.PEDIR_ENDERECO);
                conversa.setEtapa(EtapaConversa.ONBOARDING_ENDERECO);
            }
            case ONBOARDING_ENDERECO -> {
                if (!StringUtils.hasText(texto)) {
                    responder(telefone, MensagensBot.PEDIR_ENDERECO);
                    return;
                }
                conversa.setCadEndereco(texto);
                responder(telefone, MensagensBot.PEDIR_CPF);
                conversa.setEtapa(EtapaConversa.ONBOARDING_CPF);
            }
            case ONBOARDING_CPF -> finalizarCadastro(conversa, telefone, texto);
            // INICIO/ENCERRADA/qualquer outra: dá as boas-vindas e pede o nome.
            default -> {
                responder(telefone, MensagensBot.BOAS_VINDAS_CADASTRO);
                conversa.setEtapa(EtapaConversa.ONBOARDING_NOME);
            }
        }
    }

    private void finalizarCadastro(ConversaBot conversa, String telefone, String cpf) {
        try {
            Usuario novo = onboardingService.cadastrarPassageiro(
                    telefone, conversa.getCadNome(), conversa.getCadEndereco(), cpf);
            conversa.setUsuario(novo);
            conversa.limparContexto();
            conversa.setEtapa(EtapaConversa.MENU);
            responder(telefone, MensagensBot.cadastroConcluido(primeiroNome(novo)));
        } catch (RegraNegocioException e) {
            // CPF inválido / já cadastrado etc. — repete a etapa do CPF.
            responder(telefone, "⚠️ " + e.getMessage() + ".");
            responder(telefone, MensagensBot.PEDIR_CPF);
        }
    }

    // ============================================================ atendimento (cadastrado)

    private void atender(ConversaBot conversa, Usuario usuario, String texto) {
        switch (conversa.getEtapa()) {
            case MENU -> menu(conversa, usuario, texto);
            case ESCOLHER_DESTINO -> escolherDestino(conversa, texto);
            case ESCOLHER_DATA -> escolherData(conversa, texto);
            case ESCOLHER_HORARIO -> escolherHorario(conversa, texto);
            case CONDICOES -> condicoes(conversa, texto);
            case CONFIRMAR_DEMANDA -> confirmarDemanda(conversa, usuario, texto);
            case CANCELAR -> cancelar(conversa, usuario, texto);
            // INICIO/ENCERRADA/HUMANO/etapas dormentes: saúda e abre o menu.
            default -> {
                responder(conversa.getTelefone(),
                        MensagensBot.saudacao(primeiroNome(usuario)) + "\n\n" + MensagensBot.menu());
                conversa.setEtapa(EtapaConversa.MENU);
            }
        }
    }

    private void menu(ConversaBot conversa, Usuario usuario, String texto) {
        switch (texto) {
            case "1" -> {
                List<Cidade> cidades = solicitacaoService.cidadesDestino();
                if (cidades.isEmpty()) {
                    responder(conversa.getTelefone(), MensagensBot.SEM_DESTINOS);
                    responder(conversa.getTelefone(), MensagensBot.menu());
                    return;
                }
                responder(conversa.getTelefone(), MensagensBot.escolherDestino(cidades));
                conversa.setEtapa(EtapaConversa.ESCOLHER_DESTINO);
            }
            case "2" -> {
                List<SolicitacaoViagem> solicitacoes = solicitacaoService.listarDoPassageiro(usuario.getId());
                if (solicitacoes.isEmpty()) {
                    responder(conversa.getTelefone(), MensagensBot.SEM_SOLICITACOES);
                    responder(conversa.getTelefone(), MensagensBot.menu());
                    return;
                }
                responder(conversa.getTelefone(), MensagensBot.minhasViagens(solicitacoes));
                List<SolicitacaoViagem> ativas = ativas(solicitacoes);
                if (ativas.isEmpty()) {
                    responder(conversa.getTelefone(), MensagensBot.menu());
                } else {
                    responder(conversa.getTelefone(), MensagensBot.escolherCancelamento(ativas));
                    conversa.setEtapa(EtapaConversa.CANCELAR);
                }
            }
            case "3" -> {
                List<LinhaProgramada> linhas = solicitacaoService.linhasDisponiveis();
                responder(conversa.getTelefone(), linhas.isEmpty()
                        ? MensagensBot.SEM_LINHAS : MensagensBot.linhasDisponiveis(linhas));
                responder(conversa.getTelefone(), MensagensBot.menu());
            }
            case "4" -> {
                String link = conviteService.gerarAcessoPlataforma(usuario.getId());
                responder(conversa.getTelefone(), MensagensBot.acessoPlataforma(linkCompleto(link)));
                responder(conversa.getTelefone(), MensagensBot.menu());
            }
            case "0" -> {
                responder(conversa.getTelefone(), MensagensBot.DESPEDIDA);
                conversa.limparContexto();
                conversa.setEtapa(EtapaConversa.ENCERRADA);
            }
            default -> responder(conversa.getTelefone(),
                    MensagensBot.OPCAO_INVALIDA + "\n\n" + MensagensBot.menu());
        }
    }

    // ------------------------------------------------------------ solicitar sob demanda

    private void escolherDestino(ConversaBot conversa, String texto) {
        if ("0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        List<Cidade> cidades = solicitacaoService.cidadesDestino();
        Integer escolha = numero(texto);
        if (escolha == null || escolha < 1 || escolha > cidades.size()) {
            responder(conversa.getTelefone(),
                    MensagensBot.OPCAO_INVALIDA + "\n\n" + MensagensBot.escolherDestino(cidades));
            return;
        }
        conversa.setCidadeDestino(cidades.get(escolha - 1));
        responder(conversa.getTelefone(), MensagensBot.PEDIR_DATA);
        conversa.setEtapa(EtapaConversa.ESCOLHER_DATA);
    }

    private void escolherData(ConversaBot conversa, String texto) {
        if ("0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        LocalDate data = parseData(texto);
        if (data == null) {
            responder(conversa.getTelefone(), MensagensBot.DATA_INVALIDA);
            return;
        }
        if (data.isBefore(LocalDate.now())) {
            responder(conversa.getTelefone(), MensagensBot.DATA_PASSADA);
            return;
        }
        conversa.setDataDesejada(data);
        responder(conversa.getTelefone(), MensagensBot.PEDIR_HORARIO);
        conversa.setEtapa(EtapaConversa.ESCOLHER_HORARIO);
    }

    private void escolherHorario(ConversaBot conversa, String texto) {
        if ("0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        LocalTime horario = parseHorario(texto);
        if (horario == null) {
            responder(conversa.getTelefone(), MensagensBot.HORARIO_INVALIDO);
            return;
        }
        conversa.setHorarioDesejado(horario);
        responder(conversa.getTelefone(), MensagensBot.PEDIR_CONDICOES);
        conversa.setEtapa(EtapaConversa.CONDICOES);
    }

    private void condicoes(ConversaBot conversa, String texto) {
        if ("0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        conversa.setCondicoes(semCondicoes(texto) ? null : texto);
        responder(conversa.getTelefone(), MensagensBot.confirmarDemanda(
                conversa.getCidadeDestino(), conversa.getDataDesejada(),
                conversa.getHorarioDesejado(), conversa.getCondicoes()));
        conversa.setEtapa(EtapaConversa.CONFIRMAR_DEMANDA);
    }

    private void confirmarDemanda(ConversaBot conversa, Usuario usuario, String texto) {
        if ("2".equals(texto) || "0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        if (!"1".equals(texto)) {
            responder(conversa.getTelefone(), MensagensBot.OPCAO_INVALIDA + "\n\n"
                    + MensagensBot.confirmarDemanda(conversa.getCidadeDestino(), conversa.getDataDesejada(),
                    conversa.getHorarioDesejado(), conversa.getCondicoes()));
            return;
        }
        try {
            solicitacaoService.solicitarSobDemanda(usuario.getId(), conversa.getCidadeDestino().getId(),
                    conversa.getDataDesejada(), conversa.getHorarioDesejado(), conversa.getCondicoes());
            responder(conversa.getTelefone(), MensagensBot.DEMANDA_REGISTRADA);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            responder(conversa.getTelefone(), MensagensBot.erroDeRegra(e.getMessage()));
        }
        voltarAoMenu(conversa);
    }

    // ------------------------------------------------------------ cancelar

    private void cancelar(ConversaBot conversa, Usuario usuario, String texto) {
        if ("0".equals(texto)) {
            voltarAoMenu(conversa);
            return;
        }
        List<SolicitacaoViagem> ativas = ativas(solicitacaoService.listarDoPassageiro(usuario.getId()));
        Integer escolha = numero(texto);
        if (escolha == null || escolha < 1 || escolha > ativas.size()) {
            responder(conversa.getTelefone(),
                    MensagensBot.OPCAO_INVALIDA + "\n\n" + MensagensBot.escolherCancelamento(ativas));
            return;
        }
        SolicitacaoViagem solicitacao = ativas.get(escolha - 1);
        try {
            // Isolamento (RN-WPP-06/RN-SOL-07): só as do próprio passageiro.
            solicitacaoService.cancelar(solicitacao.getId(), usuario.getId());
            responder(conversa.getTelefone(), MensagensBot.cancelamentoConfirmado(solicitacao));
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            responder(conversa.getTelefone(), MensagensBot.erroDeRegra(e.getMessage()));
        }
        voltarAoMenu(conversa);
    }

    // ============================================================ apoio

    /**
     * Resolve o usuário ativo pelo telefone normalizado. JIDs antigos podem vir
     * sem o 9º dígito — as variantes cobrem o caso (SPEC-10 §4.5).
     */
    private Optional<Usuario> identificar(String telefone) {
        for (String variante : Documentos.variantesTelefoneBr(telefone)) {
            Optional<Usuario> usuario = usuarioRepository.findByTelefoneAndRemovidoEmIsNull(variante)
                    .filter(Usuario::isAtivo);
            if (usuario.isPresent()) {
                return usuario;
            }
        }
        return Optional.empty();
    }

    /** Solicitações que ainda podem ser canceladas (pendentes ou alocadas). */
    private List<SolicitacaoViagem> ativas(List<SolicitacaoViagem> solicitacoes) {
        return solicitacoes.stream()
                .filter(s -> s.getStatus() == StatusSolicitacao.PENDENTE
                        || s.getStatus() == StatusSolicitacao.ALOCADA)
                .toList();
    }

    private void voltarAoMenu(ConversaBot conversa) {
        conversa.limparContexto();
        responder(conversa.getTelefone(), MensagensBot.menu());
        conversa.setEtapa(EtapaConversa.MENU);
    }

    private void responder(String telefone, String texto) {
        whatsappService.enviarTexto(telefone, texto);
    }

    private boolean emAtendimento(ConversaBot conversa) {
        return conversa.getEtapa() != EtapaConversa.INICIO
                && conversa.getEtapa() != EtapaConversa.ENCERRADA;
    }

    private boolean expirou(ConversaBot conversa) {
        Instant referencia = conversa.getAtualizadoEm();
        return referencia == null || referencia.plus(EXPIRACAO).isBefore(Instant.now());
    }

    private String primeiroNome(Usuario usuario) {
        String nome = usuario.getNomeCompleto();
        int espaco = nome.indexOf(' ');
        return espaco > 0 ? nome.substring(0, espaco) : nome;
    }

    /** Prefixa o link de ativação com a URL pública (quando configurada). */
    private String linkCompleto(String linkRelativo) {
        return StringUtils.hasText(appUrlPublica) ? appUrlPublica + linkRelativo : linkRelativo;
    }

    private boolean semCondicoes(String texto) {
        String t = texto.trim().toLowerCase();
        return t.equals("não") || t.equals("nao") || t.equals("-") || t.equals("nenhuma") || t.equals("nenhum");
    }

    private Integer numero(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Aceita {@code d/M/aaaa} e {@code d/M} (assume o ano corrente). */
    private LocalDate parseData(String texto) {
        try {
            return LocalDate.parse(texto, DATA_COMPLETA);
        } catch (DateTimeParseException e) {
            try {
                return MonthDay.parse(texto, DATA_SEM_ANO).atYear(LocalDate.now().getYear());
            } catch (DateTimeParseException ignorada) {
                return null;
            }
        }
    }

    /** Aceita {@code HH:mm}, {@code HHhmm}, {@code HHh} e {@code HH}. */
    private LocalTime parseHorario(String texto) {
        String t = texto.trim().toLowerCase().replace('h', ':').replace(" ", "");
        if (t.endsWith(":")) {
            t = t + "00";
        }
        try {
            if (t.contains(":")) {
                String[] partes = t.split(":");
                int hora = Integer.parseInt(partes[0]);
                int min = partes.length > 1 && !partes[1].isEmpty() ? Integer.parseInt(partes[1]) : 0;
                return LocalTime.of(hora, min);
            }
            return LocalTime.of(Integer.parseInt(t), 0);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}
