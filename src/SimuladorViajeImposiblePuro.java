import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class SimuladorViajeImposiblePuro {

    public static void main(String[] args) {
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasViajeImposible_Choice");

        // Variables del Store (Se instancian máximo UNA vez por unidad vía Tell)
        IntVar paisTx         = utcclang.Store.getStore().creavarsRetor("paisTx", 0, 2);         // 1=Local, 2=Extranjero
        IntVar montoTx        = utcclang.Store.getStore().creavarsRetor("montoTx", 0, 10000);
        IntVar dispSospechoso = utcclang.Store.getStore().creavarsRetor("dispSospechoso", 0, 1); // 0=Confiable, 1=Sospechoso
        IntVar rastroLocal    = utcclang.Store.getStore().creavarsRetor("rastroLocal", 0, 1);    // Memoria temporal (Next)
        IntVar velImposible   = utcclang.Store.getStore().creavarsRetor("velImposible", 0, 1);   // Señal deducida
        IntVar decision       = utcclang.Store.getStore().creavarsRetor("decision", 0, 3);       // 1=Aprobar, 2=Alertar, 3=Bloquear

        // =========================================================================
        // 1. MEMORIA TEMPORAL: Rastro de la compra local durante las próximas 3 unidades
        // =========================================================================
        Procesos memoriaUbicacion = new Bang(
                new Ask(new XeqC(paisTx, 1),
                        new Par(
                                new Next(new Tell(new XeqC(rastroLocal, 1))),
                                new Next(new Next(new Tell(new XeqC(rastroLocal, 1)))),
                                new Next(new Next(new Next(new Tell(new XeqC(rastroLocal, 1)))))
                        )
                )
        );

        // =========================================================================
        // 2. PROCESO DE INSTANCIACIÓN NO DETERMINISTA DEL MONTO (Choice 1)
        // =========================================================================
        // Cuando ocurre una transacción en otro país (paisTx = 2), un proceso independiente
        // usa Choice para instanciar no-determinísticamente el monto y el dispositivo.
        Procesos generadorMontoExtranjero = new Bang(
                new Ask(new XeqC(paisTx, 2),
                        new Par(
                                new Tell(new XeqC(dispSospechoso, 0)), // Dispositivo NO sospechoso (camuflaje)
                                new Choice(
                                        // Hilo A: El atacante intenta un monto moderado-alto ($650)
                                        new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoTx, 650))),
                                        // Hilo B: El atacante intenta un golpe fuerte ($1500)
                                        new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoTx, 1500))),
                                        // Hilo C: El atacante hace una compra pequeña en el extranjero ($45)
                                        new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoTx, 45)))
                                )
                        )
                )
        );

        // =========================================================================
        // 3. DEDUCCIÓN DE VELOCIDAD IMPOSIBLE
        // =========================================================================
        Procesos reglaVelocidad = new Bang(
                new Ask(new And(Ar.expre(new XeqC(rastroLocal, 1), new XeqC(paisTx, 2))),
                        new Tell(new XeqC(velImposible, 1))
                )
        );

        // =========================================================================
        // 4. MOTOR DE DECISIÓN ANTIFRAUDE NO DETERMINISTA (Choice 2)
        // =========================================================================
        // Caso A: Velocidad imposible (1) + Monto alto (> 500) + Dispositivo No Sospechoso (0)
        // Compiten en tiempo real la Alerta (2) y el Bloqueo (3)
        Procesos decisionFraudeAlto = new Bang(
                new Ask(new And(Ar.expre(
                        new XeqC(velImposible, 1),
                        new XgtC(montoTx, 500),
                        new XeqC(dispSospechoso, 0))),
                        new Choice(
                                new AskChoice(new XeqC(velImposible, 1), new Tell(new XeqC(decision, 3))), // Bloquear
                                new AskChoice(new XeqC(velImposible, 1), new Tell(new XeqC(decision, 2)))  // Alertar
                        )
                )
        );

        // Caso B: Velocidad imposible (1) pero Monto Bajo (<= 500, ej. si ganó el hilo de $45)
        // Como el dispositivo es confiable y el monto es bajo, el banco solo emite Alerta (2) o Aprueba (1)
        Procesos decisionFraudeBajo = new Bang(
                new Ask(new And(Ar.expre(
                        new XeqC(velImposible, 1),
                        new XlteqC(montoTx, 500),
                        new XeqC(dispSospechoso, 0))),
                        new Choice(
                                new AskChoice(new XeqC(velImposible, 1), new Tell(new XeqC(decision, 2))), // Alertar
                                new AskChoice(new XeqC(velImposible, 1), new Tell(new XeqC(decision, 1)))  // Aprobar por bajo monto
                        )
                )
        );

        // =========================================================================
        // 5. ESCENARIO TEMPORAL: Compra Local en t=1 y Disparo Eventual en t=[2..4]
        // =========================================================================
        Procesos compra1_Local = new Par(
                new Tell(new XeqC(paisTx, 1)),
                new Tell(new XeqC(montoTx, 30)),
                new Tell(new XeqC(dispSospechoso, 0)),
                new Tell(new XeqC(decision, 1))
        );

        // ¡Mira qué limpio queda el Star ahora! Solo dispara la presencia de la compra en otro país
        // entre las unidades 2 y 4 (dentro de la ventana de velocidad imposible).
        Procesos disparoSegundoPais = new Star(
                new Tell(new XeqC(paisTx, 2)),
                2, 4
        );

        Procesos ecosistema = new Par(
                memoriaUbicacion,
                generadorMontoExtranjero,
                reglaVelocidad,
                decisionFraudeAlto,
                decisionFraudeBajo,
                compra1_Local,
                disparoSegundoPais
        );

        simulador.addProce(ecosistema);
        Planner.setTimeUnit(6);
        simulador.run();
    }
}