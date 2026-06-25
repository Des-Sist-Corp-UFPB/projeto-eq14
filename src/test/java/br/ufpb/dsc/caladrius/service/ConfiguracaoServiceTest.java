package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.ConfiguracaoSistema;
import br.ufpb.dsc.caladrius.repository.ConfiguracaoSistemaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link ConfiguracaoService} — configuração dinâmica
 * de runtime (timeout de sessão e cidade-sede), incluindo defaults e validação de limites.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfiguracaoService — Testes Unitários")
class ConfiguracaoServiceTest {

    @Mock private ConfiguracaoSistemaRepository repository;
    @InjectMocks private ConfiguracaoService service;

    /** Sem valor gravado, o timeout cai no padrão de 30 minutos. */
    @Test
    @DisplayName("getTimeoutSessaoMinutos: ausente retorna o padrão (30)")
    void timeout_ausente_retornaPadrao() {
        when(repository.findById(ConfiguracaoService.CHAVE_TIMEOUT)).thenReturn(Optional.empty());
        assertThat(service.getTimeoutSessaoMinutos()).isEqualTo(30);
    }

    /** Valor não-numérico no banco não quebra: retorna o padrão. */
    @Test
    @DisplayName("getTimeoutSessaoMinutos: valor inválido retorna o padrão")
    void timeout_invalido_retornaPadrao() {
        when(repository.findById(ConfiguracaoService.CHAVE_TIMEOUT))
                .thenReturn(Optional.of(new ConfiguracaoSistema(ConfiguracaoService.CHAVE_TIMEOUT, "abc")));
        assertThat(service.getTimeoutSessaoMinutos()).isEqualTo(30);
    }

    /** Valor válido é lido e convertido. */
    @Test
    @DisplayName("getTimeoutSessaoMinutos: valor válido é lido")
    void timeout_valido_lido() {
        when(repository.findById(ConfiguracaoService.CHAVE_TIMEOUT))
                .thenReturn(Optional.of(new ConfiguracaoSistema(ConfiguracaoService.CHAVE_TIMEOUT, " 45 ")));
        assertThat(service.getTimeoutSessaoMinutos()).isEqualTo(45);
    }

    /** O setter limita o valor ao intervalo [1, 1440] (clamp do limite superior). */
    @Test
    @DisplayName("setTimeoutSessaoMinutos: acima do máximo é limitado a 1440")
    void setTimeout_acimaMaximo_limita() {
        when(repository.findById(ConfiguracaoService.CHAVE_TIMEOUT)).thenReturn(Optional.empty());
        service.setTimeoutSessaoMinutos(99999);

        ArgumentCaptor<ConfiguracaoSistema> captor = ArgumentCaptor.forClass(ConfiguracaoSistema.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValor()).isEqualTo("1440");
    }

    /** O setter limita o valor ao intervalo [1, 1440] (clamp do limite inferior). */
    @Test
    @DisplayName("setTimeoutSessaoMinutos: abaixo do mínimo é limitado a 1")
    void setTimeout_abaixoMinimo_limita() {
        when(repository.findById(ConfiguracaoService.CHAVE_TIMEOUT)).thenReturn(Optional.empty());
        service.setTimeoutSessaoMinutos(0);

        ArgumentCaptor<ConfiguracaoSistema> captor = ArgumentCaptor.forClass(ConfiguracaoSistema.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValor()).isEqualTo("1");
    }

    /** Cidade-sede em branco é tratada como ausente. */
    @Test
    @DisplayName("getCidadeSedeId: valor em branco é ausente")
    void cidadeSede_branco_ausente() {
        when(repository.findById(ConfiguracaoService.CHAVE_CIDADE_SEDE))
                .thenReturn(Optional.of(new ConfiguracaoSistema(ConfiguracaoService.CHAVE_CIDADE_SEDE, "  ")));
        assertThat(service.getCidadeSedeId()).isEmpty();
    }

    /** Cidade-sede com UUID válido é convertida. */
    @Test
    @DisplayName("getCidadeSedeId: UUID válido é convertido")
    void cidadeSede_valido_converte() {
        UUID id = UUID.randomUUID();
        when(repository.findById(ConfiguracaoService.CHAVE_CIDADE_SEDE))
                .thenReturn(Optional.of(new ConfiguracaoSistema(ConfiguracaoService.CHAVE_CIDADE_SEDE, id.toString())));
        assertThat(service.getCidadeSedeId()).contains(id);
    }

    /** Definir cidade-sede como null grava string vazia (limpa o valor). */
    @Test
    @DisplayName("setCidadeSede: null grava valor vazio")
    void setCidadeSede_null_gravaVazio() {
        when(repository.findById(ConfiguracaoService.CHAVE_CIDADE_SEDE)).thenReturn(Optional.empty());
        service.setCidadeSede(null);

        ArgumentCaptor<ConfiguracaoSistema> captor = ArgumentCaptor.forClass(ConfiguracaoSistema.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValor()).isEmpty();
    }

    /** salvar: chave nova cria; reaproveita a entidade existente quando já há valor. */
    @Test
    @DisplayName("salvar: atualiza a entidade existente quando a chave já existe")
    void salvar_chaveExistente_atualiza() {
        ConfiguracaoSistema existente = new ConfiguracaoSistema("k", "antigo");
        when(repository.findById("k")).thenReturn(Optional.of(existente));

        service.salvar("k", "novo");

        assertThat(existente.getValor()).isEqualTo("novo");
        org.mockito.Mockito.verify(repository).save(existente);
    }
}
