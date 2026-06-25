package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.ProvedorOauth;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Vínculo entre um {@link Usuario} e uma identidade de provedor externo
 * (OAuth2/OIDC), como o Google — SPEC-08.
 *
 * <p>A chave de identidade é o par <strong>({@link #provedor}, {@link #subjectId})</strong>,
 * único. O {@code subjectId} é o {@code sub} <em>imutável</em> do provedor — preferido
 * ao e-mail, que pode mudar. O {@link #emailProvedor} é informativo (auditoria/diagnóstico).
 */
@Entity
@Table(name = "identidades_oauth")
public class IdentidadeOauth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Usuário do sistema vinculado a esta identidade externa. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false, length = 20)
    private ProvedorOauth provedor;

    /** Identificador estável do usuário no provedor (claim {@code sub}). */
    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    /** E-mail no provedor no momento do vínculo (informativo). */
    @Column(name = "email_provedor", length = 160)
    private String emailProvedor;

    @Column(name = "vinculado_em", nullable = false, updatable = false)
    private Instant vinculadoEm;

    public IdentidadeOauth() {
    }

    public IdentidadeOauth(Usuario usuario, ProvedorOauth provedor, String subjectId, String emailProvedor) {
        this.usuario = usuario;
        this.provedor = provedor;
        this.subjectId = subjectId;
        this.emailProvedor = emailProvedor;
    }

    @PrePersist
    protected void prePersist() {
        if (this.vinculadoEm == null) {
            this.vinculadoEm = Instant.now();
        }
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

    public ProvedorOauth getProvedor() {
        return provedor;
    }

    public void setProvedor(ProvedorOauth provedor) {
        this.provedor = provedor;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getEmailProvedor() {
        return emailProvedor;
    }

    public void setEmailProvedor(String emailProvedor) {
        this.emailProvedor = emailProvedor;
    }

    public Instant getVinculadoEm() {
        return vinculadoEm;
    }

    public void setVinculadoEm(Instant vinculadoEm) {
        this.vinculadoEm = vinculadoEm;
    }
}
