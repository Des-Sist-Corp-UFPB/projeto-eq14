package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.StatusVeiculo;
import br.ufpb.dsc.caladrius.domain.enums.TipoVeiculo;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Veículo da frota usada nas viagens.
 *
 * <p>Mapeado para a tabela {@code veiculos}. Usa <strong>soft-delete</strong>:
 * ao "excluir", preenchemos {@link #removidoEm} em vez de apagar a linha, e as
 * consultas filtram {@code removido_em IS NULL}. A {@code placa} é única apenas
 * entre veículos ativos (índice único parcial — ver migração {@code V2}).
 *
 * <p>A {@code capacidade} (assentos) governa quantos passageiros cabem em uma
 * viagem — é o insumo central da futura alocação automática.
 */
@Entity
@Table(name = "veiculos")
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "A placa é obrigatória")
    @Size(min = 7, max = 8, message = "A placa deve ter 7 caracteres (ex.: ABC1D23)")
    @Column(name = "placa", nullable = false, length = 8)
    private String placa;

    @NotBlank(message = "A marca é obrigatória")
    @Size(max = 60, message = "A marca deve ter no máximo 60 caracteres")
    @Column(name = "marca", nullable = false, length = 60)
    private String marca;

    @NotBlank(message = "O modelo é obrigatório")
    @Size(max = 60, message = "O modelo deve ter no máximo 60 caracteres")
    @Column(name = "modelo", nullable = false, length = 60)
    private String modelo;

    @NotNull(message = "O ano é obrigatório")
    @Min(value = 1950, message = "Ano inválido")
    @Max(value = 2100, message = "Ano inválido")
    @Column(name = "ano", nullable = false)
    private Integer ano;

    @NotNull(message = "O tipo é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoVeiculo tipo;

    @NotNull(message = "A capacidade é obrigatória")
    @Min(value = 1, message = "A capacidade deve ser ao menos 1")
    @Max(value = 100, message = "Capacidade máxima de 100 assentos")
    @Column(name = "capacidade", nullable = false)
    private Integer capacidade;

    @Column(name = "possui_acessibilidade", nullable = false)
    private boolean possuiAcessibilidade;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusVeiculo status;

    /** Timestamp de exclusão lógica (soft-delete). {@code null} = veículo ativo. */
    @Column(name = "removido_em")
    private Instant removidoEm;

    public Veiculo() {
    }

    public Veiculo(String placa, String marca, String modelo, Integer ano,
                   TipoVeiculo tipo, Integer capacidade, boolean possuiAcessibilidade) {
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
        this.tipo = tipo;
        this.capacidade = capacidade;
        this.possuiAcessibilidade = possuiAcessibilidade;
        this.status = StatusVeiculo.DISPONIVEL;
    }

    /**
     * Define o status padrão antes do INSERT, caso não tenha sido informado.
     */
    @PrePersist
    protected void prePersist() {
        if (this.status == null) {
            this.status = StatusVeiculo.DISPONIVEL;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public TipoVeiculo getTipo() {
        return tipo;
    }

    public void setTipo(TipoVeiculo tipo) {
        this.tipo = tipo;
    }

    public Integer getCapacidade() {
        return capacidade;
    }

    public void setCapacidade(Integer capacidade) {
        this.capacidade = capacidade;
    }

    public boolean isPossuiAcessibilidade() {
        return possuiAcessibilidade;
    }

    public void setPossuiAcessibilidade(boolean possuiAcessibilidade) {
        this.possuiAcessibilidade = possuiAcessibilidade;
    }

    public StatusVeiculo getStatus() {
        return status;
    }

    public void setStatus(StatusVeiculo status) {
        this.status = status;
    }

    public Instant getRemovidoEm() {
        return removidoEm;
    }

    public void setRemovidoEm(Instant removidoEm) {
        this.removidoEm = removidoEm;
    }

    @Override
    public String toString() {
        return "Veiculo{id=" + id + ", placa='" + placa + "'}";
    }
}
