package br.ufpb.dsc.caladrius.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Dados de criação de uma {@code Viagem}.
 *
 * <p>As referências (veículo, motorista, cidade de destino) chegam como UUID
 * vindos dos selects do formulário; o serviço resolve as entidades. As anotações
 * {@code @DateTimeFormat} garantem o parsing dos inputs HTML {@code type="date"}
 * (ISO {@code AAAA-MM-DD}) e {@code type="time"} (ISO {@code HH:MM}).
 */
public record ViagemForm(

        @NotNull(message = "Selecione o veículo")
        UUID veiculoId,

        @NotNull(message = "Selecione o motorista")
        UUID motoristaId,

        @NotNull(message = "Selecione a cidade de destino")
        UUID cidadeDestinoId,

        @NotNull(message = "Informe a data da viagem")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataViagem,

        @NotNull(message = "Informe o horário de saída")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime horarioSaida,

        @NotNull(message = "Informe o horário de chegada")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime horarioChegada

) {
}
