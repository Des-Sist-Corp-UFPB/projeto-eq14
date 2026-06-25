package br.ufpb.dsc.caladrius.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários de {@link Documentos}: normalização de telefone, detecção de
 * e-mail e validação de CPF (regra de negócio reutilizada no login e no cadastro).
 */
@DisplayName("Documentos — Testes Unitários")
class DocumentosTest {

    @Test
    @DisplayName("apenasDigitos: remove máscara e trata nulo como vazio")
    void apenasDigitos() {
        assertThat(Documentos.apenasDigitos("(83) 99999-9999")).isEqualTo("83999999999");
        assertThat(Documentos.apenasDigitos("123.456.789-09")).isEqualTo("12345678909");
        assertThat(Documentos.apenasDigitos(null)).isEmpty();
        assertThat(Documentos.apenasDigitos("abc")).isEmpty();
    }

    @Test
    @DisplayName("pareceEmail: true só quando contém @")
    void pareceEmail() {
        assertThat(Documentos.pareceEmail("user@dominio.com")).isTrue();
        assertThat(Documentos.pareceEmail("83999999999")).isFalse();
        assertThat(Documentos.pareceEmail(null)).isFalse();
    }

    @Test
    @DisplayName("cpfValido: aceita CPF válido com ou sem máscara")
    void cpfValido_aceitaValido() {
        assertThat(Documentos.cpfValido("52998224725")).isTrue();
        assertThat(Documentos.cpfValido("529.982.247-25")).isTrue();
    }

    @Test
    @DisplayName("cpfValido: rejeita tamanho errado, dígitos iguais e verificador inválido")
    void cpfValido_rejeitaInvalidos() {
        assertThat(Documentos.cpfValido("123")).isFalse();            // tamanho
        assertThat(Documentos.cpfValido("11111111111")).isFalse();   // todos iguais
        assertThat(Documentos.cpfValido("52998224724")).isFalse();   // dígito verificador errado
        assertThat(Documentos.cpfValido(null)).isFalse();
    }
}
