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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link WhatsappService} — a fachada do canal
 * (SPEC-10 §10).
 *
 * <p>O ponto central é a <strong>degradação graciosa</strong>: sem provedor
 * configurado (RN-WPP-02) ou com a Evolution fora do ar (RN-WPP-01), nada quebra —
 * o envio vira no-op, o painel mostra "desconectado" e o fluxo de negócio segue.
 * O {@link ObjectProvider} é justamente o que permite a app subir sem a integração.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsappService — fachada do canal (Testes Unitários)")
class WhatsappServiceTest {

    @Mock private MensagemWhatsappRepository mensagemRepository;
    @Mock private ConfiguracaoService configuracaoService;

    /** Fachada com um provedor disponível (integração configurada). */
    private WhatsappService comProvedor(ProvedorWhatsapp provedor) {
        return new WhatsappService(providerDe(provedor), mensagemRepository, configuracaoService);
    }

    /** Fachada sem provedor — o cenário "EVOLUTION_URL não definida" (RN-WPP-02). */
    private WhatsappService semProvedor() {
        return new WhatsappService(providerDe(null), mensagemRepository, configuracaoService);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ProvedorWhatsapp> providerDe(ProvedorWhatsapp provedor) {
        ObjectProvider<ProvedorWhatsapp> objectProvider = mock(ObjectProvider.class);
        // lenient: nem todo cenário consulta o provedor (registrar recebida e as
        // configurações de envio, por exemplo, não dependem dele).
        lenient().when(objectProvider.getIfAvailable()).thenReturn(provedor);
        return objectProvider;
    }

    // ------------------------------------------------------------------ envio

    @Nested
    @DisplayName("Envio de texto")
    class Envio {

        @Test
        @DisplayName("RN-WPP-02: sem integração configurada o envio é no-op (false) e nada é logado")
        void semProvedorNaoEnvia() {
            assertThat(semProvedor().enviarTexto("83999990000", "oi")).isFalse();
            verify(mensagemRepository, never()).save(any());
        }

        @Test
        @DisplayName("envio bem-sucedido devolve true e registra a mensagem ENVIADA")
        void envioComSucessoRegistra() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);

            assertThat(comProvedor(provedor).enviarTexto("83999990000", "oi")).isTrue();

            verify(provedor).enviarTexto("83999990000", "oi");
            verify(mensagemRepository).save(any(MensagemWhatsapp.class));
        }

        @Test
        @DisplayName("RN-WPP-01: falha do provedor NÃO propaga — devolve false e não registra envio")
        void falhaDoProvedorNaoPropaga() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
            doThrow(new WhatsappException("Evolution fora do ar"))
                    .when(provedor).enviarTexto(anyString(), anyString());

            assertThat(comProvedor(provedor).enviarTexto("83999990000", "oi")).isFalse();
            verify(mensagemRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------- recebimento

    @Nested
    @DisplayName("Recebimento (webhook)")
    class Recebimento {

        private final MensagemRecebida mensagem =
                new MensagemRecebida("EVT-1", "83999990000", "Fulana", "oi", Instant.now());

        @Test
        @DisplayName("RN-WPP-03: mensagem nova é registrada e liberada para o bot")
        void mensagemNova() {
            when(mensagemRepository.existsByIdProvedorAndDirecao("EVT-1", DirecaoMensagem.RECEBIDA))
                    .thenReturn(false);

            assertThat(semProvedor().registrarRecebida(mensagem)).isTrue();
            verify(mensagemRepository).save(any(MensagemWhatsapp.class));
        }

        @Test
        @DisplayName("RN-WPP-03: evento repetido devolve false e não registra de novo")
        void eventoRepetido() {
            when(mensagemRepository.existsByIdProvedorAndDirecao("EVT-1", DirecaoMensagem.RECEBIDA))
                    .thenReturn(true);

            assertThat(semProvedor().registrarRecebida(mensagem)).isFalse();
            verify(mensagemRepository, never()).save(any());
        }

        @Test
        @DisplayName("mensagem sem id do provedor é registrada (não há como deduplicar)")
        void semIdDoProvedor() {
            MensagemRecebida semId =
                    new MensagemRecebida(null, "83999990000", "Fulana", "oi", Instant.now());

            assertThat(semProvedor().registrarRecebida(semId)).isTrue();
            verify(mensagemRepository).save(any(MensagemWhatsapp.class));
        }
    }

    // ------------------------------------------------------------------ painel

    @Nested
    @DisplayName("Estado da conexão (painel)")
    class Painel {

        @Test
        @DisplayName("RN-WPP-02: sem provedor o painel mostra DESCONECTADO, mesmo com estado reportado antes")
        void semProvedorSempreDesconectado() {
            WhatsappService service = semProvedor();
            service.registrarEstadoConexao(StatusConexaoWhatsapp.CONECTADO, null);

            assertThat(service.estadoConexao().status()).isEqualTo(StatusConexaoWhatsapp.DESCONECTADO);
            assertThat(service.configurada()).isFalse();
            assertThat(service.contaConectada()).isEmpty();
        }

        @Test
        @DisplayName("estado vem do provedor (fonte da verdade): CONECTADO")
        void estadoConectado() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
            when(provedor.statusConexao()).thenReturn(StatusConexaoWhatsapp.CONECTADO);
            WhatsappService service = comProvedor(provedor);

            assertThat(service.configurada()).isTrue();
            assertThat(service.estadoConexao().status()).isEqualTo(StatusConexaoWhatsapp.CONECTADO);
        }

        @Test
        @DisplayName("AGUARDANDO_QR reaproveita o QR que o webhook trouxe (cache)")
        void aguardandoQrUsaCacheDoWebhook() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
            when(provedor.statusConexao()).thenReturn(StatusConexaoWhatsapp.AGUARDANDO_QR);
            WhatsappService service = comProvedor(provedor);
            service.registrarEstadoConexao(StatusConexaoWhatsapp.AGUARDANDO_QR, "base64-do-qr");

            ConexaoWhatsapp conexao = service.estadoConexao();

            assertThat(conexao.status()).isEqualTo(StatusConexaoWhatsapp.AGUARDANDO_QR);
            assertThat(conexao.qrCodeBase64()).isEqualTo("base64-do-qr");
        }

        @Test
        @DisplayName("falha ao consultar a Evolution cai no último estado reportado (painel não quebra)")
        void falhaCaiNoUltimoEstado() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
            when(provedor.statusConexao()).thenThrow(new WhatsappException("timeout"));
            WhatsappService service = comProvedor(provedor);
            service.registrarEstadoConexao(StatusConexaoWhatsapp.CONECTADO, null);

            assertThat(service.estadoConexao().status()).isEqualTo(StatusConexaoWhatsapp.CONECTADO);
        }

        @Test
        @DisplayName("falha sem estado anterior ⇒ DESCONECTADO (nunca lança)")
        void falhaSemEstadoAnterior() {
            ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
            when(provedor.statusConexao()).thenThrow(new WhatsappException("timeout"));

            assertThat(comProvedor(provedor).estadoConexao().status())
                    .isEqualTo(StatusConexaoWhatsapp.DESCONECTADO);
        }

        @Test
        @DisplayName("contaConectada: devolve a conta do provedor; em falha, vazio")
        void contaConectada() {
            ProvedorWhatsapp ok = mock(ProvedorWhatsapp.class);
            when(ok.contaConectada()).thenReturn(Optional.of(new ContaWhatsapp("5583999990000", "Secretaria")));
            assertThat(comProvedor(ok).contaConectada()).isPresent();

            ProvedorWhatsapp falha = mock(ProvedorWhatsapp.class);
            when(falha.contaConectada()).thenThrow(new WhatsappException("timeout"));
            assertThat(comProvedor(falha).contaConectada()).isEmpty();
        }

        @Test
        @DisplayName("RN-WPP-01: erro de CONFIGURAÇÃO no boot não derruba a aplicação (regressão 2026-07-29)")
        void erroDeConfiguracaoNoBootNaoDerrubaApp() {
            // Reproduz o incidente: EVOLUTION_URL sem esquema fazia o JDK HttpClient lançar
            // IllegalArgumentException — que não é WhatsappException e escapava do listener
            // do ApplicationReadyEvent, matando o boot inteiro.
            ProvedorWhatsapp urlTorta = mock(ProvedorWhatsapp.class);
            when(urlTorta.statusConexao())
                    .thenThrow(new IllegalArgumentException("URI with undefined scheme"));

            // Não pode lançar: o listener roda no ApplicationReadyEvent.
            comProvedor(urlTorta).reRegistrarWebhookSeConectado();
        }

        @Test
        @DisplayName("no boot, conta conectada re-registra o webhook; falha não impede a app de subir")
        void reRegistroDeWebhookNoBoot() {
            ProvedorWhatsapp conectado = mock(ProvedorWhatsapp.class);
            when(conectado.statusConexao()).thenReturn(StatusConexaoWhatsapp.CONECTADO);
            comProvedor(conectado).reRegistrarWebhookSeConectado();
            verify(conectado).registrarWebhook();

            ProvedorWhatsapp desconectado = mock(ProvedorWhatsapp.class);
            when(desconectado.statusConexao()).thenReturn(StatusConexaoWhatsapp.DESCONECTADO);
            comProvedor(desconectado).reRegistrarWebhookSeConectado();
            verify(desconectado, never()).registrarWebhook();

            ProvedorWhatsapp comFalha = mock(ProvedorWhatsapp.class);
            when(comFalha.statusConexao()).thenThrow(new WhatsappException("timeout"));
            comProvedor(comFalha).reRegistrarWebhookSeConectado(); // não lança

            // Sem provedor, o listener simplesmente não faz nada.
            semProvedor().reRegistrarWebhookSeConectado();
        }
    }

    // -------------------------------------------------------- configurações de envio

    @Nested
    @DisplayName("Configurações de envio")
    class Configuracoes {

        @Test
        @DisplayName("sem nada configurado, devolve os valores padrão")
        void padroes() {
            when(configuracaoService.get(anyString())).thenReturn(Optional.empty());

            ConfiguracaoEnvioWhatsapp cfg = semProvedor().configuracaoEnvio();

            assertThat(cfg.nomeExibicao()).isEmpty();
            assertThat(cfg.mensagemConfirmacao()).contains("{data}", "{hora}", "{destino}");
        }

        @Test
        @DisplayName("salvar em branco cai no padrão (nunca grava mensagem de confirmação vazia)")
        void salvarEmBrancoUsaPadrao() {
            semProvedor().salvarConfiguracaoEnvio(new ConfiguracaoEnvioWhatsapp(null, "  "));

            verify(configuracaoService).salvar(WhatsappService.CFG_NOME, "");
            verify(configuracaoService).salvar(eq(WhatsappService.CFG_MENSAGEM), anyString());
        }

        @Test
        @DisplayName("salvar valores válidos persiste o que veio (com trim)")
        void salvarValores() {
            semProvedor().salvarConfiguracaoEnvio(
                    new ConfiguracaoEnvioWhatsapp(" SMS Saúde ", " Oi {destino} "));

            verify(configuracaoService).salvar(WhatsappService.CFG_NOME, "SMS Saúde");
            verify(configuracaoService).salvar(WhatsappService.CFG_MENSAGEM, "Oi {destino}");
        }

        @Test
        @DisplayName("mensagemConfirmacao substitui {data}/{hora}/{destino} e assina com o nome")
        void mensagemConfirmacaoRenderiza() {
            when(configuracaoService.get(WhatsappService.CFG_NOME))
                    .thenReturn(Optional.of("Secretaria de Saúde"));
            when(configuracaoService.get(WhatsappService.CFG_MENSAGEM))
                    .thenReturn(Optional.of("Viagem em {data} às {hora} para {destino}."));

            String texto = semProvedor().mensagemConfirmacao("João Pessoa", "10/08", "07:30");

            assertThat(texto)
                    .isEqualTo("Viagem em 10/08 às 07:30 para João Pessoa.\n\n— Secretaria de Saúde");
        }

        @Test
        @DisplayName("mensagemConfirmacao tolera valores nulos (troca por vazio, sem NPE)")
        void mensagemConfirmacaoComNulos() {
            when(configuracaoService.get(WhatsappService.CFG_NOME)).thenReturn(Optional.empty());
            when(configuracaoService.get(WhatsappService.CFG_MENSAGEM))
                    .thenReturn(Optional.of("[{data}][{hora}][{destino}]"));

            assertThat(semProvedor().mensagemConfirmacao(null, null, null)).isEqualTo("[][][]");
        }
    }

    // ------------------------------------------------------------ conectar/desconectar

    @Test
    @DisplayName("conectar/desconectar delegam ao provedor e atualizam o cache do painel")
    void conectarEDesconectar() {
        ProvedorWhatsapp provedor = mock(ProvedorWhatsapp.class);
        when(provedor.iniciarConexao())
                .thenReturn(ConexaoWhatsapp.aguardandoQr("qr-novo"));

        @SuppressWarnings("unchecked")
        ObjectProvider<ProvedorWhatsapp> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getObject()).thenReturn(provedor);
        WhatsappService service =
                new WhatsappService(objectProvider, mensagemRepository, configuracaoService);

        assertThat(service.conectar().qrCodeBase64()).isEqualTo("qr-novo");

        service.desconectar();
        verify(provedor).desconectar();
    }

    @Test
    @DisplayName("sem provedor, conectar() falha explicitamente (a tela só oferece a ação quando configurada)")
    void conectarSemProvedorFalha() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ProvedorWhatsapp> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getObject()).thenThrow(new IllegalStateException("sem bean"));
        WhatsappService service =
                new WhatsappService(objectProvider, mensagemRepository, configuracaoService);

        assertThatThrownBy(service::conectar).isInstanceOf(IllegalStateException.class);
    }
}
