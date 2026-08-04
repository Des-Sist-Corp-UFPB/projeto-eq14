package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import br.ufpb.dsc.caladrius.domain.enums.StatusOrganizacao;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.OrganizacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários de {@link OrganizacaoService} — o cadastro de secretarias do
 * plano de controle (SPEC-PLT-02 §3, fase 1).
 *
 * <p>A organização é a <strong>fronteira</strong> do sistema multi-tenant: é dela que
 * o vínculo (e, na fase 2, o schema de dados) depende. Por isso o slug — que vai virar
 * subdomínio e nome de schema — nasce normalizado e único.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizacaoService — cadastro de secretarias (Testes Unitários)")
class OrganizacaoServiceTest {

    @Mock private OrganizacaoRepository organizacaoRepository;
    @InjectMocks private OrganizacaoService service;

    private void aceitaSlugNovo() {
        when(organizacaoRepository.existsBySlug(anyString())).thenReturn(false);
        when(organizacaoRepository.save(any(Organizacao.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("o slug é normalizado: acentos, maiúsculas e espaços viram kebab-case")
    void slugNormalizado() {
        aceitaSlugNovo();

        Organizacao org = service.criar("Secretaria de Saúde de Campina Grande", null, "  Campina Grande  ");

        assertThat(org.getSlug()).isEqualTo("campina-grande");
    }

    @Test
    @DisplayName("sem slug informado, ele é derivado do nome da secretaria")
    void slugDerivadoDoNome() {
        aceitaSlugNovo();

        assertThat(service.criar("Secretaria de Saúde de João Pessoa", null, null).getSlug())
                .isEqualTo("secretaria-de-saude-de-joao-pessoa");
    }

    @Test
    @DisplayName("a organização nasce em RASCUNHO e SEM schema de dados (tenant legado até provisionar)")
    void nasceEmRascunhoSemSchema() {
        aceitaSlugNovo();

        Organizacao org = service.criar("Secretaria de Saúde de Caruaru", "12345678000199", null);

        assertThat(org.getStatus()).isEqualTo(StatusOrganizacao.RASCUNHO);
        assertThat(org.getSchemaDados()).isNull();
        assertThat(org.getDocumento()).isEqualTo("12345678000199");
    }

    @Test
    @DisplayName("slug já usado é recusado — ele identifica o tenant, não pode colidir")
    void slugDuplicadoRecusado() {
        when(organizacaoRepository.existsBySlug("campina-grande")).thenReturn(true);

        assertThatThrownBy(() -> service.criar("Outra Secretaria", null, "campina-grande"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("campina-grande");

        verify(organizacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("nome em branco é recusado (o slug derivado ficaria vazio)")
    void nomeEmBrancoRecusado() {
        assertThatThrownBy(() -> service.criar("   ", null, null))
                .isInstanceOf(RegraNegocioException.class);

        verify(organizacaoRepository, never()).save(any());
    }
}
