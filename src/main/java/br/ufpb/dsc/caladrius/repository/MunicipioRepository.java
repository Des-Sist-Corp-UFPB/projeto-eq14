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
}
