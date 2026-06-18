package br.ufpb.dsc.caladrius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Parâmetro de configuração do sistema (chave/valor), gerido pelo SYSADMIN em
 * runtime — sem redeploy. A chave é a PK natural.
 *
 * @see br.ufpb.dsc.caladrius.service.ConfiguracaoService
 */
@Entity
@Table(name = "configuracoes_sistema")
public class ConfiguracaoSistema {

    @Id
    @Column(name = "chave", length = 80, nullable = false, updatable = false)
    private String chave;

    @Column(name = "valor", length = 255, nullable = false)
    private String valor;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public ConfiguracaoSistema() {
    }

    public ConfiguracaoSistema(String chave, String valor) {
        this.chave = chave;
        this.valor = valor;
    }

    @PrePersist
    @PreUpdate
    protected void marcarAtualizacao() {
        this.atualizadoEm = Instant.now();
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(Instant atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
