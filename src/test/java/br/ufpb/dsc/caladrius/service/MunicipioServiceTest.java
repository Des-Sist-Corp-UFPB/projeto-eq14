package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Endereco;
import br.ufpb.dsc.caladrius.domain.Municipio;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.EnderecoRepository;
import br.ufpb.dsc.caladrius.repository.MunicipioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link MunicipioService} — o <em>entitlement</em> de
 * pagamento por município (SPEC-PLT-01, RN-FLG-05). Escritos <strong>antes</strong> da
 * implementação (TDD).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MunicipioService — entitlement de pagamento (Testes Unitários / TDD)")
class MunicipioServiceTest {

    @Mock private MunicipioRepository municipioRepository;
    @Mock private EnderecoRepository enderecoRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private MunicipioService service;

    private Municipio municipio(String nome, boolean habilitado) {
        Municipio m = new Municipio();
        m.setNome(nome);
        m.setUf("PB");
        m.setPagamentoHabilitado(habilitado);
        return m;
    }

    // ------------------------------------------------------------------- listagem

    @Test
    @DisplayName("listar sem termo devolve a lista completa; com termo, filtra por nome")
    void listarComESemTermo() {
        when(municipioRepository.findAllByOrderByNomeAsc())
                .thenReturn(List.of(municipio("Cabedelo", false)));
        when(municipioRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc("camp"))
                .thenReturn(List.of(municipio("Campina Grande", false)));

        assertThat(service.listar(null)).extracting(Municipio::getNome).containsExactly("Cabedelo");
        assertThat(service.listar("  ")).extracting(Municipio::getNome).containsExactly("Cabedelo");
        assertThat(service.listar(" camp ")).extracting(Municipio::getNome)
                .containsExactly("Campina Grande");
    }

    // ------------------------------------------------------------------- adesão

    @Test
    @DisplayName("RN-FLG-05/07: marcar adesão persiste o entitlement e audita a mudança")
    void marcarAdesaoPersisteEAudita() {
        UUID id = UUID.randomUUID();
        Municipio m = municipio("Campina Grande", false);
        when(municipioRepository.findById(id)).thenReturn(Optional.of(m));

        service.definirPagamentoHabilitado(id, true);

        assertThat(m.isPagamentoHabilitado()).isTrue();
        verify(municipioRepository).save(m);
        verify(auditoriaService).registrarOperacao(eq("ADESAO_PAGAMENTO"), eq("Municipio"),
                eq(id.toString()), anyString());
    }

    @Test
    @DisplayName("município inexistente ⇒ 404 de domínio, sem salvar nem auditar")
    void municipioInexistente() {
        UUID id = UUID.randomUUID();
        when(municipioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definirPagamentoHabilitado(id, true))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(municipioRepository, never()).save(any());
        verify(auditoriaService, never())
                .registrarOperacao(anyString(), anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------- elegibilidade (RN-FLG-05)

    @Test
    @DisplayName("RN-FLG-05: passageiro de município aderido é elegível; de município comum, não")
    void elegibilidadeSegueOMunicipioDoEndereco() {
        UUID aderido = UUID.randomUUID();
        UUID comum = UUID.randomUUID();
        when(enderecoRepository.findByUsuarioId(aderido))
                .thenReturn(Optional.of(endereco(municipio("Campina Grande", true))));
        when(enderecoRepository.findByUsuarioId(comum))
                .thenReturn(Optional.of(endereco(municipio("Cabedelo", false))));

        assertThat(service.pagamentoHabilitadoPara(aderido)).isTrue();
        assertThat(service.pagamentoHabilitadoPara(comum)).isFalse();
    }

    @Test
    @DisplayName("RN-FLG-02: sem endereço (ou sem município) o default seguro é 'não cobra'")
    void semEnderecoNaoEhElegivel() {
        UUID semEndereco = UUID.randomUUID();
        UUID semMunicipio = UUID.randomUUID();
        when(enderecoRepository.findByUsuarioId(semEndereco)).thenReturn(Optional.empty());
        when(enderecoRepository.findByUsuarioId(semMunicipio)).thenReturn(Optional.of(endereco(null)));

        assertThat(service.pagamentoHabilitadoPara(semEndereco)).isFalse();
        assertThat(service.pagamentoHabilitadoPara(semMunicipio)).isFalse();
    }

    private Endereco endereco(Municipio municipio) {
        Endereco e = new Endereco();
        e.setMunicipio(municipio);
        return e;
    }
}
