package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Municipio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório dos municípios de referência (lista IBGE).
 */
@Repository
public interface MunicipioRepository extends JpaRepository<Municipio, UUID> {

    /** Lista para o select de endereço (ordenada por nome). */
    List<Municipio> findAllByOrderByNomeAsc();

    /** Busca por nome na tela de adesão ao pagamento (SPEC-PLT-01) — a lista da PB é longa. */
    List<Municipio> findByNomeContainingIgnoreCaseOrderByNomeAsc(String nome);

    /** Municípios que aderiram ao pagamento (SPEC-PLT-01, RN-FLG-05). */
    List<Municipio> findByPagamentoHabilitadoTrueOrderByNomeAsc();
}
