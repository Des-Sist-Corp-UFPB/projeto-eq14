package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Etapa da máquina de estados do bot de atendimento WhatsApp (SPEC-10).
 *
 * <p>Menu (SPEC-11): solicitar viagem <strong>sob demanda</strong>
 * ({@code ESCOLHER_DESTINO} → {@code ESCOLHER_DATA} → {@code ESCOLHER_HORARIO} →
 * {@code CONDICOES} → {@code CONFIRMAR_DEMANDA}), minhas viagens (+
 * {@code CANCELAR}) e "acesso à plataforma". Número novo passa antes pelo
 * <strong>cadastro</strong> ({@code ONBOARDING_NOME} → {@code ONBOARDING_ENDERECO}
 * → {@code ONBOARDING_CPF}) e cai no {@code MENU}.
 *
 * <p>{@code ESCOLHER_LINHA}/{@code CONFIRMAR} (fluxo por linha da SPEC-10) e
 * {@code HUMANO} (RN-WPP-08) permanecem para compatibilidade de schema/uso
 * futuro, fora do menu atual. {@code ENCERRADA} marca a despedida/expiração.
 */
public enum EtapaConversa {

    INICIO,
    MENU,
    // Fluxo por linha (SPEC-10) — mantido para compatibilidade.
    ESCOLHER_LINHA,
    CONFIRMAR,
    // Comum aos fluxos.
    ESCOLHER_DATA,
    CANCELAR,
    HUMANO,
    ENCERRADA,
    // Cadastro pelo WhatsApp (SPEC-11).
    ONBOARDING_NOME,
    ONBOARDING_ENDERECO,
    ONBOARDING_CPF,
    // Solicitação sob demanda (SPEC-11).
    ESCOLHER_DESTINO,
    ESCOLHER_HORARIO,
    CONDICOES,
    CONFIRMAR_DEMANDA
}
