package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;

import java.time.LocalDate;
import java.util.List;

/**
 * Modelo de visão do painel semanal (SPEC-06, FR-VIA-09): para cada dia da
 * semana, as linhas que operam e a viagem já designada (se houver).
 */
public record PainelSemana(LocalDate inicio, LocalDate fim, List<DiaPainel> dias) {

    /** Um dia da grade (Dom→Sáb) com seus itens. */
    public record DiaPainel(LocalDate data, DiaSemana dia, List<ItemDia> itens) {
    }

    /** Uma linha do dia; {@code viagem} nula = ainda não designada. */
    public record ItemDia(LinhaProgramada linha, Viagem viagem) {
        public boolean designada() {
            return viagem != null;
        }
    }
}
