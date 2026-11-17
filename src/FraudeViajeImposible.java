import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class FraudeViajeImposible {
    public static void main(String[] args) {
        // Inicializa el motor de ejecución centralizado.
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasViajeImposible_jNtcc");

        // Variables de estado en el Store gestionadas por JaCoP.
        IntVar montoTx = utcclang.Store.getStore().creavarsRetor("montoTx", 0, 10000);
        IntVar paisTx = utcclang.Store.getStore().creavarsRetor("paisTx", 0, 1); // 0=Local, 1=Extranjero
        IntVar velocidad = utcclang.Store.getStore().creavarsRetor("velocidad", 0, 1); // 0=Normal, 1=Imposible
        IntVar riesgo = utcclang.Store.getStore().creavarsRetor("riesgo", 0, 100);
        IntVar bloqueada = utcclang.Store.getStore().creavarsRetor("bloqueada", 0, 1);

        // REGLA 1: Patrón de Viaje Imposible (Asíncrono)
        // La instrucción Change actúa como una celda de memoria que suma 60 puntos de riesgo al instante siguiente[cite: 1, 2].
        Procesos reglaVelocidad = new Bang(
                new Ask(new And(new XeqC(paisTx, 1), new XeqC(velocidad, 1)),
                        new Change(riesgo, Operation._INCRE, 60)
                )
        );

        // REGLA 2: Anomalía de Monto
        Procesos reglaMonto = new Bang(
                new Ask(new XgtC(montoTx, 500),
                        new Change(riesgo, Operation._INCRE, 20)
                )
        );

        // REGLA 3: Motor de Bloqueo Concurrente
        // El operador Choice pone a competir hilos reales por un CountDownLatch (Lock)[cite: 1, 2].
        Procesos motorDecision = new Bang(
                new Ask(new XgtC(riesgo, 70),
                        new Choice(
                                new AskChoice(new XeqC(bloqueada, 0), new Tell(new XeqC(bloqueada, 1))),
                                new AskChoice(new XeqC(bloqueada, 0), new Change(riesgo, Operation._DECRE, 30))
                        )
                )
        );

        // ENTORNO: El Atacante y el Cliente
        // 1. Compra local perfectamente normal en la Unidad 1.
        Procesos compraLocal = new Next(
                new Par(new Tell(new XeqC(paisTx, 0)), new Tell(new XeqC(montoTx, 15)))
        );

        // 2. Compra en el extranjero, monto alto, velocidad imposible.
        // El operador Star inyecta el ataque en una unidad aleatoria entre 2 y 5[cite: 1, 2].
        Procesos ataqueExtranjero = new Star(
                new Par(
                        new Tell(new XeqC(paisTx, 1)),
                        new Tell(new XeqC(velocidad, 1)),
                        new Tell(new XeqC(montoTx, 1500))
                ), 2, 5
        );

        // Orquestación del paralelismo puro.
        Procesos ecosistema = new Par(reglaVelocidad, reglaMonto, motorDecision, compraLocal, ataqueExtranjero);
        simulador.addProce(ecosistema);
        simulador.setTimeUnit(10);

        System.out.println("Iniciando Detección de Viaje Imposible...");
        simulador.run();
    }
}