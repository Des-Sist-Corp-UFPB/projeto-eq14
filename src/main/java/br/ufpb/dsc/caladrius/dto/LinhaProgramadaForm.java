package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dados de uma linha programada (template recorrente — SPEC-06). A origem é
 * opcional (default = cidade-sede); os dias da semana definem a recorrência.
 */
public record LinhaProgramadaForm(

        UUID cidadeOrigemId,

        @NotNull(message = "Selecione a cidade de destino")
        UUID cidadeDestinoId,

        @NotNull(message = "Informe o horário de saída")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime horarioSaida,

        @NotNull(message = "Informe o horário de chegada")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime horarioChegada,

        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime horarioRetorno,

        Set<DiaSemana> dias,

        Boolean ativa

) {
    public Set<DiaSemana> diasOuVazio() {
        return dias == null ? new LinkedHashSet<>() : dias;
    }

    public boolean ativaOuPadrao() {
        return ativa == null || ativa;
    }
}
