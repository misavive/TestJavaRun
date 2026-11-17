import JaCoP.constraints.*;
import JaCoP.core.*;
import utccEnums.*;
import utcclang.*;

public class SimuladorBlindadoLocal {

    public static void main(String[] args) {
        Planner simulador = new Planner();
        utcclang.Store.setFilename("TrazasBlindado_Local");

        // Variables públicas en el Store
        IntVar paisTx         = utcclang.Store.getStore().creavarsRetor("paisTx", 0, 2);
        IntVar montoC1        = utcclang.Store.getStore().creavarsRetor("montoC1", 0, 5000);
        IntVar montoC2        = utcclang.Store.getStore().creavarsRetor("montoC2", 0, 5000);
        IntVar montoTotal     = utcclang.Store.getStore().creavarsRetor("montoTotal", 0, 10000);
        IntVar rastroLocal    = utcclang.Store.getStore().creavarsRetor("rastroLocal", 0, 1);
        IntVar velImposible   = utcclang.Store.getStore().creavarsRetor("velImposible", 0, 1);
        IntVar ataqueRafaga   = utcclang.Store.getStore().creavarsRetor("ataqueRafaga", 0, 1);

        // Variable pública que el atacante intentará sabotear (Inyección externa)
        IntVar llaveBanco     = utcclang.Store.getStore().creavarsRetor("llaveBanco", 0, 1);
        // Variable privada alfa-convertida (_llaveBanco) que usa internamente la instrucción Local
        IntVar _llaveBanco    = utcclang.Store.getStore().creavarsRetor("_llaveBanco", 0, 1);

        IntVar intentoSabotaje= utcclang.Store.getStore().creavarsRetor("intentoSabotaje", 0, 1);
        IntVar decision       = utcclang.Store.getStore().creavarsRetor("decision", 0, 3); // 1=Aprobar, 2=Alerta, 3=Bloquear

        // 1. Propagador relacional de canales: montoC1 + montoC2 = montoTotal
        Procesos agregadorCanales = new Bang(
                new Tell(new XplusYeqZ(montoC1, montoC2, montoTotal))
        );

        // 2. Memoria temporal de compra local (3 unidades vía Next)
        Procesos memoriaUbicacion = new Bang(
                new Ask(new XeqC(paisTx, 1),
                        new Par(
                                new Next(new Tell(new XeqC(rastroLocal, 1))),
                                new Next(new Next(new Tell(new XeqC(rastroLocal, 1)))),
                                new Next(new Next(new Next(new Tell(new XeqC(rastroLocal, 1)))))
                        )
                )
        );

        // 3. Ataque de Ráfaga + Intento de Sabotaje al Store Público (ChoicePar + Parc)
        // El atacante dispara simultáneamente Canal 1, Canal 2 e inyecta llaveBanco = 1
        // creyendo que con eso sobreescribirá la autorización interna del banco.
        Procesos ataqueConSabotaje = new Bang(
                new Ask(new XeqC(paisTx, 2),
                        new ChoicePar(
                                new Parc(new Tell(new XeqC(montoC1, 480))),
                                new Parc(new Tell(new XeqC(montoC2, 450))),
                                new Parc(
                                        new Par(
                                                new Tell(new XeqC(llaveBanco, 1)),      // Inyección maliciosa a la variable global
                                                new Tell(new XeqC(intentoSabotaje, 1))  // Registro del intento de inyección
                                        )
                                )
                        )
                )
        );

        // 4. Bóveda Interna del Banco protegida con LOCAL (Regla LOC de ntcc)
        // Local("llaveBanco", P) aplica alfa-conversión creando/aislando "_llaveBanco".
        // Aunque el atacante forzó llaveBanco = 1 afuera, adentro _llaveBanco vale 0 (Rechazado).
        Procesos bovedaLocalAntifraude = new Bang(
                new Ask(new And(Ar.expre(
                        new XeqC(rastroLocal, 1),
                        new XeqC(paisTx, 2),
                        new XgtC(montoTotal, 700))),
                        new Local("llaveBanco",
                                new Par(
                                        new Tell(new XeqC(velImposible, 1)),
                                        new Tell(new XeqC(ataqueRafaga, 1)),
                                        // La bóveda interna sella su variable privada _llaveBanco en 0 (No autorizado)
                                        new Tell(new XeqC(_llaveBanco, 0)),
                                        // Y decide exclusivamente con base en su variable privada _llaveBanco:
                                        new Ask(new XeqC(_llaveBanco, 0), new Tell(new XeqC(decision, 3)))
                                )
                        )
                )
        );

        // 5. Escenario Temporal
        Procesos compra1_Local = new Par(
                new Tell(new XeqC(paisTx, 1)),
                new Tell(new XeqC(montoC1, 30)),
                new Tell(new XeqC(montoC2, 0)),
                new Tell(new XeqC(decision, 1))
        );

        Procesos disparoAtaque = new Star(new Tell(new XeqC(paisTx, 2)), 2, 4);

        Procesos ecosistema = new Par(
                agregadorCanales,
                memoriaUbicacion,
                ataqueConSabotaje,
                bovedaLocalAntifraude,
                compra1_Local,
                disparoAtaque
        );

        simulador.addProce(ecosistema);
        Planner.setTimeUnit(6);
        simulador.run();
    }
}