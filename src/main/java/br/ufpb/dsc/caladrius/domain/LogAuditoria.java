package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
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
 * Registro imutável da trilha de auditoria ("quem fez o quê, quando").
 *
 * <p>O usuário é <strong>denormalizado</strong> (id + nome, sem FK) para que a
 * trilha sobreviva à exclusão do usuário.
 */
@Entity
@Table(name = "log_auditoria")
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instante", nullable = false)
    private Instant instante;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaAuditoria categoria;

    @Column(name = "acao", nullable = false, length = 60)
    private String acao;

    @Column(name = "resultado", nullable = false, length = 20)
    private String resultado;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", length = 180)
    private String usuarioNome;

    @Column(name = "entidade", length = 60)
    private String entidade;

    @Column(name = "entidade_id", length = 80)
    private String entidadeId;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "ip", length = 60)
    private String ip;

    public LogAuditoria() {
    }

    @PrePersist
    protected void prePersist() {
        if (this.instante == null) {
            this.instante = Instant.now();
        }
        if (this.resultado == null) {
            this.resultado = "SUCESSO";
        }
    }

    public UUID getId() {
        return id;
    }

    public Instant getInstante() {
        return instante;
    }

    public void setInstante(Instant instante) {
        this.instante = instante;
    }

    public CategoriaAuditoria getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaAuditoria categoria) {
        this.categoria = categoria;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNome() {
        return usuarioNome;
    }

    public void setUsuarioNome(String usuarioNome) {
        this.usuarioNome = usuarioNome;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
