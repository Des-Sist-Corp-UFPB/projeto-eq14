package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Notificacao;
import br.ufpb.dsc.caladrius.notificacao.CanalNotificacao;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.NotificacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link NotificacaoService} — orquestração dos canais
 * desacoplados (in-app/e-mail) e as consultas do sino.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacaoService — Testes Unitários")
class NotificacaoServiceTest {

    @Mock private NotificacaoRepository notificacaoRepository;

    /** Canal in-app simulado, registrado por tipo no construtor do serviço. */
    private CanalNotificacao canalInApp() {
        CanalNotificacao canal = mock(CanalNotificacao.class);
        when(canal.tipo()).thenReturn(CanalTipo.IN_APP);
        return canal;
    }

    /** enviar: encaminha ao canal cujo tipo foi pedido; tipos sem canal são ignorados. */
    @Test
    @DisplayName("enviar: roteia para o canal do tipo e ignora tipos sem canal")
    void enviar_roteiaECalaTiposSemCanal() {
        CanalNotificacao inApp = canalInApp();
        NotificacaoService service = new NotificacaoService(List.of(inApp), notificacaoRepository);
        NotificacaoDestino destino = new NotificacaoDestino(UUID.randomUUID(), null, null);

        // pede IN_APP (existe) e EMAIL (sem canal registrado → ignorado, sem erro)
        service.enviar(destino, "Título", "Mensagem", CanalTipo.IN_APP, CanalTipo.EMAIL);

        verify(inApp).enviar(destino, "Título", "Mensagem");
        // apenas um envio aconteceu (o tipo EMAIL, sem canal, foi ignorado)
        verify(inApp, times(1)).enviar(any(), any(), any());
    }

    /** notificarInApp: atalho que dispara apenas o canal in-app para o usuário. */
    @Test
    @DisplayName("notificarInApp: envia só pelo canal in-app, com o id do usuário no destino")
    void notificarInApp_apenasInApp() {
        CanalNotificacao inApp = canalInApp();
        NotificacaoService service = new NotificacaoService(List.of(inApp), notificacaoRepository);
        UUID usuarioId = UUID.randomUUID();

        service.notificarInApp(usuarioId, "Oi", "Corpo");

        verify(inApp).enviar(argThat(d -> usuarioId.equals(d.usuarioId())), eq("Oi"), eq("Corpo"));
    }

    /** naoLidas delega ao repositório (ordenado por mais recente). */
    @Test
    @DisplayName("naoLidas: delega ao repositório")
    void naoLidas_delega() {
        NotificacaoService service = new NotificacaoService(List.of(), notificacaoRepository);
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.findByUsuarioIdAndLidaFalseOrderByCriadoEmDesc(usuarioId))
                .thenReturn(List.of(new Notificacao(), new Notificacao()));

        assertThat(service.naoLidas(usuarioId)).hasSize(2);
    }

    /** contarNaoLidas delega ao repositório. */
    @Test
    @DisplayName("contarNaoLidas: delega ao repositório")
    void contarNaoLidas_delega() {
        NotificacaoService service = new NotificacaoService(List.of(), notificacaoRepository);
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId)).thenReturn(5L);

        assertThat(service.contarNaoLidas(usuarioId)).isEqualTo(5L);
    }

    /** marcarTodasLidas: marca cada não-lida e persiste o lote. */
    @Test
    @DisplayName("marcarTodasLidas: marca todas como lidas e salva em lote")
    void marcarTodasLidas_marcaESalva() {
        NotificacaoService service = new NotificacaoService(List.of(), notificacaoRepository);
        UUID usuarioId = UUID.randomUUID();
        Notificacao n1 = new Notificacao();
        Notificacao n2 = new Notificacao();
        when(notificacaoRepository.findByUsuarioIdAndLidaFalseOrderByCriadoEmDesc(usuarioId))
                .thenReturn(List.of(n1, n2));

        service.marcarTodasLidas(usuarioId);

        assertThat(n1.isLida()).isTrue();
        assertThat(n2.isLida()).isTrue();
        verify(notificacaoRepository).saveAll(List.of(n1, n2));
    }
}
