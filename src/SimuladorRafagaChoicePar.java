import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class SimuladorRafagaChoicePar {

    public static void main(String[] args) {
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasRafaga_ChoicePar");

        // 1. VARIABLES DE CANALES CONCURRENTES Y AGREGACIÓN RELACIONAL
        IntVar paisTx         = utcclang.Store.getStore().creavarsRetor("paisTx", 0, 2);         // 1=Local, 2=Extranjero
        IntVar montoC1        = utcclang.Store.getStore().creavarsRetor("montoC1", 0, 5000);     // Canal 1 (E-commerce)
        IntVar montoC2        = utcclang.Store.getStore().creavarsRetor("montoC2", 0, 5000);     // Canal 2 (Wallet/POS)
        IntVar montoTotal     = utcclang.Store.getStore().creavarsRetor("montoTotal", 0, 10000); // Deducido vía XplusYeqZ
        IntVar dispSospechoso = utcclang.Store.getStore().creavarsRetor("dispSospechoso", 0, 1); // 0=Confiable

        // 2. VARIABLES DE MEMORIA TEMPORAL Y DEDUCCIÓN DE ANOMALÍAS
        IntVar rastroLocal    = utcclang.Store.getStore().creavarsRetor("rastroLocal", 0, 1);    // Ventana compra local
        IntVar velImposible   = utcclang.Store.getStore().creavarsRetor("velImposible", 0, 1);   // 1=Viaje imposible
        IntVar ataqueRafaga   = utcclang.Store.getStore().creavarsRetor("ataqueRafaga", 0, 1);   // 1=Ráfaga simultánea detectada

        // 3. VARIABLES DE RESPUESTA Y DECISIÓN (2FA + Unless)
        IntVar reto2FA        = utcclang.Store.getStore().creavarsRetor("reto2FA", 0, 1);
        IntVar tokenCliente   = utcclang.Store.getStore().creavarsRetor("tokenCliente", 0, 1);
        IntVar decision       = utcclang.Store.getStore().creavarsRetor("decision", 0, 3);       // 1=Aprobar, 2=Alerta 2FA, 3=Bloquear

        // =========================================================================
        // CAPA 1: PROPAGADOR RELACIONAL PERMANENTE (montoC1 + montoC2 = montoTotal)
        // =========================================================================
        // En cada unidad de tiempo donde entren transacciones por ambos canales,
        // JaCoP calcula instantáneamente montoTotal sin violar la monotonía.
        Procesos agregadorCanales = new Bang(
                new Tell(new XplusYeqZ(montoC1, montoC2, montoTotal))
        );

        // =========================================================================
        // CAPA 2: MEMORIA TEMPORAL DE UBICACIÓN FÍSICA (Next)
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
        // CAPA 3: ATAQUE DE RÁFAGA SINCRONIZADA CON ChoicePar Y Parc
        // =========================================================================
        // Cuando Star dispara la señal de ataque en el extranjero (paisTx == 2),
        // ChoicePar alinea a 3 hilos Parc en la barrera y los suelta EXACTAMENTE al mismo tiempo:
        // - Parc 1: Canal 1 elige no-determinísticamente un monto bajo el radar ($400 o $480)
        // - Parc 2: Canal 2 elige no-determinísticamente un monto bajo el radar ($450 o $350)
        // - Parc 3: Camuflaje de dispositivo confiable (dispSospechoso = 0)
        Procesos rafagaSincronizada = new Bang(
                new Ask(new XeqC(paisTx, 2),
                        new ChoicePar(
                                new Parc(
                                        new Choice(
                                                new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoC1, 400))),
                                                new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoC1, 480)))
                                        )
                                ),
                                new Parc(
                                        new Choice(
                                                new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoC2, 450))),
                                                new AskChoice(new XeqC(paisTx, 2), new Tell(new XeqC(montoC2, 350)))
                                        )
                                ),
                                new Parc(
                                        new Tell(new XeqC(dispSospechoso, 0))
                                )
                        )
                )
        );

        // =========================================================================
        // CAPA 4: DETECCIÓN CONCURRENTE DE RÁFAGA + VIAJE IMPOSIBLE
        // =========================================================================
        // Nota que montoC1 (<500) y montoC2 (<500) pasarían desapercibidos por separado.
        // Pero al ocurrir simultáneamente (montoC1 > 0 && montoC2 > 0) con suma > 700
        // dentro de rastroLocal == 1, el motor deduce velImposible = 1 y ataqueRafaga = 1.
        Procesos motorAntifraudeRafaga = new Bang(
                new Ask(new And(Ar.expre(
                        new XeqC(rastroLocal, 1),
                        new XeqC(paisTx, 2),
                        new XgtC(montoC1, 0),
                        new XgtC(montoC2, 0),
                        new XgtC(montoTotal, 700))),
                        new Par(
                                new Tell(new XeqC(velImposible, 1)),
                                new Tell(new XeqC(ataqueRafaga, 1)),
                                new Choice(
                                        // Hilo A: Bloqueo inmediato por ráfaga sincronizada
                                        new AskChoice(new XeqC(dispSospechoso, 0), new Tell(new XeqC(decision, 3))),
                                        // Hilo B: Alerta 2FA de emergencia + apertura de reto en t+1
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

        // =========================================================================
        // CAPA 5: PROTOCOLO 2FA DE EMERGENCIA CON UNLESS
        // =========================================================================
        Procesos protocoloVerificacion2FA = new Bang(
                new Ask(new XeqC(reto2FA, 1),
                        new Par(
                                new Choice(
                                        new AskChoice(new XeqC(reto2FA, 1), new Tell(new XeqC(tokenCliente, 1))),
                                        new AskChoice(new XeqC(reto2FA, 1), new Tell(new XeqC(tokenCliente, 0)))
                                ),
                                new Ask(new XeqC(tokenCliente, 1), new Tell(new XeqC(decision, 1))),
                                new Unless(new XeqC(tokenCliente, 1), new Tell(new XeqC(decision, 3)))
                        )
                )
        );

        // =========================================================================
        // ESCENARIO TEMPORAL: Compra Local en t=1 y Disparo de Ráfaga en t=[2..4]
        // =========================================================================
        Procesos compra1_Local = new Par(
                new Tell(new XeqC(paisTx, 1)),
                new Tell(new XeqC(montoC1, 30)),
                new Tell(new XeqC(montoC2, 0)),
                new Tell(new XeqC(dispSospechoso, 0)),
                new Tell(new XeqC(decision, 1))
        );

        Procesos disparoRafagaExtranjero = new Star(
                new Tell(new XeqC(paisTx, 2)),
                2, 4
        );

        Procesos ecosistema = new Par(
                agregadorCanales,
                memoriaUbicacion,
                rafagaSincronizada,
                motorAntifraudeRafaga,
                protocoloVerificacion2FA,
                compra1_Local,
                disparoRafagaExtranjero
        );

        simulador.addProce(ecosistema);
        Planner.setTimeUnit(7);
        simulador.run();
    }
}