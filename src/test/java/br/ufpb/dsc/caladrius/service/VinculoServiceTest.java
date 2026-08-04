package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusVinculo;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.VinculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários de {@link VinculoService} — a peça que diz <em>quem pertence a
 * qual secretaria, com qual papel</em> (SPEC-PLT-02 §4, ADR-22).
 *
 * <p>O vínculo é a evolução de {@code papeis_usuario}: o papel deixa de valer
 * globalmente e passa a valer <strong>dentro de uma organização</strong>. Dois
 * invariantes se destacam aqui:
 *
 * <ul>
 *   <li><strong>RN-MT-08</strong> — vínculo não se auto-concede: nasce {@code PENDENTE}
 *       e só um gestor da organização o ativa;</li>
 *   <li><strong>isolamento</strong> — buscar vínculo por id exige o dono; ninguém
 *       manipula o vínculo de outra pessoa nem descobre que ele existe.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VinculoService — pertencimento a uma secretaria (Testes Unitários)")
class VinculoServiceTest {

    @Mock private VinculoRepository vinculoRepository;
    @Mock private AuditoriaService auditoriaService;

    private VinculoService service() {
        return new VinculoService(vinculoRepository, auditoriaService);
    }

    private Usuario usuario(String nome) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto(nome);
        return u;
    }

    private Organizacao organizacao(String nome) {
        Organizacao org = new Organizacao();
        org.setId(UUID.randomUUID());
        org.setNome(nome);
        org.setSlug(nome.toLowerCase());
        return org;
    }

    @Test
    @DisplayName("RN-MT-08: o vínculo nasce PENDENTE — ninguém entra em uma secretaria por conta própria")
    void vinculoNascePendente() {
        Usuario pessoa = usuario("Fulana");
        Organizacao org = organizacao("campina");
        when(vinculoRepository.findByUsuarioIdAndOrganizacaoIdAndPapelAndStatusNot(
                pessoa.getId(), org.getId(), Papel.PASSAGEIRO, StatusVinculo.REVOGADO))
                .thenReturn(Optional.empty());
        when(vinculoRepository.save(any(Vinculo.class))).thenAnswer(i -> i.getArgument(0));

        Vinculo vinculo = service().solicitar(pessoa, org, Papel.PASSAGEIRO);

        assertThat(vinculo.getStatus()).isEqualTo(StatusVinculo.PENDENTE);
        assertThat(vinculo.getAprovadoEm()).isNull();
    }

    @Test
    @DisplayName("solicitar duas vezes não duplica: devolve o vínculo que já existe")
    void solicitarDuasVezesNaoDuplica() {
        Usuario pessoa = usuario("Fulana");
        Organizacao org = organizacao("campina");
        Vinculo existente = new Vinculo(pessoa, org, Papel.PASSAGEIRO);
        when(vinculoRepository.findByUsuarioIdAndOrganizacaoIdAndPapelAndStatusNot(
                pessoa.getId(), org.getId(), Papel.PASSAGEIRO, StatusVinculo.REVOGADO))
                .thenReturn(Optional.of(existente));

        assertThat(service().solicitar(pessoa, org, Papel.PASSAGEIRO)).isSameAs(existente);
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("aprovar ativa o vínculo, registra quem aprovou e deixa rastro na auditoria")
    void aprovarAtiva() {
        Usuario pessoa = usuario("Fulana");
        Usuario gestor = usuario("Gestor");
        Vinculo vinculo = new Vinculo(pessoa, organizacao("campina"), Papel.MOTORISTA);
        vinculo.setId(UUID.randomUUID());
        when(vinculoRepository.findById(vinculo.getId())).thenReturn(Optional.of(vinculo));
        when(vinculoRepository.save(any(Vinculo.class))).thenAnswer(i -> i.getArgument(0));

        Vinculo aprovado = service().aprovar(vinculo.getId(), gestor);

        assertThat(aprovado.getStatus()).isEqualTo(StatusVinculo.ATIVO);
        assertThat(aprovado.getAprovadoEm()).isNotNull();
        assertThat(aprovado.getAprovadoPor()).isSameAs(gestor);
        verify(auditoriaService).registrarOperacao(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("revogar tira o acesso sem apagar o registro (RN-MT-16: nada some do histórico)")
    void revogarMantemRegistro() {
        Vinculo vinculo = new Vinculo(usuario("Fulana"), organizacao("campina"), Papel.GERENTE);
        vinculo.setId(UUID.randomUUID());
        vinculo.setStatus(StatusVinculo.ATIVO);
        when(vinculoRepository.findById(vinculo.getId())).thenReturn(Optional.of(vinculo));
        when(vinculoRepository.save(any(Vinculo.class))).thenAnswer(i -> i.getArgument(0));

        service().revogar(vinculo.getId(), usuario("Gestor"));

        assertThat(vinculo.getStatus()).isEqualTo(StatusVinculo.REVOGADO);
        verify(vinculoRepository).save(vinculo);
    }

    @Test
    @DisplayName("vínculo inexistente não é aprovado (404, sem revelar nada)")
    void aprovarInexistente() {
        UUID id = UUID.randomUUID();
        when(vinculoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().aprovar(id, usuario("Gestor")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("ativosDe devolve só os vínculos ATIVOS da própria pessoa")
    void ativosDaPessoa() {
        UUID pessoaId = UUID.randomUUID();
        Vinculo ativo = new Vinculo(usuario("Fulana"), organizacao("campina"), Papel.PASSAGEIRO);
        when(vinculoRepository.findByUsuarioIdAndStatus(pessoaId, StatusVinculo.ATIVO)).thenReturn(List.of(ativo));

        assertThat(service().ativosDe(pessoaId)).containsExactly(ativo);
    }

    @Test
    @DisplayName("isolamento: buscar um vínculo exige ser o dono — o de outra pessoa é 404")
    void isolamentoPorDono() {
        UUID vinculoId = UUID.randomUUID();
        UUID outraPessoa = UUID.randomUUID();
        when(vinculoRepository.findByIdAndUsuarioId(vinculoId, outraPessoa)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().doUsuario(vinculoId, outraPessoa))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
