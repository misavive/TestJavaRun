import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class SimuladorFraude {

    public static void main(String[] args) {

        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasFraude_jNtcc.txt");

        // Variables de Estado (Store)
        // montoTx: El valor de la transacción en la unidad de tiempo actual.
        // riesgo: Acumulador dinámico (0-100) que modela el perfil de riesgo.
        // bloqueada: Estado binario de la tarjeta (0=Activa, 1=Bloqueada).
        IntVar montoTx   = utcclang.Store.getStore().creavarsRetor("montoTx", 0, 10000);
        IntVar riesgo    = utcclang.Store.getStore().creavarsRetor("riesgo", 0, 100);
        IntVar bloqueada = utcclang.Store.getStore().creavarsRetor("bloqueada", 0, 1);

        // REGLA 1: Acumulador de Riesgo Temporal (Usando Change)
        // Si hay una transacción superior a $800, el riesgo sube 40 puntos en el siguiente instante.
        Procesos reglaMontoAlto = new Bang(
                new Ask(new XgtC(montoTx, 800),
                        new Change(riesgo, Operation._INCRE, 40)
                )
        );

        // REGLA 2: Decaimiento de Riesgo por Ausencia (Usando Unless)
        // Si en la unidad de tiempo actual NO se puede deducir que hay transacciones (monto > 0),
        // el sistema premia el comportamiento bajando el riesgo en 5 puntos[cite: 3].
        Procesos decaimientoRiesgo = new Bang(
                new Unless(new XgtC(montoTx, 0),
                        new Change(riesgo, Operation._DECRE, 5)
                )
        );

        // REGLA 3: Motor de Decisión Concurrente (Usando Choice)
        // Si el riesgo supera el umbral crítico de 70, compiten dos hilos Java reales.
        // Opción A: Bloqueo preventivo inmediato.
        // Opción B: Disminución de riesgo asumiendo que un sistema paralelo (ej. SMS) verificó al usuario.
        Procesos motorDecision = new Bang(
                new Ask(new XgtC(riesgo, 70),
                        new Choice(
                                new AskChoice(new XeqC(bloqueada, 0), new Tell(new XeqC(bloqueada, 1))),
                                new AskChoice(new XeqC(bloqueada, 0), new Change(riesgo, Operation._DECRE, 30))
                        )
                )
        );

        // ENTORNO (Asíncrono): El Atacante (Usando Star)
        // El operador Star garantiza que el evento ocurrirá, pero el instante exacto es aleatorio.
        // 1. Transacción de prueba ($50) ocurre en algún momento entre la unidad 1 y 3.
        // 2. Transacción de fraude ($900) ocurre entre la unidad 4 y 8.
        //Procesos ataqueTest = new Star(new Tell(new XeqC(montoTx, 50)), 1, 3);
        //Procesos ataqueFuerte = new Star(new Tell(new XeqC(montoTx, 900)), 4, 8);
        Procesos ataqueFuerzaBruta = new Bang(new Tell(new XeqC(montoTx, 900)));

        // Orquestación del paralelismo puro
        Procesos sistemaBancario = new Par(
                reglaMontoAlto,
                decaimientoRiesgo,
                motorDecision,
                ataqueFuerzaBruta
        );

        simulador.addProce(sistemaBancario);
        simulador.setTimeUnit(15);

        System.out.println("Iniciando Simulador de Fraude Transaccional - jNtcc...");
        simulador.run();
    }
}