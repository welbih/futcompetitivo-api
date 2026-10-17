package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Resultado {
    private boolean golDeOuro;
    private Integer golsMandante;
    private Integer golsVisitante;
    private Integer penaltisMandante;
    private Integer penaltisVisitante;
}
