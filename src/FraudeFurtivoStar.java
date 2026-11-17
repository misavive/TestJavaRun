import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class FraudeFurtivoStar {

    public static void main(String[] args) {
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasFraude_Star_jNtcc");

        // VARIABLES DE STORE
        IntVar montoTx   = utcclang.Store.getStore().creavarsRetor("montoTx", 0, 10000);
        IntVar riesgo    = utcclang.Store.getStore().creavarsRetor("riesgo", 0, 1000);
        IntVar bloqueada = utcclang.Store.getStore().creavarsRetor("bloqueada", 0, 1);

        // Inicialización monótona del estado
        Procesos initEstado = new Par(
                new Change(riesgo, 0),
                new Change(bloqueada, 0)
        );

        // REGLAS DEL BANCO (Permanentes)
        Procesos reglaMontoAlto = new Bang(
                new Ask(new XgtC(montoTx, 800),
                        new Change(riesgo, Operation._INCRE, 40)
                )
        );

        Procesos decaimientoRiesgo = new Bang(
                new Unless(new XgtC(montoTx, 0),
                        new Change(riesgo, Operation._DECRE, 5)
                )
        );

        Procesos motorDecision = new Bang(
                new Ask(new XgtC(riesgo, 70),
                        new Choice(
                                new AskChoice(new XeqC(bloqueada, 0), new Change(bloqueada, 1)),
                                new AskChoice(new XeqC(bloqueada, 0), new Change(riesgo, Operation._DECRE, 30))
                        )
                )
        );

        // EL ATACANTE EVASIVO (No-Determinismo Temporal)
        // El atacante programa tres impactos, pero la máquina virtual decidirá
        // aleatoriamente en qué instante exacto de la ventana temporal detonarlos.
        Procesos ataqueFurtivo = new Par(
                new Star(new Tell(new XeqC(montoTx, 900)), 2, 5),   // Impacto 1: Entre t=2 y t=5
                new Star(new Tell(new XeqC(montoTx, 900)), 6, 9),   // Impacto 2: Entre t=6 y t=9
                new Star(new Tell(new XeqC(montoTx, 900)), 12, 20)  // Impacto 3: Entre t=12 y t=20
        );

        // Orquestación
        Procesos sistemaBancario = new Par(
                initEstado, reglaMontoAlto, decaimientoRiesgo, motorDecision, ataqueFurtivo
        );

        simulador.addProce(sistemaBancario);

        // Configuramos 15 unidades. Como el Impacto 3 tiene un límite superior de 20,
        // existe la posibilidad de que este último ataque nunca se materialice en la simulación.
        simulador.setTimeUnit(15);

        System.out.println("Iniciando Simulador de Fraude (Eventualidad Star) - jNtcc...");
        simulador.run();
    }
}