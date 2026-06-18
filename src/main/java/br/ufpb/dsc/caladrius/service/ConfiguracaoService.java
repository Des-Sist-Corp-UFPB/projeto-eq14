package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.ConfiguracaoSistema;
import br.ufpb.dsc.caladrius.repository.ConfiguracaoSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Parâmetros de runtime do sistema (DT-10 e SPEC-06), geridos pelo SYSADMIN
 * <strong>sem redeploy</strong> — a configuração é <em>dinâmica</em> (lida do
 * banco), não estática do {@code application.yml}.
 */
@Service
@Transactional(readOnly = true)
public class ConfiguracaoService {

    /** Tempo de inatividade da sessão, em minutos (aplicado via HttpSessionListener). */
    public static final String CHAVE_TIMEOUT = "sessao.timeout_minutos";
    /** Cidade-sede (origem padrão das viagens — SPEC-06). */
    public static final String CHAVE_CIDADE_SEDE = "geral.cidade_sede_id";

    private static final int TIMEOUT_PADRAO_MIN = 30;
    private static final int TIMEOUT_MIN = 1;
    private static final int TIMEOUT_MAX = 24 * 60;

    private final ConfiguracaoSistemaRepository repository;

    public ConfiguracaoService(ConfiguracaoSistemaRepository repository) {
        this.repository = repository;
    }

    /** Lê um valor bruto. */
    public Optional<String> get(String chave) {
        return repository.findById(chave).map(ConfiguracaoSistema::getValor);
    }

    /** Grava (upsert) um valor. */
    @Transactional
    public void salvar(String chave, String valor) {
        ConfiguracaoSistema cfg = repository.findById(chave)
                .orElseGet(() -> new ConfiguracaoSistema(chave, valor));
        cfg.setValor(valor);
        repository.save(cfg);
    }

    // ----------------------------------------------------------------- sessão

    /** Tempo de sessão configurado (minutos); cai no padrão (30) se ausente/ inválido. */
    public int getTimeoutSessaoMinutos() {
        return get(CHAVE_TIMEOUT).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return TIMEOUT_PADRAO_MIN;
            }
        }).orElse(TIMEOUT_PADRAO_MIN);
    }

    /** Define o tempo de sessão (minutos), validado em [1, 1440]. */
    @Transactional
    public void setTimeoutSessaoMinutos(int minutos) {
        int v = Math.max(TIMEOUT_MIN, Math.min(TIMEOUT_MAX, minutos));
        salvar(CHAVE_TIMEOUT, String.valueOf(v));
    }

    // --------------------------------------------------------------- cidade sede

    /** Id da cidade-sede, se configurada (ignora valor em branco). */
    public Optional<UUID> getCidadeSedeId() {
        return get(CHAVE_CIDADE_SEDE)
                .filter(v -> !v.isBlank())
                .map(UUID::fromString);
    }

    /** Define (ou limpa, com {@code null}) a cidade-sede. */
    @Transactional
    public void setCidadeSede(UUID cidadeId) {
        salvar(CHAVE_CIDADE_SEDE, cidadeId == null ? "" : cidadeId.toString());
    }
}
