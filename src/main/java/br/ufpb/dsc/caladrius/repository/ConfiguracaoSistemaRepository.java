package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.ConfiguracaoSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório dos parâmetros de configuração do sistema (chave/valor).
 */
@Repository
public interface ConfiguracaoSistemaRepository extends JpaRepository<ConfiguracaoSistema, String> {
}
