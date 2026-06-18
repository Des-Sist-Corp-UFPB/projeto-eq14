package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Endereco;
import br.ufpb.dsc.caladrius.domain.Municipio;
import br.ufpb.dsc.caladrius.dto.EnderecoForm;
import br.ufpb.dsc.caladrius.repository.EnderecoRepository;
import br.ufpb.dsc.caladrius.repository.MunicipioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Endereço do passageiro e consultas de análise (SPEC-07).
 */
@Service
@Transactional(readOnly = true)
public class EnderecoService {

    private final EnderecoRepository enderecoRepository;
    private final MunicipioRepository municipioRepository;

    public EnderecoService(EnderecoRepository enderecoRepository, MunicipioRepository municipioRepository) {
        this.enderecoRepository = enderecoRepository;
        this.municipioRepository = municipioRepository;
    }

    /** Lista de municípios para o select (ordenada por nome). */
    public List<Municipio> listarMunicipios() {
        return municipioRepository.findAllByOrderByNomeAsc();
    }

    /** Endereço do passageiro (se houver). */
    public Optional<Endereco> buscarPorUsuario(UUID usuarioId) {
        return enderecoRepository.findByUsuarioId(usuarioId);
    }

    /** RN-END-02: usado pelo fluxo de solicitação de viagem (futuro). */
    public boolean enderecoCadastrado(UUID usuarioId) {
        return enderecoRepository.existsByUsuarioId(usuarioId);
    }

    /**
     * Cria/atualiza (upsert) o endereço do passageiro. Se o formulário vier vazio
     * e não houver endereço, não faz nada.
     */
    @Transactional
    public void salvar(UUID usuarioId, EnderecoForm form) {
        Optional<Endereco> existente = enderecoRepository.findByUsuarioId(usuarioId);
        if (form.vazio() && existente.isEmpty()) {
            return;
        }
        Endereco endereco = existente.orElseGet(() -> {
            Endereco novo = new Endereco();
            novo.setUsuarioId(usuarioId);
            return novo;
        });

        Municipio municipio = form.municipioId() == null ? null
                : municipioRepository.findById(form.municipioId()).orElse(null);
        endereco.setMunicipio(municipio);
        endereco.setBairro(trimOuNulo(form.bairro()));
        endereco.setLogradouro(trimOuNulo(form.logradouro()));
        endereco.setNumero(trimOuNulo(form.numero()));
        endereco.setComplemento(trimOuNulo(form.complemento()));
        endereco.setPontoReferencia(trimOuNulo(form.pontoReferencia()));
        endereco.setCep(normalizarCep(form.cep()));
        enderecoRepository.save(endereco);
    }

    // ----------------------------------------------------------- análise

    public List<EnderecoRepository.Contagem> contarPorBairro() {
        return enderecoRepository.contarPorBairro();
    }

    public List<EnderecoRepository.Contagem> contarPorMunicipio() {
        return enderecoRepository.contarPorMunicipio();
    }

    // ----------------------------------------------------------- helpers

    private String trimOuNulo(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** CEP: só dígitos; com 8 dígitos vira {@code 00000-000}; senão mantém o trim. */
    private String normalizarCep(String cep) {
        if (!StringUtils.hasText(cep)) {
            return null;
        }
        String digitos = Documentos.apenasDigitos(cep);
        if (digitos.length() == 8) {
            return digitos.substring(0, 5) + "-" + digitos.substring(5);
        }
        return cep.trim();
    }
}
