package com.datadynecode.futcompetitivo.api.service.classificacao;

import com.datadynecode.futcompetitivo.api.entity.ClassificacaoParticipante;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CriterioSorteio implements CriterioDesempate {
    private final Map<Long, UUID> chaves = new HashMap<>();

    // A chave permanece fixa durante a ordenação, respeitando o contrato do comparador.
    @Override
    public int comparar(ClassificacaoParticipante a, ClassificacaoParticipante b) {
        UUID chaveA = chaves.computeIfAbsent(a.getParticipante().getId(), id -> UUID.randomUUID());
        UUID chaveB = chaves.computeIfAbsent(b.getParticipante().getId(), id -> UUID.randomUUID());
        return chaveA.compareTo(chaveB);
    }
}
