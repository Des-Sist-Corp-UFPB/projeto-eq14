package br.ufpb.dsc.caladrius.notificacao;

import br.ufpb.dsc.caladrius.domain.Notificacao;
import br.ufpb.dsc.caladrius.repository.NotificacaoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canal in-app: persiste a notificação para aparecer no sino da barra superior.
 */
@Component
public class NotificacaoInAppCanal implements CanalNotificacao {

    private final NotificacaoRepository repository;

    public NotificacaoInAppCanal(NotificacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    public CanalTipo tipo() {
        return CanalTipo.IN_APP;
    }

    @Override
    @Transactional
    public void enviar(NotificacaoDestino destino, String titulo, String mensagem) {
        if (destino.usuarioId() == null) {
            return; // sem usuário interno não há sino para alimentar
        }
        repository.save(new Notificacao(destino.usuarioId(), titulo, mensagem));
    }
}
