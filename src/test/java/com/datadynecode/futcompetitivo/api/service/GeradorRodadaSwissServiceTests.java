package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.GeradorRodadaSwissService;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeradorRodadaSwissServiceTests {
    private final GeradorRodadaSwissService service = new GeradorRodadaSwissService();

    @Test
    void voltaETentaOutraCombinacaoParaEvitarRevanche() {
        var participantes = participantes(4);
        Fase fase = fase();
        historico(fase, participantes, 1, 4);
        historico(fase, participantes, 2, 3);
        historico(fase, participantes, 3, 4);

        Rodada rodada = service.gerar(fase, participantes, null);

        assertEquals(Set.of(Set.of(1L, 3L), Set.of(2L, 4L)), confrontos(rodada));
        assertEquals(4, fase.getRodadas().size());
    }

    @Test
    void permiteRevancheEmUltimoCasoInvertendoUltimoMando() {
        var participantes = participantes(2);
        Fase fase = fase();
        historico(fase, participantes, 2, 1);

        Rodada rodada = service.gerar(fase, participantes, null);
        assertEquals(1L, rodada.getPartidas().getFirst().getMandante().getId());
        assertEquals(2L, rodada.getPartidas().getFirst().getVisitante().getId());
        rodada.setStatus(RodadaStatus.FINALIZADA);
        Rodada seguinte = service.gerar(fase, participantes, null);
        assertEquals(2L, seguinte.getPartidas().getFirst().getMandante().getId());
        assertEquals(1L, seguinte.getPartidas().getFirst().getVisitante().getId());
    }

    @Test
    void tentaOutroCandidatoAFolgaSeOPreferidoImpedePareamento() {
        var participantes = participantes(5);
        Fase fase = fase();
        // Só restam os confrontos 1 x 2 e 3 x 5. Dar folga ao 5 impede a rodada.
        for (int a = 1; a <= 5; a++) for (int b = a + 1; b <= 5; b++) {
            if ((a == 1 && b == 2) || (a == 3 && b == 5)) continue;
            historico(fase, participantes, a, b);
        }

        Rodada rodada = service.gerar(fase, participantes, null);

        assertEquals(4L, rodada.getFolga().getId());
        assertEquals(Set.of(Set.of(1L, 2L), Set.of(3L, 5L)), confrontos(rodada));
    }

    @Test
    void permiteRevancheComFolgaQuandoTodosJaSeEnfrentaram() {
        var participantes = participantes(3);
        Fase fase = fase();
        historico(fase, participantes, 1, 2);
        historico(fase, participantes, 1, 3);
        historico(fase, participantes, 2, 3);

        Rodada rodada = service.gerar(fase, participantes, null);
        assertEquals(4, fase.getRodadas().size());
        assertEquals(3L, rodada.getFolga().getId());
        assertEquals(2L, rodada.getPartidas().getFirst().getMandante().getId());
        assertEquals(1L, rodada.getPartidas().getFirst().getVisitante().getId());
    }

    @Test
    void minimizaRevanchesEmVezDeRepetirTodosOsConfrontos() {
        var participantes = participantes(4);
        Fase fase = fase();
        // Participante 4 já enfrentou todos: pelo menos uma revanche é inevitável.
        historico(fase, participantes, 1, 2);
        historico(fase, participantes, 1, 4);
        historico(fase, participantes, 2, 4);
        historico(fase, participantes, 3, 4);
        Set<Set<Long>> anteriores = new HashSet<>();
        fase.getRodadas().forEach(r -> anteriores.addAll(confrontos(r)));

        Rodada rodada = service.gerar(fase, participantes, null);

        assertEquals(1, confrontos(rodada).stream().filter(anteriores::contains).count());
        assertEquals(4, rodada.getPartidas().stream().flatMap(p ->
                java.util.stream.Stream.of(p.getMandante().getId(), p.getVisitante().getId())).distinct().count());
        rodada.getPartidas().stream().filter(p -> p.getMandante().getId().equals(4L)
                || p.getVisitante().getId().equals(4L)).forEach(p -> assertEquals(4L, p.getMandante().getId()));
    }

    private Fase fase() {
        Fase fase = new Fase();
        fase.setTipo(TipoFase.SWISS);
        fase.setTotalRodadas(20);
        return fase;
    }

    private List<Participante> participantes(int quantidade) {
        List<Participante> participantes = new ArrayList<>();
        for (long id = 1; id <= quantidade; id++) {
            Participante participante = new Participante();
            participante.setId(id);
            participantes.add(participante);
        }
        return participantes;
    }

    private void historico(Fase fase, List<Participante> participantes, int a, int b) {
        Rodada rodada = new Rodada(fase.getRodadas().size() + 1);
        rodada.setStatus(RodadaStatus.FINALIZADA);
        Partida partida = new Partida(participantes.get(a - 1), participantes.get(b - 1));
        partida.setStatus(StatusPartida.FINALIZADA);
        partida.setResultado(new Resultado(false, 0, 0, null, null));
        rodada.getPartidas().add(partida);
        fase.getRodadas().add(rodada);
    }

    private Set<Set<Long>> confrontos(Rodada rodada) {
        Set<Set<Long>> pares = new HashSet<>();
        rodada.getPartidas().forEach(p -> pares.add(Set.of(p.getMandante().getId(), p.getVisitante().getId())));
        return pares;
    }
}
