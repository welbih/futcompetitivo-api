package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Partida;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartidaRepository extends JpaRepository<Partida, Long> {
    @Query(value = """
        SELECT p.id AS "partidaId",
               jm.id AS "mandanteJogadorId", jm.nome AS "mandanteNome",
               jm.apelido AS "mandanteApelido", jm.ativo AS "mandanteAtivo",
               jv.id AS "visitanteJogadorId", jv.nome AS "visitanteNome",
               jv.apelido AS "visitanteApelido", jv.ativo AS "visitanteAtivo",
               em.id AS "mandanteEquipeId", em.nome AS "mandanteEquipeNome",
               em.logo_url AS "mandanteEquipeLogo", em.ativo AS "mandanteEquipeAtiva",
               ev.id AS "visitanteEquipeId", ev.nome AS "visitanteEquipeNome",
               ev.logo_url AS "visitanteEquipeLogo", ev.ativo AS "visitanteEquipeAtiva",
               p.gols_mandante AS "golsMandante", p.gols_visitante AS "golsVisitante",
               p.penaltis_mandante AS "penaltisMandante", p.penaltis_visitante AS "penaltisVisitante",
               p.x1_id AS "x1Id", p.numero AS "numeroPartida",
               ca.id AS "campeonatoId", ca.edicao AS "edicao",
               o.id AS "organizacaoId", o.nome AS "organizacaoNome",
               r.id AS "rodadaId", r.numero AS "rodadaNumero",
               f.id AS "faseId", f.ordem AS "faseOrdem", f.tipo AS "faseTipo",
               f.total_rodadas AS "totalRodadas",
               EXISTS (SELECT 1 FROM confronto cp WHERE cp.fase_id = f.id
                       AND cp.origem_um_id IS NULL AND cp.participante_dois_id IS NULL) AS "preliminar",
               co.terceiro_lugar AS "terceiroLugar", co.tipo AS "confrontoTipo",
               (SELECT MIN(pi.id) FROM partida pi WHERE pi.confronto_id = co.id) AS "primeiraPartidaId"
        FROM partida p
        JOIN participante pm ON pm.id = p.mandante_id
        JOIN participante pv ON pv.id = p.visitante_id
        JOIN jogador jm ON jm.id = pm.jogador_id
        JOIN jogador jv ON jv.id = pv.jogador_id
        JOIN equipe em ON em.id = pm.equipe_id
        JOIN equipe ev ON ev.id = pv.equipe_id
        LEFT JOIN rodada r ON r.id = p.rodada_id
        LEFT JOIN fase f ON f.id = r.fase_id
        LEFT JOIN campeonato ca ON ca.id = f.campeonato_id
        LEFT JOIN organizacao o ON o.id = ca.organizacao_id
        LEFT JOIN confronto co ON co.id = p.confronto_id
        WHERE p.status = 'FINALIZADA'
          AND p.gols_mandante IS NOT NULL AND p.gols_visitante IS NOT NULL
          AND (jm.id = :jogadorId OR jv.id = :jogadorId)
          AND jm.id <> jv.id
          AND (ca.id IS NOT NULL OR p.x1_id IS NOT NULL)
          AND (:origem = 'TODOS' OR (:origem = 'CAMPEONATO' AND ca.id IS NOT NULL)
               OR (:origem = 'X1' AND p.x1_id IS NOT NULL))
        ORDER BY p.id
        """, nativeQuery = true)
    List<PartidaEstatisticasProjection> buscarHistoricoFinalizado(@Param("jogadorId") Long jogadorId,
            @Param("origem") String origem);
}
