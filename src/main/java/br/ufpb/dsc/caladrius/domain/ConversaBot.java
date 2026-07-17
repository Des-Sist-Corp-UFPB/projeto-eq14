package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.EtapaConversa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Estado da conversa do bot de atendimento WhatsApp com um número (SPEC-10).
 *
 * <p>Mapeada para {@code conversas_bot} (migração V12) — uma conversa por
 * telefone (normalizado, sem DDI). Persistir a máquina de estados permite que
 * o atendimento sobreviva a redeploys. O contexto do fluxo de solicitação em
 * andamento (linha escolhida, data desejada) fica em colunas tipadas e é limpo
 * ao concluir ou expirar (RN-WPP-07).
 */
@Entity
@Table(name = "conversas_bot")
public class ConversaBot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Telefone normalizado (apenas dígitos, sem DDI 55) — casa com {@code usuarios.telefone}. */
    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    /** Usuário identificado pelo telefone (RN-WPP-05); nulo se o número for desconhecido. */
    @ManyToOne
    @JoinColumn(name = "usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false, length = 20)
    private EtapaConversa etapa = EtapaConversa.INICIO;

    /** Linha escolhida no fluxo de solicitação (contexto temporário). */
    @ManyToOne
    @JoinColumn(name = "linha_programada")
    private LinhaProgramada linha;

    /** Data desejada no fluxo de solicitação (contexto temporário). */
    @Column(name = "data_desejada")
    private LocalDate dataDesejada;

    /** Destino escolhido no fluxo sob demanda (contexto temporário — SPEC-11). */
    @ManyToOne
    @JoinColumn(name = "cidade_destino")
    private Cidade cidadeDestino;

    /** Horário desejado no fluxo sob demanda (contexto temporário — SPEC-11). */
    @Column(name = "horario_desejado")
    private LocalTime horarioDesejado;

    /** Condições informadas no fluxo sob demanda (contexto temporário — SPEC-11). */
    @Column(name = "condicoes", length = 280)
    private String condicoes;

    /** Rascunho do cadastro (onboarding) enquanto o bot coleta os dados — SPEC-11. */
    @Column(name = "cad_nome", length = 160)
    private String cadNome;

    @Column(name = "cad_endereco", length = 280)
    private String cadEndereco;

    @Column(name = "cad_cpf", length = 14)
    private String cadCpf;

    /** Última interação — base da expiração por inatividade (RN-WPP-07/08). */
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public ConversaBot() {
    }

    @PrePersist
    @PreUpdate
    protected void tocarAtualizadoEm() {
        this.atualizadoEm = Instant.now();
    }

    /** Limpa o contexto dos fluxos (solicitação por linha, sob demanda e cadastro). */
    public void limparContexto() {
        this.linha = null;
        this.dataDesejada = null;
        this.cidadeDestino = null;
        this.horarioDesejado = null;
        this.condicoes = null;
        this.cadNome = null;
        this.cadEndereco = null;
        this.cadCpf = null;
    }

    public UUID getId() {
        return id;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public EtapaConversa getEtapa() {
        return etapa;
    }

    public void setEtapa(EtapaConversa etapa) {
        this.etapa = etapa;
    }

    public LinhaProgramada getLinha() {
        return linha;
    }

    public void setLinha(LinhaProgramada linha) {
        this.linha = linha;
    }

    public LocalDate getDataDesejada() {
        return dataDesejada;
    }

    public void setDataDesejada(LocalDate dataDesejada) {
        this.dataDesejada = dataDesejada;
    }

    public Cidade getCidadeDestino() {
        return cidadeDestino;
    }

    public void setCidadeDestino(Cidade cidadeDestino) {
        this.cidadeDestino = cidadeDestino;
    }

    public LocalTime getHorarioDesejado() {
        return horarioDesejado;
    }

    public void setHorarioDesejado(LocalTime horarioDesejado) {
        this.horarioDesejado = horarioDesejado;
    }

    public String getCondicoes() {
        return condicoes;
    }

    public void setCondicoes(String condicoes) {
        this.condicoes = condicoes;
    }

    public String getCadNome() {
        return cadNome;
    }

    public void setCadNome(String cadNome) {
        this.cadNome = cadNome;
    }

    public String getCadEndereco() {
        return cadEndereco;
    }

    public void setCadEndereco(String cadEndereco) {
        this.cadEndereco = cadEndereco;
    }

    public String getCadCpf() {
        return cadCpf;
    }

    public void setCadCpf(String cadCpf) {
        this.cadCpf = cadCpf;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(Instant atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
