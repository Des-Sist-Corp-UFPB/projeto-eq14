package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.StatusOrganizacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Secretaria cliente — a <strong>fronteira de isolamento</strong> do modelo
 * multi-tenant (SPEC-PLT-02 / ADR-21).
 *
 * <p>O {@link #slug} identifica o tenant: vira subdomínio e, na fase 2, o nome do
 * schema de dados. Enquanto {@link #schemaDados} for nulo, a organização é servida
 * pelo <em>schema legado</em> — que é o estado de todo o sistema hoje.
 */
@Entity
@Table(name = "organizacoes")
public class Organizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    /** CNPJ da secretaria; opcional enquanto a organização está em rascunho. */
    @Column(name = "documento", length = 18)
    private String documento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusOrganizacao status = StatusOrganizacao.RASCUNHO;

    /** Schema de dados do tenant (fase 2). Nulo ⇒ servida pelo schema legado. */
    @Column(name = "schema_dados", length = 63)
    private String schemaDados;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public Organizacao() {
    }

    public Organizacao(String nome, String slug) {
        this.nome = nome;
        this.slug = slug;
    }

    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
        if (this.status == null) {
            this.status = StatusOrganizacao.RASCUNHO;
        }
    }

    /** {@code true} se a secretaria já tem schema próprio (provisionada — fase 2). */
    public boolean provisionada() {
        return schemaDados != null;
    }

    // ===================== Getters e Setters =====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public StatusOrganizacao getStatus() {
        return status;
    }

    public void setStatus(StatusOrganizacao status) {
        this.status = status;
    }

    public String getSchemaDados() {
        return schemaDados;
    }

    public void setSchemaDados(String schemaDados) {
        this.schemaDados = schemaDados;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
