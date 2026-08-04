package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link FeatureFlagService} — feature toggle (SPEC-PLT-01 §11).
 * Escritos <strong>antes</strong> da implementação (TDD).
 *
 * <p>Cobrem as três garantias do mecanismo: <em>fail-safe</em> (RN-FLG-02), intervalo
 * válido dos parâmetros (RN-FLG-06) e cache com invalidação na escrita (RN-FLG-08),
 * além da auditoria de toda alteração (RN-FLG-07).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagService — Feature toggle (Testes Unitários / TDD)")
class FeatureFlagServiceTest {

    @Mock private ConfiguracaoService configuracaoService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private FeatureFlagService service;

    // ============================================================ flags globais

    @Nested
    @DisplayName("Flags globais (feature.*)")
    class Flags {

        @Test
        @DisplayName("RN-FLG-02: chave ausente ⇒ default do código (bot ligado, manutenção desligada)")
        void ausenteUsaDefault() {
            when(configuracaoService.get(anyString())).thenReturn(Optional.empty());

            assertThat(service.ativo(ChaveFeature.BOT_WHATSAPP)).isTrue();
            assertThat(service.ativo(ChaveFeature.MODO_MANUTENCAO)).isFalse();
        }

        @Test
        @DisplayName("lê o valor gravado ('true'/'false'), ignorando espaços e caixa")
        void leValorGravado() {
            when(configuracaoService.get(ChaveFeature.BOT_WHATSAPP.getChave()))
                    .thenReturn(Optional.of(" FALSE "));
            when(configuracaoService.get(ChaveFeature.MODO_MANUTENCAO.getChave()))
                    .thenReturn(Optional.of("True"));

            assertThat(service.ativo(ChaveFeature.BOT_WHATSAPP)).isFalse();
            assertThat(service.ativo(ChaveFeature.MODO_MANUTENCAO)).isTrue();
        }

        @Test
        @DisplayName("RN-FLG-02: valor inválido ('talvez') ⇒ default, sem lançar exceção")
        void valorInvalidoUsaDefault() {
            when(configuracaoService.get(ChaveFeature.BOT_WHATSAPP.getChave()))
                    .thenReturn(Optional.of("talvez"));

            assertThat(service.ativo(ChaveFeature.BOT_WHATSAPP)).isTrue();
        }

        @Test
        @DisplayName("RN-FLG-01: o valor é cacheado — a 2ª leitura não vai ao banco")
        void leituraUsaCache() {
            when(configuracaoService.get(ChaveFeature.BOT_WHATSAPP.getChave()))
                    .thenReturn(Optional.of("false"));

            service.ativo(ChaveFeature.BOT_WHATSAPP);
            service.ativo(ChaveFeature.BOT_WHATSAPP);
            service.ativo(ChaveFeature.BOT_WHATSAPP);

            verify(configuracaoService, times(1)).get(ChaveFeature.BOT_WHATSAPP.getChave());
        }

        @Test
        @DisplayName("RN-FLG-08: salvar invalida o cache — a leitura seguinte enxerga o novo valor")
        void salvarInvalidaCache() {
            when(configuracaoService.get(ChaveFeature.BOT_WHATSAPP.getChave()))
                    .thenReturn(Optional.of("true"), Optional.of("false"));

            assertThat(service.ativo(ChaveFeature.BOT_WHATSAPP)).isTrue();
            service.definir(ChaveFeature.BOT_WHATSAPP, false);

            assertThat(service.ativo(ChaveFeature.BOT_WHATSAPP)).isFalse();
            verify(configuracaoService).salvar(ChaveFeature.BOT_WHATSAPP.getChave(), "false");
        }

        @Test
        @DisplayName("RN-FLG-07: alterar uma flag gera registro de auditoria com antes → depois")
        void alteracaoEhAuditada() {
            when(configuracaoService.get(ChaveFeature.MODO_MANUTENCAO.getChave()))
                    .thenReturn(Optional.empty());

            service.definir(ChaveFeature.MODO_MANUTENCAO, true);

            verify(auditoriaService).registrarSistema(eq("FEATURE_ALTERADA"),
                    contains(ChaveFeature.MODO_MANUTENCAO.getChave()));
        }

        @Test
        @DisplayName("estadoDasFlags: devolve todas as flags conhecidas com o valor efetivo")
        void estadoDasFlags() {
            when(configuracaoService.get(ChaveFeature.BOT_WHATSAPP.getChave()))
                    .thenReturn(Optional.of("false"));
            when(configuracaoService.get(ChaveFeature.MODO_MANUTENCAO.getChave()))
                    .thenReturn(Optional.empty());

            assertThat(service.estadoDasFlags())
                    .containsEntry(ChaveFeature.BOT_WHATSAPP, false)
                    .containsEntry(ChaveFeature.MODO_MANUTENCAO, false)
                    .hasSize(ChaveFeature.values().length);
        }
    }

    // ======================================================= parâmetros de negócio

    @Nested
    @DisplayName("Parâmetros de negócio (param.*)")
    class Parametros {

        @Test
        @DisplayName("RN-FLG-02: chave ausente ⇒ default (10 min, 5 tentativas, 60 s)")
        void ausenteUsaDefault() {
            when(configuracaoService.get(anyString())).thenReturn(Optional.empty());

            assertThat(service.parametro(ParametroSistema.OTP_VALIDADE_MINUTOS)).isEqualTo(10);
            assertThat(service.parametro(ParametroSistema.OTP_MAX_TENTATIVAS)).isEqualTo(5);
            assertThat(service.parametro(ParametroSistema.OTP_COOLDOWN_SEGUNDOS)).isEqualTo(60);
        }

        @Test
        @DisplayName("lê o valor configurado quando ele está dentro do intervalo")
        void leValorConfigurado() {
            when(configuracaoService.get(ParametroSistema.OTP_VALIDADE_MINUTOS.getChave()))
                    .thenReturn(Optional.of("15"));

            assertThat(service.parametro(ParametroSistema.OTP_VALIDADE_MINUTOS)).isEqualTo(15);
        }

        @Test
        @DisplayName("RN-FLG-06: valor fora do intervalo (999 min) ⇒ default, sem quebrar")
        void foraDoIntervaloUsaDefault() {
            when(configuracaoService.get(ParametroSistema.OTP_VALIDADE_MINUTOS.getChave()))
                    .thenReturn(Optional.of("999"));

            assertThat(service.parametro(ParametroSistema.OTP_VALIDADE_MINUTOS)).isEqualTo(10);
        }

        @Test
        @DisplayName("RN-FLG-02: valor não numérico ('dez') ⇒ default")
        void naoNumericoUsaDefault() {
            when(configuracaoService.get(ParametroSistema.OTP_MAX_TENTATIVAS.getChave()))
                    .thenReturn(Optional.of("dez"));

            assertThat(service.parametro(ParametroSistema.OTP_MAX_TENTATIVAS)).isEqualTo(5);
        }

        @Test
        @DisplayName("definir: grava, invalida o cache e audita")
        void definirGravaEAudita() {
            when(configuracaoService.get(ParametroSistema.OTP_MAX_TENTATIVAS.getChave()))
                    .thenReturn(Optional.empty(), Optional.of("3"));

            assertThat(service.parametro(ParametroSistema.OTP_MAX_TENTATIVAS)).isEqualTo(5);
            service.definir(ParametroSistema.OTP_MAX_TENTATIVAS, 3);

            assertThat(service.parametro(ParametroSistema.OTP_MAX_TENTATIVAS)).isEqualTo(3);
            verify(configuracaoService).salvar(ParametroSistema.OTP_MAX_TENTATIVAS.getChave(), "3");
            verify(auditoriaService).registrarSistema(eq("PARAMETRO_ALTERADO"), anyString());
        }

        @Test
        @DisplayName("RN-FLG-06: gravar valor fora do intervalo é recusado (nada é salvo)")
        void definirForaDoIntervaloRecusa() {
            assertThatThrownBy(() -> service.definir(ParametroSistema.OTP_MAX_TENTATIVAS, 99))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("1 e 10");

            verify(configuracaoService, never()).salvar(anyString(), anyString());
            verify(auditoriaService, never()).registrarSistema(anyString(), anyString());
        }
    }
}
