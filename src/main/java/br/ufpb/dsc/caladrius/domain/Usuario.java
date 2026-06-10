package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Usuário do sistema — é a raiz de identidade do CALADRIUS. Um mesmo usuário
 * pode ter vários papéis (passageiro, motorista, gerente).
 *
 * <p><strong>Identificação flexível:</strong> de acordo com o redesenho v3, o
 * usuário pode entrar com <em>e-mail OU telefone</em>. O telefone é sempre
 * obrigatório e único; o e-mail e o CPF são opcionais (mas únicos quando
 * preenchidos). A unicidade vale apenas entre usuários ativos — ver os índices
 * únicos parciais na migração {@code V2}.
 *
 * <p><strong>Soft-delete:</strong> usuários nunca são apagados fisicamente
 * (regra de domínio). "Excluir" preenche {@link #removidoEm}; as consultas
 * filtram {@code removido_em IS NULL}.
 *
 * <p><strong>Papéis:</strong> mapeados como {@code @ElementCollection} para a
 * tabela {@code papeis_usuario}. Carregados em modo EAGER porque a autenticação
 * e a montagem de tela precisam deles imediatamente.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome_completo", nullable = false, length = 160)
    private String nomeCompleto;

    /** CPF (somente dígitos). Opcional, único entre usuários ativos. */
    @Column(name = "cpf", length = 11)
    private String cpf;

    /** E-mail. Opcional, único entre ativos — pode ser usado para login. */
    @Column(name = "email", length = 160)
    private String email;

    /** Telefone (somente dígitos). Obrigatório, único entre ativos. */
    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    /** Hash BCrypt da senha. Pode ser nulo (ex.: usuário criado sem senha). */
    @Column(name = "hash_senha", length = 100)
    private String hashSenha;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusUsuario status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    /** Timestamp de exclusão lógica (soft-delete). {@code null} = usuário ativo. */
    @Column(name = "removido_em")
    private Instant removidoEm;

    /**
     * Papéis do usuário (RBAC). Mapeados para {@code papeis_usuario(usuario, papel)}.
     * As demais colunas da tabela ({@code concedido_em}, {@code concedido_por})
     * têm valores padrão no banco e não são gerenciadas por aqui.
     */
    @ElementCollection(targetClass = Papel.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "papeis_usuario", joinColumns = @JoinColumn(name = "usuario"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 30)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    public Usuario() {
    }

    /**
     * Preenche valores automáticos antes do INSERT: timestamp de criação e
     * status padrão ({@code ATIVO}) quando não informado.
     */
    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
        if (this.status == null) {
            this.status = StatusUsuario.ATIVO;
        }
    }

    // ===================== Regras de conveniência =====================

    /** {@code true} se o usuário pode autenticar (ativo e não removido). */
    public boolean isAtivo() {
        return status == StatusUsuario.ATIVO && removidoEm == null;
    }

    /** {@code true} se o usuário foi removido logicamente. */
    public boolean isRemovido() {
        return removidoEm != null;
    }

    /** {@code true} se o usuário possui o papel informado. */
    public boolean temPapel(Papel papel) {
        return papeis.contains(papel);
    }

    // ===================== Getters e Setters =====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getHashSenha() {
        return hashSenha;
    }

    public void setHashSenha(String hashSenha) {
        this.hashSenha = hashSenha;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    public void setStatus(StatusUsuario status) {
        this.status = status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getRemovidoEm() {
        return removidoEm;
    }

    public void setRemovidoEm(Instant removidoEm) {
        this.removidoEm = removidoEm;
    }

    public Set<Papel> getPapeis() {
        return papeis;
    }

    public void setPapeis(Set<Papel> papeis) {
        this.papeis = papeis;
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nome='" + nomeCompleto + "', telefone='" + telefone + "'}";
    }
}
