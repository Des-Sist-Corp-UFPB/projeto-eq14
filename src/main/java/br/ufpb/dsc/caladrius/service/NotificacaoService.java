package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Notificacao;
import br.ufpb.dsc.caladrius.notificacao.CanalNotificacao;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestra o envio de notificações pelos canais desacoplados (in-app, e-mail,
 * WhatsApp) e expõe as notificações in-app do sino.
 */
@Service
@Transactional(readOnly = true)
public class NotificacaoService {

    private final Map<CanalTipo, CanalNotificacao> canais = new EnumMap<>(CanalTipo.class);
    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(List<CanalNotificacao> canaisDisponiveis,
                              NotificacaoRepository notificacaoRepository) {
        canaisDisponiveis.forEach(c -> canais.put(c.tipo(), c));
        this.notificacaoRepository = notificacaoRepository;
    }

    /** Envia pelos canais indicados (os que não se aplicarem ao destino são ignorados). */
    public void enviar(NotificacaoDestino destino, String titulo, String mensagem, CanalTipo... tipos) {
        for (CanalTipo tipo : tipos) {
            CanalNotificacao canal = canais.get(tipo);
            if (canal != null) {
                canal.enviar(destino, titulo, mensagem);
            }
        }
    }

    /** Atalho para uma notificação apenas in-app (sino). */
    public void notificarInApp(UUID usuarioId, String titulo, String mensagem) {
        enviar(new NotificacaoDestino(usuarioId, null, null), titulo, mensagem, CanalTipo.IN_APP);
    }

    // ----------------------------------------------------------- sino

    public List<Notificacao> naoLidas(UUID usuarioId) {
        return notificacaoRepository.findByUsuarioIdAndLidaFalseOrderByCriadoEmDesc(usuarioId);
    }

    public long contarNaoLidas(UUID usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    @Transactional
    public void marcarTodasLidas(UUID usuarioId) {
        List<Notificacao> naoLidas = naoLidas(usuarioId);
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
    }
}
