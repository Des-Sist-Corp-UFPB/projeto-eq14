package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.OrganizacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro das secretarias clientes — o plano de controle do modelo multi-tenant
 * (SPEC-PLT-02 §3, fase 1).
 *
 * <p>O cuidado central é o <strong>slug</strong>: ele identifica o tenant, vira
 * subdomínio e, na fase 2, o nome do schema de dados. Por isso é normalizado (sem
 * acento, minúsculo, kebab-case) e único.
 */
@Service
public class OrganizacaoService {

    /** Limite da coluna `slug` (V16) — e folga suficiente para o prefixo `tenant_`. */
    private static final int TAMANHO_MAXIMO_SLUG = 60;

    private final OrganizacaoRepository organizacaoRepository;

    public OrganizacaoService(OrganizacaoRepository organizacaoRepository) {
        this.organizacaoRepository = organizacaoRepository;
    }

    /**
     * Cria uma secretaria em {@code RASCUNHO}. Ela só passa a operar quando o pagamento
     * confirma (SPEC-PLT-03, RN-PAG-01) — este método não concede acesso a ninguém.
     *
     * @param slug identificador legível; se ausente, é derivado do nome
     */
    @Transactional
    public Organizacao criar(String nome, String documento, String slug) {
        if (!StringUtils.hasText(nome)) {
            throw new RegraNegocioException("O nome da secretaria é obrigatório.");
        }
        String slugFinal = normalizarSlug(StringUtils.hasText(slug) ? slug : nome);
        if (slugFinal.isEmpty()) {
            throw new RegraNegocioException("Não foi possível derivar um identificador do nome informado.");
        }
        if (organizacaoRepository.existsBySlug(slugFinal)) {
            throw new RegraNegocioException("Já existe uma secretaria com o identificador '" + slugFinal + "'.");
        }
        Organizacao organizacao = new Organizacao(nome.trim(), slugFinal);
        organizacao.setDocumento(StringUtils.hasText(documento) ? documento.trim() : null);
        return organizacaoRepository.save(organizacao);
    }

    @Transactional(readOnly = true)
    public Optional<Organizacao> porSlug(String slug) {
        return organizacaoRepository.findBySlug(normalizarSlug(slug));
    }

    @Transactional(readOnly = true)
    public Optional<Organizacao> porId(UUID id) {
        return organizacaoRepository.findById(id);
    }

    /**
     * Todas as secretarias. Só o SYSADMIN da plataforma enxerga esta lista
     * (RN-MT-13) — ela <strong>nunca</strong> é exposta ao usuário final, que
     * descobre a sua secretaria por convite, subdomínio ou município (§6 da spec).
     */
    @Transactional(readOnly = true)
    public List<Organizacao> listar() {
        return organizacaoRepository.findAll();
    }

    /**
     * Reduz um texto livre a um identificador seguro: sem acento, minúsculo, com
     * hífen no lugar de qualquer separador. "Secretaria de Saúde de João Pessoa"
     * vira "secretaria-de-saude-de-joao-pessoa".
     */
    public static String normalizarSlug(String texto) {
        if (!StringUtils.hasText(texto)) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = semAcento.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > TAMANHO_MAXIMO_SLUG ? slug.substring(0, TAMANHO_MAXIMO_SLUG) : slug;
    }
}
