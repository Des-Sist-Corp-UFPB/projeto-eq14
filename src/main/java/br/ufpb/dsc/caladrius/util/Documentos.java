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
     * Extrai o telefone brasileiro de um JID do WhatsApp
     * (ex.: {@code "5583999999999@s.whatsapp.net"} → {@code "83999999999"}):
     * descarta o sufixo após o {@code @} e remove o DDI {@code 55} quando
     * presente. O resultado fica no formato de {@code usuarios.telefone}
     * (apenas dígitos, DDD + número) — SPEC-10 §4.5.
     */
    public static String telefoneDeJid(String jid) {
        if (jid == null) {
            return "";
        }
        int arroba = jid.indexOf('@');
        String digitos = apenasDigitos(arroba >= 0 ? jid.substring(0, arroba) : jid);
        // DDI 55 + DDD (2) + número (8 ou 9 dígitos) = 12 ou 13 dígitos.
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            return digitos.substring(2);
        }
        return digitos;
    }

    /**
     * Variantes de um telefone brasileiro para busca: JIDs antigos do WhatsApp
     * podem vir <em>sem</em> o 9º dígito após o DDD. Devolve o telefone como
     * veio e, quando tem 10 dígitos (DDD + 8), também a variante com o {@code 9}
     * inserido — SPEC-10 §4.5.
     */
    public static java.util.List<String> variantesTelefoneBr(String telefone) {
        String digitos = apenasDigitos(telefone);
        if (digitos.length() == 10) {
            return java.util.List.of(digitos, digitos.substring(0, 2) + "9" + digitos.substring(2));
        }
        return java.util.List.of(digitos);
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
