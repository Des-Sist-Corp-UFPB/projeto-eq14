package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Municipio;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.EnderecoRepository;
import br.ufpb.dsc.caladrius.repository.MunicipioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Municípios de referência e o <strong>entitlement de pagamento</strong> por
 * município (SPEC-PLT-01, RN-FLG-05 / migration V15).
 *
 * <p>É o terceiro tipo de toggle da SPEC-PLT-01: enquanto {@link ChaveFeature} liga/desliga
 * uma funcionalidade para <em>todo mundo</em>, aqui a feature é liberada
 * <strong>por município</strong> — o marcador que a futura integração de pagamento vai
 * consultar. A avaliação é feita pelo município de <em>origem</em> do passageiro, que
 * vem do endereço (SPEC-CAD-04).
 */
@Service
@Transactional(readOnly = true)
public class MunicipioService {

    private final MunicipioRepository municipioRepository;
    private final EnderecoRepository enderecoRepository;
    private final AuditoriaService auditoriaService;

    public MunicipioService(MunicipioRepository municipioRepository,
                            EnderecoRepository enderecoRepository,
                            AuditoriaService auditoriaService) {
        this.municipioRepository = municipioRepository;
        this.enderecoRepository = enderecoRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Lista para a tela de adesão; com termo, filtra por nome (a lista da PB é longa). */
    public List<Municipio> listar(String termo) {
        return StringUtils.hasText(termo)
                ? municipioRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc(termo.trim())
                : municipioRepository.findAllByOrderByNomeAsc();
    }

    /** Municípios que já aderiram — o resumo exibido no topo da tela. */
    public List<Municipio> listarAderidos() {
        return municipioRepository.findByPagamentoHabilitadoTrueOrderByNomeAsc();
    }

    /** Marca/desmarca a adesão de um município, auditando a mudança (RN-FLG-07). */
    @Transactional
    public void definirPagamentoHabilitado(UUID municipioId, boolean habilitado) {
        Municipio municipio = municipioRepository.findById(municipioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Município", municipioId));
        boolean anterior = municipio.isPagamentoHabilitado();
        municipio.setPagamentoHabilitado(habilitado);
        municipioRepository.save(municipio);

        auditoriaService.registrarOperacao("ADESAO_PAGAMENTO", "Municipio",
                String.valueOf(municipioId),
                municipio.getNome() + "/" + municipio.getUf() + ": " + anterior + " → " + habilitado);
    }

    /**
     * Elegibilidade de um passageiro ao (futuro) fluxo de pagamento (RN-FLG-05):
     * depende do município do seu endereço. Sem endereço ou sem município cadastrado,
     * a resposta é <strong>false</strong> — o default seguro é "não cobra"
     * (RN-FLG-02).
     */
    public boolean pagamentoHabilitadoPara(UUID usuarioId) {
        return enderecoRepository.findByUsuarioId(usuarioId)
                .map(endereco -> endereco.getMunicipio() != null
                        && endereco.getMunicipio().isPagamentoHabilitado())
                .orElse(false);
    }
}
