import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class ViajeImposible2FAUnless {

    public static void main(String[] args) {
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasViajeImposible_2FA");

        // Variables del Store (Una sola instanciación por unidad de tiempo vía Tell)
        IntVar paisTx         = utcclang.Store.getStore().creavarsRetor("paisTx", 0, 2);         // 1=Local, 2=Extranjero
        IntVar montoTx        = utcclang.Store.getStore().creavarsRetor("montoTx", 0, 10000);
        IntVar dispSospechoso = utcclang.Store.getStore().creavarsRetor("dispSospechoso", 0, 1); // 0=Confiable, 1=Sospechoso
        IntVar rastroLocal    = utcclang.Store.getStore().creavarsRetor("rastroLocal", 0, 1);    // Memoria temporal (Next)
        IntVar velImposible   = utcclang.Store.getStore().creavarsRetor("velImposible", 0, 1);   // Señal deducida

        // Nuevas variables para el protocolo 2FA con Unless
        IntVar reto2FA        = utcclang.Store.getStore().creavarsRetor("reto2FA", 0, 1);        // 1=Banco esperando Push 2FA
        IntVar tokenCliente   = utcclang.Store.getStore().creavarsRetor("tokenCliente", 0, 1);   // 1=Cliente aprobó en su App
        IntVar decision       = utcclang.Store.getStore().creavarsRetor("decision", 0, 3);       // 1=Aprobar, 2=Alerta/2FA, 3=Bloquear

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
        // 2. INSTANCIACIÓN NO DETERMINISTA DEL MONTO EN EL EXTRANJERO (Choice 1)
        // =========================================================================
        Procesos generadorMontoExtranjero = new Bang(
                new Choice(
                        new AskChoice(new XeqC(paisTx, 2), new Par(new Tell(new XeqC(montoTx, 650)), new Tell(new XeqC(dispSospechoso, 0)))),
                        new AskChoice(new XeqC(paisTx, 2), new Par(new Tell(new XeqC(montoTx, 1500)), new Tell(new XeqC(dispSospechoso, 0)))),
                        new AskChoice(new XeqC(paisTx, 2), new Par(new Tell(new XeqC(montoTx, 45)), new Tell(new XeqC(dispSospechoso, 0))))
                )
        );

        // =========================================================================
        // 3. MOTOR ANTIFRAUDE EN MILISEGUNDOS (Choice 2) + DISPARO DE RETO 2FA
        // =========================================================================
        Procesos decisionFraudeAlto = new Bang(
                new Ask(new And(Ar.expre(new XeqC(rastroLocal, 1), new XeqC(paisTx, 2), new XgtC(montoTx, 500))),
                        new Par(
                                new Tell(new XeqC(velImposible, 1)),
                                new Choice(
                                        // Hilo A: Bloqueo directo en t
                                        new AskChoice(new XeqC(dispSospechoso, 0), new Tell(new XeqC(decision, 3))),
                                        // Hilo B: Alerta Temprana (decision = 2) en t -> Abre reto2FA en t+1
                                        new AskChoice(new XeqC(dispSospechoso, 0),
                                                new Par(
                                                        new Tell(new XeqC(decision, 2)),
                                                        new Next(new Tell(new XeqC(reto2FA, 1)))
                                                )
                                        )
                                )
                        )
                )
        );

        Procesos decisionFraudeBajo = new Bang(
                new Ask(new And(Ar.expre(new XeqC(rastroLocal, 1), new XeqC(paisTx, 2), new XgtC(montoTx, 0), new XlteqC(montoTx, 500))),
                        new Par(
                                new Tell(new XeqC(velImposible, 1)),
                                new Choice(
                                        // Si elige Alerta (2), también abre reto2FA en t+1
                                        new AskChoice(new XeqC(dispSospechoso, 0),
                                                new Par(
                                                        new Tell(new XeqC(decision, 2)),
                                                        new Next(new Tell(new XeqC(reto2FA, 1)))
                                                )
                                        ),
                                        new AskChoice(new XeqC(dispSospechoso, 0), new Tell(new XeqC(decision, 1)))
                                )
                        )
                )
        );

        // =========================================================================
        // 4. PROTOCOLO 2FA CON UNLESS (Preempción Débil por Ausencia de Respuesta)
        // =========================================================================
        // Cuando reto2FA == 1:
        // - Simulamos con un Choice si el usuario responde (tokenCliente = 1) o no responde (0).
        // - Si tokenCliente == 1 -> Se aprueba la compra en ese mismo instante (decision = 1).
        // - UNLESS: A menos que se deduzca tokenCliente == 1 en esta unidad, en la siguiente
        //   unidad de tiempo se bloquea definitivamente la tarjeta (decision = 3).
        Procesos protocoloVerificacion2FA = new Bang(
                new Ask(new XeqC(reto2FA, 1),
                        new Par(
                                // Comportamiento incierto del humano/atacante ante el reto Push:
                                new Choice(
                                        new AskChoice(new XeqC(reto2FA, 1), new Tell(new XeqC(tokenCliente, 1))), // Responde OK
                                        new AskChoice(new XeqC(reto2FA, 1), new Tell(new XeqC(tokenCliente, 0)))  // Silencio / Falla 2FA
                                ),
                                // Si respondió con el token válido, aprobamos en la unidad actual:
                                new Ask(new XeqC(tokenCliente, 1), new Tell(new XeqC(decision, 1))),
                                // UNLESS: Si al final de la unidad NO se pudo deducir tokenCliente == 1,
                                // se agenda el Bloqueo (decision = 3) para la siguiente unidad de tiempo:
                                new Unless(new XeqC(tokenCliente, 1), new Tell(new XeqC(decision, 3)))
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

        Procesos disparoSegundoPais = new Star(
                new Tell(new XeqC(paisTx, 2)),
                2, 4
        );

        Procesos ecosistema = new Par(
                memoriaUbicacion,
                generadorMontoExtranjero,
                decisionFraudeAlto,
                decisionFraudeBajo,
                protocoloVerificacion2FA,
                compra1_Local,
                disparoSegundoPais
        );

        simulador.addProce(ecosistema);
        Planner.setTimeUnit(7);
        simulador.run();
    }
}