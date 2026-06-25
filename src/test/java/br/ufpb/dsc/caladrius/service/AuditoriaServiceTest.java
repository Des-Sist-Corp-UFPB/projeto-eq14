package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
import br.ufpb.dsc.caladrius.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link AuditoriaService} — registro das três trilhas
 * de auditoria (segurança/operação/sistema) e as consultas por perfil.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditoriaService — Testes Unitários")
class AuditoriaServiceTest {

    @Mock private LogAuditoriaRepository repository;
    @InjectMocks private AuditoriaService service;

    /** Evento de segurança grava categoria SEGURANCA com ação/resultado/autor explícitos. */
    @Test
    @DisplayName("registrarSeguranca: persiste log na categoria SEGURANCA")
    void registrarSeguranca_categoriaSeguranca() {
        UUID usuarioId = UUID.randomUUID();
        service.registrarSeguranca("LOGIN_SUCESSO", "SUCESSO", usuarioId, "Maria", "10.0.0.1");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        LogAuditoria log = captor.getValue();
        assertThat(log.getCategoria()).isEqualTo(CategoriaAuditoria.SEGURANCA);
        assertThat(log.getAcao()).isEqualTo("LOGIN_SUCESSO");
        assertThat(log.getResultado()).isEqualTo("SUCESSO");
        assertThat(log.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(log.getIp()).isEqualTo("10.0.0.1");
    }

    /** Operação de negócio grava categoria OPERACAO com entidade/identificador. */
    @Test
    @DisplayName("registrarOperacao: persiste log na categoria OPERACAO com a entidade")
    void registrarOperacao_categoriaOperacao() {
        service.registrarOperacao("USUARIO_CRIADO", "Usuario", "abc-123", "João");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        LogAuditoria log = captor.getValue();
        assertThat(log.getCategoria()).isEqualTo(CategoriaAuditoria.OPERACAO);
        assertThat(log.getEntidade()).isEqualTo("Usuario");
        assertThat(log.getEntidadeId()).isEqualTo("abc-123");
        assertThat(log.getResultado()).isEqualTo("SUCESSO");
    }

    /** Evento de sistema grava categoria SISTEMA. */
    @Test
    @DisplayName("registrarSistema: persiste log na categoria SISTEMA")
    void registrarSistema_categoriaSistema() {
        service.registrarSistema("CONFIG_ALTERADA", "timeout=45");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCategoria()).isEqualTo(CategoriaAuditoria.SISTEMA);
        assertThat(captor.getValue().getDetalhe()).isEqualTo("timeout=45");
    }

    /** Visão do SYSADMIN: trilha completa, mais recentes primeiro. */
    @Test
    @DisplayName("listarTudo: delega a consulta completa (visão SYSADMIN)")
    void listarTudo_delega() {
        Page<LogAuditoria> page = new PageImpl<>(List.of(new LogAuditoria()));
        when(repository.findAllByOrderByInstanteDesc(any(Pageable.class))).thenReturn(page);

        assertThat(service.listarTudo(Pageable.unpaged())).isSameAs(page);
    }

    /** Visão do GERENTE: apenas eventos de OPERACAO. */
    @Test
    @DisplayName("listarOperacao: filtra apenas a categoria OPERACAO (visão GERENTE)")
    void listarOperacao_filtraOperacao() {
        Page<LogAuditoria> page = new PageImpl<>(List.of(new LogAuditoria()));
        when(repository.findByCategoriaInOrderByInstanteDesc(anyList(), any(Pageable.class))).thenReturn(page);

        assertThat(service.listarOperacao(Pageable.unpaged())).isSameAs(page);
    }
}
