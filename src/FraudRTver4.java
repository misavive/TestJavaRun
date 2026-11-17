// ============================================================
// FraudRT.java — Real-Time Credit Card Fraud Detection (jNtcc)
// Runtime: jNtccVM.jar  +  JaCoP 3.2
// ============================================================
// Rules / Conventions:
// - Par(...) with N es (varargs)
// - Variables: Store.getStore().creavarsRetor("name", min, max)
// - Tell(...) only receives a Constraint
// - Ask(Constraint cond, Procesos body)
// - AskChoice(Constraint cond, Procesos body)
// - SumWeight(IntVar[] list, int[] weights, IntVar sum)
// - Star(Procesos P, ini, fin) → execute P once in [ini, fin]
// - Star(Procesos P, ini) → execute P once, eventually, between ini and horizon end
// - Execution: planner.addProce(model); planner.run();
// ============================================================

import JaCoP.constraints.*;
import JaCoP.core.IntVar;
import utcclang.*;
import utccEnums.*;

public class FraudRTver4 {

    public static void main(String[] args) {
        System.out.println(">>> INICIANDO FRAUDRT <<<");

        // ---------- Planner & Store ----------
        Planner fraud = new Planner();
        Store.setFilename("FraudRT_StoreVer420.txt");
        Planner.setTimeUnit(50);  // total horizon of 20 time units

        // ---------- Variables (transaction context) ----------
        IntVar incomingTxn = Store.getStore().creavarsRetor("incomingTxn", 0, 1);
        IntVar txnCountry  = Store.getStore().creavarsRetor("txnCountry",  0, 1);       // 0=LOCAL, 1=FOREIGN
        IntVar txnAmount   = Store.getStore().creavarsRetor("txnAmount",   0, 1_000_000);
        IntVar txnDeltaMin = Store.getStore().creavarsRetor("txnDeltaMin", 0, 60);
        IntVar deviceOk    = Store.getStore().creavarsRetor("deviceOk",    0, 1);
        IntVar deviceTrust = Store.getStore().creavarsRetor("deviceTrust", 0, 3);

        // ---------- Risk flags ----------
        IntVar riskyGeo      = Store.getStore().creavarsRetor("riskyGeo",      0, 1);
        IntVar riskyVelocity = Store.getStore().creavarsRetor("riskyVelocity", 0, 1);
        IntVar riskyAmount   = Store.getStore().creavarsRetor("riskyAmount",   0, 1);
        IntVar riskyDevice   = Store.getStore().creavarsRetor("riskyDevice",   0, 1);

        // ---------- Decisions ----------
        IntVar approve = Store.getStore().creavarsRetor("approve", 0, 1);
        IntVar flag    = Store.getStore().creavarsRetor("flag",    0, 1);
        IntVar block   = Store.getStore().creavarsRetor("block",   0, 1);

        // ---------- Environment: example transactions ----------
        // Local purchase (safe-ish)
        Procesos txnLocal = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 0)),       // LOCAL
                new Tell(new XeqC(txnAmount, 120_000)),
                new Tell(new XeqC(txnDeltaMin, 10)),
                new Tell(new XeqC(deviceOk, 1))
        );

        Procesos deviceTrustUpdater = new Bang(
                new Ask(
                        new XeqC(deviceOk, 1),
                        new Change(deviceTrust, Operation._INCRE, 1)  // +1 confianza
                )
        );



        Procesos txnForeignSoon = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 1)),       // FOREIGN
                new Tell(new XeqC(txnAmount, 350_000)),
                new Tell(new XeqC(txnDeltaMin, 1)),      // 1 minuto desde la anterior
                new Tell(new XeqC(deviceOk, 1))
        );

        Procesos env = new Par(
                new Star(txnLocal, 1, 10),
                new Star(txnForeignSoon,11, 20)  // rangos no solapados
        );

// Detectores
        Procesos geoDetector =
                new Ask(
                        new XeqC(incomingTxn, 1),
                        new Ask(
                                new XeqC(txnCountry, 1),
                                new Ask(
                                        new XltC(txnDeltaMin, 3),
                                        new Tell(new XeqC(riskyGeo, 1))
                                )
                        )
                );

        Procesos velocityDetector =
                new Ask(
                        new XeqC(incomingTxn, 1),
                        new Ask(
                                new XltC(txnDeltaMin, 3),
                                new Tell(new XeqC(riskyVelocity, 1))
                        )
                );

        Procesos amountDetector =
                new Ask(
                        new XeqC(incomingTxn, 1),
                        new Ask(
                                new XgteqC(txnAmount, 200_000),
                                new Tell(new XeqC(riskyAmount, 1))
                        )
                );

        Procesos deviceDetector =
                new Ask(
                        new XeqC(incomingTxn, 1),
                        new Ask(
                                new XeqC(deviceOk, 0),
                                new Tell(new XeqC(riskyDevice, 1))
                        )
                );

// Agregación
        IntVar[] risks = new IntVar[] {riskyGeo, riskyVelocity, riskyAmount, riskyDevice};
        int[] weights   = new int[]   {1,        1,             1,            1};
        IntVar sumAll   = Store.getStore().creavarsRetor("sumAll", 0, 4);
        Procesos sumRisks = new Tell(new SumWeight(risks, weights, sumAll));

        Procesos devicePenalty = new Bang(
                new Ask(
                        new XgteqC(sumAll, 2), // mucho riesgo
                        new Change(deviceTrust, Operation._DECRE, 1)  // -1 confianza
                )
        );

// Decisión
        Procesos decision = new Choice(
                // Solo bloqueamos si hay mucho riesgo Y poca confianza
                new AskChoice(
                        new And(
                                new XgteqC(sumAll, 3),       // mucho riesgo
                                new XltC(deviceTrust, 2)     // confianza baja
                        ),
                        new Tell(new XeqC(block, 1))
                ),

                // Si hay riesgo medio (2–3) pero confianza alta → solo flag
                new AskChoice(
                        new And(
                                new XgteqC(sumAll, 2),
                                new XgteqC(deviceTrust, 2)   // el dispositivo tiene buena historia
                        ),
                        new Tell(new XeqC(flag, 1))
                ),

                // Riesgo bajo o nulo → approve
                new AskChoice(
                        new XlteqC(sumAll, 1),
                        new Tell(new XeqC(approve, 1))
                )
        );



// Monitor continuo
        Procesos monitors = new Bang(
                new Par(
                        geoDetector,
                        velocityDetector,
                        amountDetector,
                        deviceDetector,
                        sumRisks,
                        decision
                )
        );

// Modelo completo
        Procesos model = new Par(env, monitors);
        fraud.addProce(model);
        fraud.run();

        System.out.println("FraudRT model finished. Check FraudRT_Store.txt for Store evolution.");
    }
}