package br.ufpb.dsc.caladrius.domain.enums;

import java.time.DayOfWeek;

/**
 * Dia da semana em que uma {@code LinhaProgramada} opera. Ordem de domingo a
 * sábado (espelha o painel semanal).
 */
public enum DiaSemana {

    DOMINGO("Domingo", DayOfWeek.SUNDAY),
    SEGUNDA("Segunda", DayOfWeek.MONDAY),
    TERCA("Terça", DayOfWeek.TUESDAY),
    QUARTA("Quarta", DayOfWeek.WEDNESDAY),
    QUINTA("Quinta", DayOfWeek.THURSDAY),
    SEXTA("Sexta", DayOfWeek.FRIDAY),
    SABADO("Sábado", DayOfWeek.SATURDAY);

    private final String rotulo;
    private final DayOfWeek diaJava;

    DiaSemana(String rotulo, DayOfWeek diaJava) {
        this.rotulo = rotulo;
        this.diaJava = diaJava;
    }

    public String getRotulo() {
        return rotulo;
    }

    public DayOfWeek getDiaJava() {
        return diaJava;
    }

    /** Converte um {@link DayOfWeek} (de um {@code LocalDate}) para o dia correspondente. */
    public static DiaSemana de(DayOfWeek diaJava) {
        for (DiaSemana d : values()) {
            if (d.diaJava == diaJava) {
                return d;
            }
        }
        throw new IllegalArgumentException("Dia inválido: " + diaJava);
    }
}
