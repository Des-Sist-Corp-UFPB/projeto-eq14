package br.ufpb.dsc.caladrius.util;

/**
 * Utilitários para normalização e validação de identificadores (telefone, CPF)
 * e detecção do formato de login (e-mail vs telefone).
 *
 * <p>Centralizado aqui para ser reutilizado pelo login (detectar e-mail/telefone,
 * normalizar telefone) e pelo cadastro (validar CPF, normalizar telefone/CPF) —
 * evitando duplicação de regra.
 */
public final class Documentos {

    private Documentos() {
        // utilitário estático — não instanciar
    }

    /** Remove tudo que não for dígito (ex.: "(83) 99999-9999" → "83999999999"). */
    public static String apenasDigitos(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("\\D", "");
    }

    /**
     * Heurística simples: um identificador de login é tratado como e-mail quando
     * contém "@". Caso contrário, é tratado como telefone. Espelha o comportamento
     * "Detectamos automaticamente o formato" da tela de login.
     */
    public static boolean pareceEmail(String identificador) {
        return identificador != null && identificador.contains("@");
    }

    /**
     * Valida um CPF brasileiro: 11 dígitos, não todos iguais e com os dois
     * dígitos verificadores corretos. Aceita CPF com ou sem formatação.
     *
     * @param cpf CPF (com ou sem máscara)
     * @return {@code true} se for um CPF válido
     */
    public static boolean cpfValido(String cpf) {
        String c = apenasDigitos(cpf);
        if (c.length() != 11) {
            return false;
        }
        // Rejeita sequências de dígitos iguais (00000000000, 11111111111, ...).
        boolean todosIguais = true;
        for (int i = 1; i < 11; i++) {
            if (c.charAt(i) != c.charAt(0)) {
                todosIguais = false;
                break;
            }
        }
        if (todosIguais) {
            return false;
        }
        // Confere os dois dígitos verificadores.
        for (int j = 9; j < 11; j++) {
            int soma = 0;
            for (int i = 0; i < j; i++) {
                soma += (c.charAt(i) - '0') * (j + 1 - i);
            }
            int resto = (soma * 10) % 11;
            if (resto == 10) {
                resto = 0;
            }
            if (resto != (c.charAt(j) - '0')) {
                return false;
            }
        }
        return true;
    }
}
