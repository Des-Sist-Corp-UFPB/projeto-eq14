package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.DirecaoMensagem;
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
 * Registro de uma mensagem WhatsApp enviada ou recebida (SPEC-10, RN-WPP-10).
 *
 * <p>Mapeada para {@code mensagens_whatsapp} (migração V12). Alimenta o painel
 * {@code /whatsapp} do gerente e garante a idempotência do webhook: mensagens
 * recebidas guardam o {@link #idProvedor} sob índice único parcial — evento
 * repetido da Evolution não é reprocessado (RN-WPP-03).
 */
@Entity
@Table(name = "mensagens_whatsapp")
public class MensagemWhatsapp {

    /** Limite do conteúdo persistido — textos maiores são truncados (RN-WPP-10). */
    public static final int LIMITE_CONTEUDO = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "direcao", nullable = false, length = 10)
    private DirecaoMensagem direcao;

    /** Telefone normalizado (apenas dígitos, sem DDI 55). */
    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    @Column(name = "conteudo", nullable = false, length = LIMITE_CONTEUDO)
    private String conteudo;

    /** Id da mensagem no provedor (Evolution) — só as recebidas o possuem. */
    @Column(name = "id_provedor", length = 80)
    private String idProvedor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public MensagemWhatsapp() {
    }

    public MensagemWhatsapp(DirecaoMensagem direcao, String telefone, String conteudo, String idProvedor) {
        this.direcao = direcao;
        this.telefone = telefone;
        setConteudo(conteudo);
        this.idProvedor = idProvedor;
    }

    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public DirecaoMensagem getDirecao() {
        return direcao;
    }

    public void setDirecao(DirecaoMensagem direcao) {
        this.direcao = direcao;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getConteudo() {
        return conteudo;
    }

    /** Guarda o conteúdo truncado no limite da coluna (RN-WPP-10). */
    public void setConteudo(String conteudo) {
        if (conteudo != null && conteudo.length() > LIMITE_CONTEUDO) {
            this.conteudo = conteudo.substring(0, LIMITE_CONTEUDO);
        } else {
            this.conteudo = conteudo;
        }
    }

    public String getIdProvedor() {
        return idProvedor;
    }

    public void setIdProvedor(String idProvedor) {
        this.idProvedor = idProvedor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
