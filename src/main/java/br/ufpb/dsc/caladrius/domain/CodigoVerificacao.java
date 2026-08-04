package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
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
 * Código OTP de verificação (SPEC-ACE-03). Guarda apenas o <strong>hash</strong> do
 * valor cru (nunca os 6 dígitos), com expiração curta, uso único e contador de
 * tentativas para o <em>lockout</em> (RN-VER-05). Espelha a política do
 * {@link TokenAtivacao}, acrescentando o que o OTP exige: finalidade, canal e
 * tentativas.
 */
@Entity
@Table(name = "codigos_verificacao")
public class CodigoVerificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Usuário a quem o código pertence. */
    @Column(name = "usuario", nullable = false)
    private UUID usuarioId;

    /** SHA-256 (hex) do código cru — só o hash é persistido. */
    @Column(name = "codigo_hash", nullable = false, length = 120)
    private String codigoHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalidade", nullable = false, length = 30)
    private FinalidadeCodigo finalidade;

    /** Canal por onde o código foi enviado (WHATSAPP ou EMAIL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalTipo canal;

    /** Tentativas de validação já feitas (para o lockout — RN-VER-05). */
    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    /** IP de origem da emissão (throttle/auditoria — RN-VER-06). */
    @Column(name = "criado_ip", length = 45)
    private String criadoIp;

    public CodigoVerificacao() {
    }

    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
    }

    /** Código válido: não usado, dentro da validade e abaixo do teto de tentativas. */
    public boolean valido(int maxTentativas) {
        return usadoEm == null
                && tentativas < maxTentativas
                && expiraEm != null
                && Instant.now().isBefore(expiraEm);
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

    public String getCodigoHash() {
        return codigoHash;
    }

    public void setCodigoHash(String codigoHash) {
        this.codigoHash = codigoHash;
    }

    public FinalidadeCodigo getFinalidade() {
        return finalidade;
    }

    public void setFinalidade(FinalidadeCodigo finalidade) {
        this.finalidade = finalidade;
    }

    public CanalTipo getCanal() {
        return canal;
    }

    public void setCanal(CanalTipo canal) {
        this.canal = canal;
    }

    public int getTentativas() {
        return tentativas;
    }

    public void setTentativas(int tentativas) {
        this.tentativas = tentativas;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Instant expiraEm) {
        this.expiraEm = expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(Instant usadoEm) {
        this.usadoEm = usadoEm;
    }

    public String getCriadoIp() {
        return criadoIp;
    }

    public void setCriadoIp(String criadoIp) {
        this.criadoIp = criadoIp;
    }
}
