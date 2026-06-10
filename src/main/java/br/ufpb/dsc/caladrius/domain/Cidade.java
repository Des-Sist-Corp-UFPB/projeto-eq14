package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cidade do sistema — pode ser a {@code ORIGEM} (município de partida dos
 * pacientes) ou a {@code METROPOLITANA} (destino das consultas de saúde).
 *
 * <p>Mapeada para a tabela {@code cidades} (ver migração {@code V2}). A chave
 * primária é um {@link UUID} gerado pelo Hibernate ({@code GenerationType.UUID}).
 */
@Entity
@Table(name = "cidades")
public class Cidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "O nome da cidade é obrigatório")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotBlank(message = "A UF é obrigatória")
    @Size(min = 2, max = 2, message = "A UF deve ter 2 letras (ex.: PB)")
    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @NotNull(message = "O tipo da cidade é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoCidade tipo;

    public Cidade() {
    }

    public Cidade(String nome, String uf, TipoCidade tipo) {
        this.nome = nome;
        this.uf = uf;
        this.tipo = tipo;
    }

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

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public TipoCidade getTipo() {
        return tipo;
    }

    public void setTipo(TipoCidade tipo) {
        this.tipo = tipo;
    }

    @Override
    public String toString() {
        return "Cidade{id=" + id + ", nome='" + nome + "/" + uf + "'}";
    }
}
