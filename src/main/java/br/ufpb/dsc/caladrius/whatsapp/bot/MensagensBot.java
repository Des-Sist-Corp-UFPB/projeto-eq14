package br.ufpb.dsc.caladrius.whatsapp.bot;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Textos do bot de atendimento WhatsApp (SPEC-10/SPEC-11), centralizados: ajustar
 * o roteiro do atendimento não toca a máquina de estados nem a integração.
 *
 * <p>Respostas curtas e numeradas ("responda com o número da opção"), com a
 * formatação de negrito do WhatsApp ({@code *texto*}).
 */
public final class MensagensBot {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private MensagensBot() {
        // utilitário estático — não instanciar
    }

    // ------------------------------------------------------------ gerais

    public static final String AVISO_EXPIRADA = "A conversa anterior expirou por inatividade. Vamos recomeçar!";
    public static final String OPCAO_INVALIDA = "Não entendi. 🤔 Responda com o *número* de uma das opções.";
    public static final String DESPEDIDA = "Atendimento encerrado. Quando precisar, é só mandar uma mensagem. Até logo! 👋";
    public static final String SEM_DESTINOS = "No momento não há destinos cadastrados. Tente novamente mais tarde ou fale com a secretaria.";
    public static final String SEM_SOLICITACOES = "Você ainda não tem solicitações de transporte.";
    public static final String SEM_SOLICITACOES_ATIVAS = "Você não tem solicitações ativas para cancelar.";
    public static final String SEM_LINHAS =
            "No momento não há linhas de transporte programadas. Você ainda pode pedir uma viagem sob demanda (opção *1*).";

    public static String menu() {
        return """
                O que você precisa?
                *1* - Solicitar viagem
                *2* - Minhas viagens
                *3* - Linhas disponíveis
                *4* - Acesso à plataforma
                *0* - Encerrar atendimento
                Responda com o número da opção.""";
    }

    public static String saudacao(String primeiroNome) {
        return "Olá, " + primeiroNome + "! Sou o assistente virtual do *CALADRIUS* 🚐 — transporte municipal de saúde.";
    }

    // ------------------------------------------------------------ linhas disponíveis (SPEC-11)

    /**
     * Panorama informativo das linhas rotineiras ativas (rotas que a secretaria já
     * opera). Só apresenta — o pedido em si continua sendo sob demanda (opção 1).
     */
    public static String linhasDisponiveis(List<LinhaProgramada> linhas) {
        StringBuilder texto = new StringBuilder("🚐 *Linhas de transporte disponíveis*\n"
                + "Rotas rotineiras que a secretaria já opera:\n");
        for (int i = 0; i < linhas.size(); i++) {
            LinhaProgramada l = linhas.get(i);
            String origem = l.getCidadeOrigem() != null ? l.getCidadeOrigem().getNome() : "Sede";
            texto.append('\n').append('*').append(i + 1).append(".* ").append(origem)
                    .append(" → *").append(l.getCidadeDestino().getNome()).append("*\n")
                    .append("🗓️ ").append(diasAbreviados(l.getDias()))
                    .append(" · 🕐 ").append(HORA.format(l.getHorarioSaida())).append('\n');
        }
        texto.append("\nPara pedir uma viagem, responda *1* no menu.");
        return texto.toString();
    }

    // ------------------------------------------------------------ onboarding (SPEC-11)

    public static final String BOAS_VINDAS_CADASTRO = """
            Olá! Sou o assistente virtual do *CALADRIUS* 🚐 — transporte municipal de saúde.
            Ainda não encontrei o seu cadastro. Vamos fazê-lo rapidinho para você solicitar viagens.

            Qual é o seu *nome completo*?""";

    public static final String PEDIR_ENDERECO = """
            Prazer! 👋 Agora me diga o seu *endereço* (onde o motorista vai te buscar):
            rua, número e bairro — e um ponto de referência, se ajudar.""";

    public static final String PEDIR_CPF = "Para finalizar o cadastro, informe o seu *CPF* (só os números).";

    public static final String CPF_INVALIDO = "Esse CPF não parece válido. 🤔 Confira e envie novamente (só os números).";

    public static String cadastroConcluido(String primeiroNome) {
        return "Cadastro concluído, " + primeiroNome + "! ✅\n\n" + menu();
    }

    // ------------------------------------------------------------ solicitar (sob demanda)

    public static String escolherDestino(List<Cidade> cidades) {
        StringBuilder texto = new StringBuilder("Para qual *cidade* você precisa ir?\n");
        for (int i = 0; i < cidades.size(); i++) {
            texto.append('\n').append('*').append(i + 1).append("* - ").append(cidades.get(i).getNome());
        }
        texto.append("\n\nResponda com o número, ou *0* para voltar ao menu.");
        return texto.toString();
    }

    public static final String PEDIR_DATA = """
            Para qual *data* você precisa do transporte?
            Responda no formato dia/mês/ano (ex.: 25/07/2026), ou *0* para voltar ao menu.""";

    public static final String DATA_INVALIDA =
            "Não consegui entender a data. 📅 Use o formato dia/mês/ano (ex.: 25/07/2026), ou *0* para voltar ao menu.";

    public static final String DATA_PASSADA =
            "⚠️ Escolha uma data igual ou posterior a hoje, ou responda *0* para voltar ao menu.";

    public static final String PEDIR_HORARIO = """
            Que *horário* você precisa chegar/consultar? (ex.: 14:00)
            Ou responda *0* para voltar ao menu.""";

    public static final String HORARIO_INVALIDO =
            "Não entendi o horário. 🕐 Use o formato horas:minutos (ex.: 14:00), ou *0* para voltar ao menu.";

    public static final String PEDIR_CONDICOES = """
            Você tem alguma *condição de saúde* que a secretaria deva considerar
            (ex.: cadeirante, precisa de acompanhante, dificuldade de locomoção)?
            Descreva em poucas palavras, ou responda *não*.""";

    public static String confirmarDemanda(Cidade destino, LocalDate data, LocalTime horario, String condicoes) {
        StringBuilder texto = new StringBuilder("Confira a sua solicitação:\n")
                .append("Destino: *").append(destino.getNome()).append("*\n")
                .append("Data: *").append(DATA.format(data)).append("*\n");
        if (horario != null) {
            texto.append("Horário: *").append(HORA.format(horario)).append("*\n");
        }
        if (condicoes != null && !condicoes.isBlank()) {
            texto.append("Condições: ").append(condicoes).append('\n');
        }
        texto.append("\n*1* - Confirmar\n*2* - Voltar ao menu");
        return texto.toString();
    }

    public static final String DEMANDA_REGISTRADA = """
            Solicitação registrada! ✅
            A secretaria vai avaliar o seu pedido e você recebe a resposta por aqui. 📩""";

    // ------------------------------------------------------------ minhas viagens / cancelar

    public static String minhasViagens(List<SolicitacaoViagem> solicitacoes) {
        return "Suas solicitações mais recentes:\n\n" + solicitacoes.stream()
                .map(s -> "• " + DATA.format(s.getDataDesejada()) + " — " + nomeDestino(s)
                        + " — *" + s.getStatus().getRotulo() + "*")
                .collect(Collectors.joining("\n"));
    }

    public static String escolherCancelamento(List<SolicitacaoViagem> ativas) {
        StringBuilder texto = new StringBuilder("Qual solicitação você quer cancelar?\n");
        for (int i = 0; i < ativas.size(); i++) {
            SolicitacaoViagem s = ativas.get(i);
            texto.append('\n').append('*').append(i + 1).append("* - ")
                    .append(DATA.format(s.getDataDesejada())).append(" — ").append(nomeDestino(s))
                    .append(" (").append(s.getStatus().getRotulo()).append(')');
        }
        texto.append("\n\nResponda com o número, ou *0* para voltar ao menu.");
        return texto.toString();
    }

    public static String cancelamentoConfirmado(SolicitacaoViagem solicitacao) {
        return "Solicitação de " + DATA.format(solicitacao.getDataDesejada()) + " para *"
                + nomeDestino(solicitacao) + "* cancelada. ✅";
    }

    // ------------------------------------------------------------ acesso à plataforma

    public static String acessoPlataforma(String linkCompleto) {
        return """
                Para acessar o sistema pelo navegador, defina a sua senha neste link:
                """ + linkCompleto + """

                Depois, entre com o seu telefone e a senha que você criar.
                O link vale por alguns dias.""";
    }

    // ------------------------------------------------------------ erros

    /** Erros de regra de negócio repassados como resposta amigável. */
    public static String erroDeRegra(String mensagem) {
        return "⚠️ " + mensagem + ".\nVoltei ao menu para você tentar de novo.";
    }

    // ------------------------------------------------------------ apoio

    private static String nomeDestino(SolicitacaoViagem s) {
        Cidade destino = s.getDestino();
        return destino != null ? destino.getNome() : "—";
    }

    /** Dias abreviados (Seg, Ter, …) em ordem domingo→sábado, robusto à ordem do set. */
    private static String diasAbreviados(Set<DiaSemana> dias) {
        return Stream.of(DiaSemana.values())
                .filter(dias::contains)
                .map(d -> d.getRotulo().substring(0, 3))
                .collect(Collectors.joining(", "));
    }
}
