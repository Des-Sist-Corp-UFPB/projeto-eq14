package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusVinculo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Pertencimento de uma pessoa a uma secretaria, com um papel (SPEC-PLT-02 §4, ADR-22).
 *
 * <p>É a evolução de {@code papeis_usuario}: o papel deixa de valer globalmente e passa
 * a valer <strong>dentro de uma organização</strong>. Uma pessoa pode ter vários vínculos
 * — motorista em uma secretaria e passageira em outra — com uma única credencial, e
 * escolhe por qual entra a cada sessão (`/entrar/onde`).
 *
 * <p><strong>RN-MT-08:</strong> nasce {@link StatusVinculo#PENDENTE}; quem ativa é um
 * gestor da organização.
 */
@Entity
@Table(name = "vinculos")
public class Vinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organizacao", nullable = false)
    private Organizacao organizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 30)
    private Papel papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusVinculo status = StatusVinculo.PENDENTE;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovado_por")
    private Usuario aprovadoPor;

    public Vinculo() {
    }

    public Vinculo(Usuario usuario, Organizacao organizacao, Papel papel) {
        this.usuario = usuario;
        this.organizacao = organizacao;
        this.papel = papel;
    }

    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
        if (this.status == null) {
            this.status = StatusVinculo.PENDENTE;
        }
    }

    /** Ativa o vínculo, registrando quem aprovou e quando. */
    public void aprovar(Usuario aprovador) {
        this.status = StatusVinculo.ATIVO;
        this.aprovadoEm = Instant.now();
        this.aprovadoPor = aprovador;
    }

    /** Encerra o acesso — o registro permanece (RN-MT-16). */
    public void revogar() {
        this.status = StatusVinculo.REVOGADO;
    }

    public boolean isAtivo() {
        return status == StatusVinculo.ATIVO;
    }

    // ===================== Getters e Setters =====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Organizacao getOrganizacao() {
        return organizacao;
    }

    public void setOrganizacao(Organizacao organizacao) {
        this.organizacao = organizacao;
    }

    public Papel getPapel() {
        return papel;
    }

    public void setPapel(Papel papel) {
        this.papel = papel;
    }

    public StatusVinculo getStatus() {
        return status;
    }

    public void setStatus(StatusVinculo status) {
        this.status = status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAprovadoEm() {
        return aprovadoEm;
    }

    public Usuario getAprovadoPor() {
        return aprovadoPor;
    }
}
