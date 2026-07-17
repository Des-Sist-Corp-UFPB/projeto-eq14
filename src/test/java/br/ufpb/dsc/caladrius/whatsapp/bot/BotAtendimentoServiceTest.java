package br.ufpb.dsc.caladrius.whatsapp.bot;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.ConversaBot;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.EtapaConversa;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.ConversaBotRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.service.ConviteService;
import br.ufpb.dsc.caladrius.service.OnboardingService;
import br.ufpb.dsc.caladrius.service.SolicitacaoViagemService;
import br.ufpb.dsc.caladrius.service.WhatsappService;
import br.ufpb.dsc.caladrius.whatsapp.MensagemRecebida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) do {@link BotAtendimentoService} — SPEC-11:
 * onboarding do número desconhecido, solicitação sob demanda (destino → data →
 * horário → condições → confirmar), minhas viagens/cancelar, acesso à plataforma
 * e expiração. O bot só fala com serviços de domínio + {@link WhatsappService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BotAtendimentoService — Testes Unitários (SPEC-11)")
class BotAtendimentoServiceTest {

    private static final String TELEFONE = "83911112222";
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Mock private ConversaBotRepository conversaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SolicitacaoViagemService solicitacaoService;
    @Mock private OnboardingService onboardingService;
    @Mock private ConviteService conviteService;
    @Mock private WhatsappService whatsappService;

    private BotAtendimentoService bot;
    private Usuario passageiro;

    @BeforeEach
    void setUp() {
        bot = new BotAtendimentoService(conversaRepository, usuarioRepository, solicitacaoService,
                onboardingService, conviteService, whatsappService, "https://app.teste");
        passageiro = new Usuario();
        passageiro.setId(UUID.randomUUID());
        passageiro.setNomeCompleto("Maria da Silva");
        passageiro.setTelefone(TELEFONE);
        passageiro.setStatus(StatusUsuario.ATIVO);
        passageiro.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
    }

    // ------------------------------------------------------------- helpers

    private MensagemRecebida mensagem(String texto) {
        return new MensagemRecebida("MSG-" + UUID.randomUUID(), TELEFONE, "Maria", texto, Instant.now());
    }

    private void usuarioIdentificado() {
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(TELEFONE))
                .thenReturn(Optional.of(passageiro));
    }

    private void numeroDesconhecido() {
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(anyString())).thenReturn(Optional.empty());
    }

    private ConversaBot conversaEm(EtapaConversa etapa) {
        ConversaBot conversa = new ConversaBot();
        conversa.setTelefone(TELEFONE);
        conversa.setEtapa(etapa);
        conversa.setAtualizadoEm(Instant.now());
        when(conversaRepository.findByTelefone(TELEFONE)).thenReturn(Optional.of(conversa));
        return conversa;
    }

    private Cidade cidade(UUID id, String nome) {
        Cidade c = new Cidade(nome, "PB", null);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private LinhaProgramada linha(String destino, LocalTime saida, DiaSemana... dias) {
        LinhaProgramada l = new LinhaProgramada();
        l.setCidadeDestino(cidade(UUID.randomUUID(), destino));
        l.setHorarioSaida(saida);
        l.setDias(EnumSet.copyOf(List.of(dias)));
        return l;
    }

    // ------------------------------------------------------------- onboarding

    @Test
    @DisplayName("RN-ONB-01: número desconhecido é guiado ao cadastro (pede o nome)")
    void numeroDesconhecidoIniciaCadastro() {
        numeroDesconhecido();
        when(conversaRepository.findByTelefone(TELEFONE)).thenReturn(Optional.empty());

        bot.processar(mensagem("oi"));

        ArgumentCaptor<ConversaBot> salva = ArgumentCaptor.forClass(ConversaBot.class);
        verify(conversaRepository).save(salva.capture());
        assertThat(salva.getValue().getEtapa()).isEqualTo(EtapaConversa.ONBOARDING_NOME);
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("cadastr"));
    }

    @Test
    @DisplayName("onboarding completo: nome → endereço → CPF cria o passageiro e abre o menu")
    void onboardingCompleto() {
        numeroDesconhecido();
        ConversaBot conversa = conversaEm(EtapaConversa.ONBOARDING_NOME);

        bot.processar(mensagem("Maria da Silva"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ONBOARDING_ENDERECO);
        assertThat(conversa.getCadNome()).isEqualTo("Maria da Silva");

        bot.processar(mensagem("Rua A, 123, Centro"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ONBOARDING_CPF);
        assertThat(conversa.getCadEndereco()).isEqualTo("Rua A, 123, Centro");

        when(onboardingService.cadastrarPassageiro(eq(TELEFONE), eq("Maria da Silva"),
                eq("Rua A, 123, Centro"), eq("529.982.247-25"))).thenReturn(passageiro);
        bot.processar(mensagem("529.982.247-25"));

        verify(onboardingService).cadastrarPassageiro(TELEFONE, "Maria da Silva", "Rua A, 123, Centro", "529.982.247-25");
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("concluído"));
    }

    @Test
    @DisplayName("onboarding: CPF inválido faz o bot repetir a etapa do CPF")
    void onboardingCpfInvalido() {
        numeroDesconhecido();
        ConversaBot conversa = conversaEm(EtapaConversa.ONBOARDING_CPF);
        conversa.setCadNome("Maria");
        conversa.setCadEndereco("Rua A");
        when(onboardingService.cadastrarPassageiro(any(), any(), any(), any()))
                .thenThrow(new RegraNegocioException("CPF inválido"));

        bot.processar(mensagem("123"));

        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ONBOARDING_CPF);
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("CPF inválido"));
    }

    // ------------------------------------------------------------- menu

    @Test
    @DisplayName("cadastrado: primeira mensagem abre saudação + menu")
    void primeiraMensagemCadastrado() {
        usuarioIdentificado();
        when(conversaRepository.findByTelefone(TELEFONE)).thenReturn(Optional.empty());

        bot.processar(mensagem("oi"));

        ArgumentCaptor<ConversaBot> salva = ArgumentCaptor.forClass(ConversaBot.class);
        verify(conversaRepository).save(salva.capture());
        assertThat(salva.getValue().getEtapa()).isEqualTo(EtapaConversa.MENU);
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("Maria"));
    }

    @Test
    @DisplayName("menu: opção inválida repete o menu")
    void menuOpcaoInvalida() {
        usuarioIdentificado();
        conversaEm(EtapaConversa.MENU);

        bot.processar(mensagem("banana"));

        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("Não entendi"));
    }

    @Test
    @DisplayName("menu opção 3: lista as linhas ativas e permanece no menu")
    void linhasDisponiveis() {
        usuarioIdentificado();
        when(solicitacaoService.linhasDisponiveis()).thenReturn(List.of(
                linha("Campina Grande", LocalTime.of(6, 0), DiaSemana.SEGUNDA, DiaSemana.QUARTA)));
        ConversaBot conversa = conversaEm(EtapaConversa.MENU);

        bot.processar(mensagem("3"));

        verify(solicitacaoService).linhasDisponiveis();
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("Campina Grande"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }

    @Test
    @DisplayName("menu opção 3 sem linhas cadastradas: avisa e permanece no menu")
    void linhasDisponiveisVazio() {
        usuarioIdentificado();
        when(solicitacaoService.linhasDisponiveis()).thenReturn(List.of());
        ConversaBot conversa = conversaEm(EtapaConversa.MENU);

        bot.processar(mensagem("3"));

        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("não há linhas"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }

    @Test
    @DisplayName("menu opção 4: gera o link de acesso à plataforma (URL pública) e volta ao menu")
    void acessoPlataforma() {
        usuarioIdentificado();
        ConversaBot conversa = conversaEm(EtapaConversa.MENU);
        when(conviteService.gerarAcessoPlataforma(passageiro.getId())).thenReturn("/ativar?token=abc");

        bot.processar(mensagem("4"));

        verify(conviteService).gerarAcessoPlataforma(passageiro.getId());
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("https://app.teste/ativar?token=abc"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }

    // ------------------------------------------------------------- solicitar sob demanda

    @Test
    @DisplayName("fluxo sob demanda: destino → data → horário → condições → confirmar cria a solicitação")
    void fluxoSobDemanda() {
        usuarioIdentificado();
        UUID cidadeId = UUID.randomUUID();
        Cidade destino = cidade(cidadeId, "João Pessoa");
        when(solicitacaoService.cidadesDestino()).thenReturn(List.of(destino));
        LocalDate data = LocalDate.now().plusDays(7);

        ConversaBot conversa = conversaEm(EtapaConversa.MENU);
        bot.processar(mensagem("1"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ESCOLHER_DESTINO);

        bot.processar(mensagem("1")); // escolhe João Pessoa
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ESCOLHER_DATA);
        assertThat(conversa.getCidadeDestino().getId()).isEqualTo(cidadeId);

        bot.processar(mensagem(data.format(DATA_BR)));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ESCOLHER_HORARIO);
        assertThat(conversa.getDataDesejada()).isEqualTo(data);

        bot.processar(mensagem("14:00"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.CONDICOES);
        assertThat(conversa.getHorarioDesejado()).isEqualTo(LocalTime.of(14, 0));

        bot.processar(mensagem("não"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.CONFIRMAR_DEMANDA);

        bot.processar(mensagem("1"));
        verify(solicitacaoService).solicitarSobDemanda(
                eq(passageiro.getId()), eq(cidadeId), eq(data), eq(LocalTime.of(14, 0)), isNull());
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("registrada"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }

    @Test
    @DisplayName("sob demanda: data no passado repete a etapa da data")
    void sobDemandaDataPassada() {
        usuarioIdentificado();
        ConversaBot conversa = conversaEm(EtapaConversa.ESCOLHER_DATA);
        conversa.setCidadeDestino(cidade(UUID.randomUUID(), "João Pessoa"));

        bot.processar(mensagem(LocalDate.now().minusDays(1).format(DATA_BR)));

        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("igual ou posterior"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.ESCOLHER_DATA);
    }

    @Test
    @DisplayName("sob demanda: regra de negócio (duplicata) vira resposta amigável e volta ao menu")
    void sobDemandaRegraDeNegocio() {
        usuarioIdentificado();
        UUID cidadeId = UUID.randomUUID();
        ConversaBot conversa = conversaEm(EtapaConversa.CONFIRMAR_DEMANDA);
        conversa.setCidadeDestino(cidade(cidadeId, "João Pessoa"));
        conversa.setDataDesejada(LocalDate.now().plusDays(4));
        when(solicitacaoService.solicitarSobDemanda(any(), any(), any(), any(), any()))
                .thenThrow(new RegraNegocioException("Você já tem uma solicitação para este destino nesta data"));

        bot.processar(mensagem("1"));

        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("já tem uma solicitação"));
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }

    // ------------------------------------------------------------- minhas viagens / cancelar

    @Test
    @DisplayName("menu opção 2: lista solicitações e, havendo ativas, oferece o cancelamento")
    void minhasViagensOfereceCancelar() {
        usuarioIdentificado();
        SolicitacaoViagem ativa = new SolicitacaoViagem();
        ativa.setId(UUID.randomUUID());
        ativa.setCidadeDestino(cidade(UUID.randomUUID(), "João Pessoa"));
        ativa.setDataDesejada(LocalDate.now().plusDays(2));
        ativa.setStatus(StatusSolicitacao.PENDENTE);
        when(solicitacaoService.listarDoPassageiro(passageiro.getId())).thenReturn(List.of(ativa));
        ConversaBot conversa = conversaEm(EtapaConversa.MENU);

        bot.processar(mensagem("2"));

        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.CANCELAR);
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("cancelar"));
    }

    @Test
    @DisplayName("cancelar: escolhe o número e o serviço cancela em nome do passageiro (isolamento)")
    void cancelarIsolado() {
        usuarioIdentificado();
        SolicitacaoViagem ativa = new SolicitacaoViagem();
        ativa.setId(UUID.randomUUID());
        ativa.setCidadeDestino(cidade(UUID.randomUUID(), "João Pessoa"));
        ativa.setDataDesejada(LocalDate.now().plusDays(2));
        ativa.setStatus(StatusSolicitacao.PENDENTE);
        when(solicitacaoService.listarDoPassageiro(passageiro.getId())).thenReturn(List.of(ativa));
        conversaEm(EtapaConversa.CANCELAR);

        bot.processar(mensagem("1"));

        verify(solicitacaoService).cancelar(ativa.getId(), passageiro.getId());
        verify(whatsappService).enviarTexto(eq(TELEFONE), contains("cancelada"));
    }

    // ------------------------------------------------------------- expiração

    @Test
    @DisplayName("RN-WPP-07: conversa expirada (30 min) recomeça com aviso")
    void conversaExpirada() {
        usuarioIdentificado();
        ConversaBot conversa = conversaEm(EtapaConversa.ESCOLHER_DATA);
        conversa.setAtualizadoEm(Instant.now().minusSeconds(31 * 60));

        bot.processar(mensagem("qualquer coisa"));

        verify(whatsappService).enviarTexto(TELEFONE, MensagensBot.AVISO_EXPIRADA);
        assertThat(conversa.getEtapa()).isEqualTo(EtapaConversa.MENU);
    }
}
