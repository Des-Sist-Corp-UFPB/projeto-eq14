package br.ufpb.dsc.caladrius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Endereço estruturado do passageiro (1-para-1 nesta etapa). Substitui o
 * {@code perfis_passageiro.endereco} JSONB descontinuado. Base de filtros/análise
 * (por bairro/município).
 */
@Entity
@Table(name = "enderecos")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario", nullable = false, unique = true)
    private UUID usuarioId;

    @ManyToOne
    @JoinColumn(name = "municipio")
    private Municipio municipio;

    @Column(name = "bairro", length = 120)
    private String bairro;

    @Column(name = "logradouro", length = 180)
    private String logradouro;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "complemento", length = 120)
    private String complemento;

    @Column(name = "ponto_referencia", length = 180)
    private String pontoReferencia;

    @Column(name = "cep", length = 9)
    private String cep;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public Endereco() {
    }

    @PrePersist
    @PreUpdate
    protected void marcarAtualizacao() {
        this.atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Municipio getMunicipio() {
        return municipio;
    }

    public void setMunicipio(Municipio municipio) {
        this.municipio = municipio;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getPontoReferencia() {
        return pontoReferencia;
    }

    public void setPontoReferencia(String pontoReferencia) {
        this.pontoReferencia = pontoReferencia;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
