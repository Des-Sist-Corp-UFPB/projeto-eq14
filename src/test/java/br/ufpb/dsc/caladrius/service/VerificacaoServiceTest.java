package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.CodigoVerificacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.CodigoVerificacaoRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link VerificacaoService} — engine de OTP (SPEC-12).
 * Escritos <strong>antes</strong> da implementação (TDD).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificacaoService — OTP (Testes Unitários / TDD)")
class VerificacaoServiceTest {

    @Mock private CodigoVerificacaoRepository codigoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacaoService notificacaoService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private VerificacaoService service;

    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    // ------------------------------------------------------------- enviarCodigo

    @Test
    @DisplayName("enviarCodigo: persiste o HASH (não o código cru) e envia pelo canal")
    void enviarCodigo_guardaHashEEnvia() {
        Usuario u = usuario(StatusUsuario.PENDENTE);
        when(codigoRepository.findByUsuarioIdAndFinalidadeAndUsadoEmIsNull(u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE))
                .thenReturn(List.of());
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enviarCodigo(u, FinalidadeCodigo.VERIFICAR_TELEFONE, CanalTipo.WHATSAPP, "1.2.3.4");

        ArgumentCaptor<CodigoVerificacao> cod = ArgumentCaptor.forClass(CodigoVerificacao.class);
        verify(codigoRepository).save(cod.capture());
        CodigoVerificacao salvo = cod.getValue();
        assertThat(salvo.getUsuarioId()).isEqualTo(u.getId());
        assertThat(salvo.getFinalidade()).isEqualTo(FinalidadeCodigo.VERIFICAR_TELEFONE);
        assertThat(salvo.getCanal()).isEqualTo(CanalTipo.WHATSAPP);
        assertThat(salvo.getTentativas()).isZero();
        assertThat(salvo.getExpiraEm()).isAfter(Instant.now());
        assertThat(salvo.getCriadoIp()).isEqualTo("1.2.3.4");
        assertThat(salvo.getCodigoHash()).hasSize(64); // SHA-256 hex

        // O código cru vai na mensagem enviada; o que foi persistido é o seu hash.
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService).enviar(any(NotificacaoDestino.class), anyString(), msg.capture(),
                eq(CanalTipo.WHATSAPP));
        Matcher m = SEIS_DIGITOS.matcher(msg.getValue());
        assertThat(m.find()).as("mensagem contém um código de 6 dígitos").isTrue();
        String codigoCru = m.group(1);
        assertThat(salvo.getCodigoHash())
                .isEqualTo(sha256(codigoCru))
                .isNotEqualTo(codigoCru);
    }

    @Test
    @DisplayName("enviarCodigo: invalida códigos ativos anteriores da mesma finalidade")
    void enviarCodigo_invalidaAnteriores() {
        Usuario u = usuario(StatusUsuario.ATIVO);
        CodigoVerificacao anterior = codigo(u.getId(), FinalidadeCodigo.RESET_SENHA,
                sha256("111111"), Instant.now().plusSeconds(300), 0);
        when(codigoRepository.findByUsuarioIdAndFinalidadeAndUsadoEmIsNull(u.getId(), FinalidadeCodigo.RESET_SENHA))
                .thenReturn(List.of(anterior));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enviarCodigo(u, FinalidadeCodigo.RESET_SENHA, CanalTipo.WHATSAPP, "9.9.9.9");

        assertThat(anterior.getUsadoEm()).as("código anterior é invalidado").isNotNull();
    }

    // ------------------------------------------------------------- validar

    @Test
    @DisplayName("validar: código correto e no prazo marca usado, sem lançar")
    void validar_sucesso() {
        UUID uid = UUID.randomUUID();
        CodigoVerificacao c = codigo(uid, FinalidadeCodigo.RESET_SENHA,
                sha256("123456"), Instant.now().plusSeconds(300), 0);
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                uid, FinalidadeCodigo.RESET_SENHA)).thenReturn(Optional.of(c));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));

        service.validar(uid, FinalidadeCodigo.RESET_SENHA, "123456");

        assertThat(c.getUsadoEm()).isNotNull();
    }

    @Test
    @DisplayName("validar: código errado incrementa tentativas e lança (mensagem genérica)")
    void validar_codigoErrado_incrementa() {
        UUID uid = UUID.randomUUID();
        CodigoVerificacao c = codigo(uid, FinalidadeCodigo.RESET_SENHA,
                sha256("123456"), Instant.now().plusSeconds(300), 0);
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                uid, FinalidadeCodigo.RESET_SENHA)).thenReturn(Optional.of(c));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.validar(uid, FinalidadeCodigo.RESET_SENHA, "000000"))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(c.getTentativas()).isEqualTo(1);
        assertThat(c.getUsadoEm()).as("ainda utilizável para nova tentativa").isNull();
    }

    @Test
    @DisplayName("validar: na 5ª tentativa errada faz lockout (invalida o código)")
    void validar_lockout() {
        UUID uid = UUID.randomUUID();
        CodigoVerificacao c = codigo(uid, FinalidadeCodigo.VERIFICAR_TELEFONE,
                sha256("123456"), Instant.now().plusSeconds(300), 4); // já errou 4x
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                uid, FinalidadeCodigo.VERIFICAR_TELEFONE)).thenReturn(Optional.of(c));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.validar(uid, FinalidadeCodigo.VERIFICAR_TELEFONE, "000000"))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(c.getTentativas()).isEqualTo(5);
        assertThat(c.getUsadoEm()).as("código bloqueado após o teto de tentativas").isNotNull();
    }

    @Test
    @DisplayName("validar: código expirado é rejeitado")
    void validar_expirado() {
        UUID uid = UUID.randomUUID();
        CodigoVerificacao c = codigo(uid, FinalidadeCodigo.RESET_SENHA,
                sha256("123456"), Instant.now().minusSeconds(60), 0);
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                uid, FinalidadeCodigo.RESET_SENHA)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.validar(uid, FinalidadeCodigo.RESET_SENHA, "123456"))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("validar: sem código ativo é rejeitado")
    void validar_semCodigo() {
        UUID uid = UUID.randomUUID();
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                uid, FinalidadeCodigo.RESET_SENHA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(uid, FinalidadeCodigo.RESET_SENHA, "123456"))
                .isInstanceOf(RegraNegocioException.class);
    }

    // ------------------------------------------------------------- confirmarTelefone

    @Test
    @DisplayName("confirmarTelefone: marca telefone_verificado_em e promove PENDENTE→ATIVO")
    void confirmarTelefone_promove() {
        Usuario u = usuario(StatusUsuario.PENDENTE);
        CodigoVerificacao c = codigo(u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE,
                sha256("123456"), Instant.now().plusSeconds(300), 0);
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE)).thenReturn(Optional.of(c));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmarTelefone(u.getId(), "123456");

        assertThat(u.getTelefoneVerificadoEm()).isNotNull();
        assertThat(u.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(c.getUsadoEm()).isNotNull();
    }

    // ------------------------------------------------------------- cooldown

    @Test
    @DisplayName("enviarCodigo: dentro do cooldown de 60s é rejeitado e não cria novo código")
    void enviarCodigo_dentroDoCooldown_rejeita() {
        Usuario u = usuario(StatusUsuario.ATIVO);
        CodigoVerificacao recente = codigo(u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE,
                sha256("999999"), Instant.now().plusSeconds(300), 0);
        recente.setCriadoEm(Instant.now()); // criado agora
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(
                u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE)).thenReturn(Optional.of(recente));

        assertThatThrownBy(() -> service.enviarCodigo(u, FinalidadeCodigo.VERIFICAR_TELEFONE,
                CanalTipo.WHATSAPP, "1.2.3.4"))
                .isInstanceOf(RegraNegocioException.class);

        verify(codigoRepository, never()).save(any(CodigoVerificacao.class));
    }

    // ------------------------------------------- fluxos públicos (por telefone)

    @Test
    @DisplayName("confirmarTelefonePorTelefone: resolve pelo telefone e confirma (PENDENTE→ATIVO)")
    void confirmarTelefonePorTelefone_ok() {
        Usuario u = usuario(StatusUsuario.PENDENTE);
        CodigoVerificacao c = codigo(u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE,
                sha256("123456"), Instant.now().plusSeconds(300), 0);
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));
        when(codigoRepository.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
                u.getId(), FinalidadeCodigo.VERIFICAR_TELEFONE)).thenReturn(Optional.of(c));
        when(codigoRepository.save(any(CodigoVerificacao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmarTelefonePorTelefone("(83) 99999-8888", "123456");

        assertThat(u.getTelefoneVerificadoEm()).isNotNull();
        assertThat(u.getStatus()).isEqualTo(StatusUsuario.ATIVO);
    }

    @Test
    @DisplayName("reenviarPorTelefone: telefone desconhecido é rejeitado")
    void reenviarPorTelefone_desconhecido() {
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83900000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reenviarPorTelefone("83900000000", "1.2.3.4"))
                .isInstanceOf(RegraNegocioException.class);
    }

    // ------------------------------------------------------------- helpers

    private Usuario usuario(StatusUsuario status) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto("Fulano de Tal");
        u.setTelefone("83999998888");
        u.setEmail("fulano@exemplo.com");
        u.setStatus(status);
        return u;
    }

    private CodigoVerificacao codigo(UUID usuarioId, FinalidadeCodigo finalidade,
                                     String hash, Instant expira, int tentativas) {
        CodigoVerificacao c = new CodigoVerificacao();
        c.setUsuarioId(usuarioId);
        c.setFinalidade(finalidade);
        c.setCanal(CanalTipo.WHATSAPP);
        c.setCodigoHash(hash);
        c.setExpiraEm(expira);
        c.setTentativas(tentativas);
        return c;
    }

    private static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
