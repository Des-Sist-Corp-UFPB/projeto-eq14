package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acesso às secretarias clientes (SPEC-PLT-02). */
public interface OrganizacaoRepository extends JpaRepository<Organizacao, UUID> {

    Optional<Organizacao> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
