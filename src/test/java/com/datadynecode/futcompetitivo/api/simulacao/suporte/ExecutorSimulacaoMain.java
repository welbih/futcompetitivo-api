package com.datadynecode.futcompetitivo.api.simulacao.suporte;

import com.datadynecode.futcompetitivo.api.FutcompetitivoApiApplication;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class ExecutorSimulacaoMain {
    private ExecutorSimulacaoMain() { }

    public static void executar(CenarioSimulacao cenario) {
        try (var contexto = new SpringApplicationBuilder(FutcompetitivoApiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run("--spring.datasource.url=jdbc:h2:mem:simulacao_" + UUID.randomUUID(),
                        "--spring.jpa.show-sql=false", "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN")) {
            var simulacao = contexto.getAutowireCapableBeanFactory().createBean(SimuladorCampeonato.class);
            var transacao = new TransactionTemplate(contexto.getBean(PlatformTransactionManager.class));
            transacao.executeWithoutResult(status -> {
                try {
                    simulacao.executar(cenario);
                } finally {
                    status.setRollbackOnly();
                }
            });
        }
    }
}
