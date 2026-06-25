package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;

import java.util.List;

/**
 * Uma coluna da grade semanal da visão do passageiro (SPEC-09): as linhas
 * ativas que operam num determinado dia da semana. Usado na aba "Linhas
 * disponíveis" para o panorama no estilo de uma grade de horários.
 */
public record GradeDiaSemana(DiaSemana dia, List<LinhaProgramada> linhas) {
}
