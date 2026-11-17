// ============================================================
// FraudRT_V%L.java — Real-Time Credit Card Fraud (jNtcc, V5)
// Short & Long horizon + advanced risk detectors
// ============================================================
// Reglas oficiales jNtcc en este entorno:
// - Variables: IntVar v = Store.getStore().creavarsRetor("name", min, max);
// - Tell(Constraint c)  // c: XeqC, XltC, XgtC, XgteqC, SumWeight, etc.
// - Ask(Constraint cond, Procesos body)
// - AskChoice(cond, body) solo dentro de Choice(...)
// - Par(p1, p2, p3, ...)
// - Bang(P)  // ejecuta P en todas las unidades de tiempo
// - Star(P, ini, fin)  // ejecuta P una sola vez en una unidad aleatoria [ini, fin]
// - Change(IntVar v, Operation._INCRE/_DECRE, int value)
// - Planner: new Planner(); Planner.setTimeUnit(N); planner.addProce(model); planner.run();
// ============================================================

import utcclang.*;
import utccEnums.*;
import JaCoP.core.IntVar;
import JaCoP.constraints.SumWeight;
import JaCoP.constraints.XeqC;
import JaCoP.constraints.XltC;
import JaCoP.constraints.XgtC;
import JaCoP.constraints.XgteqC;

public class FraudRT_V5L {

    // ------------------------------------------------------------
    // Selector de versión:
    // false -> V5-Short (20 unidades, 3 Star)
    // true  -> V5-Long  (100 unidades, 5 Star + 10 detectores)
    // ------------------------------------------------------------
    private static final boolean LONG_VERSION = true;   // pon true para la versión larga

    public static void main(String[] args) {

        // ---------- Planner & Store ----------
        Planner planner = new Planner();
        if (LONG_VERSION) {
            Store.setFilename("FraudRT_V5_05_Long");
            Planner.setTimeUnit(100);
        } else {
            Store.setFilename("FraudRT_V5_20_Short");
            Planner.setTimeUnit(20);
        }

        // ---------- Variables base de la transacción ----------
        IntVar incomingTxn = Store.getStore().creavarsRetor("incomingTxn", 0, 1);
        IntVar txnCountry  = Store.getStore().creavarsRetor("txnCountry",  0, 1);         // 0=LOCAL, 1=FOREIGN
        IntVar txnAmount   = Store.getStore().creavarsRetor("txnAmount",   0, 1_000_000);
        IntVar txnDeltaMin = Store.getStore().creavarsRetor("txnDeltaMin", 0, 60);        // minutos desde la anterior
        IntVar deviceOk    = Store.getStore().creavarsRetor("deviceOk",    0, 1);         // 1=OK, 0=comprometido
        IntVar countryRisk = Store.getStore().creavarsRetor("countryRisk", 0, 10);        // 0..10 (0=seguro, 10=alto riesgo)
        IntVar txnHour     = Store.getStore().creavarsRetor("txnHour",     0, 23);        // 0..23 (hora del sistema)

        // ---------- Contadores / estado acumulado ----------
        IntVar freqCounter        = Store.getStore().creavarsRetor("freqCounter",        0, 100);
        IntVar amountJumpCounter  = Store.getStore().creavarsRetor("amountJumpCounter",  0, 100);

        // ---------- Señales de riesgo (flags 0/1) ----------
        // Base
        IntVar riskyGeo             = Store.getStore().creavarsRetor("riskyGeo",             0, 1);
        IntVar riskyVelocity        = Store.getStore().creavarsRetor("riskyVelocity",        0, 1);
        IntVar riskyAmount          = Store.getStore().creavarsRetor("riskyAmount",          0, 1);
        IntVar riskyDevice          = Store.getStore().creavarsRetor("riskyDevice",          0, 1);
        // Avanzados
        IntVar riskyHighRiskCountry = Store.getStore().creavarsRetor("riskyHighRiskCountry", 0, 1);
        IntVar riskyNight           = Store.getStore().creavarsRetor("riskyNight",           0, 1);
        IntVar riskyFrequency       = Store.getStore().creavarsRetor("riskyFrequency",       0, 1);
        IntVar riskyAmountJump      = Store.getStore().creavarsRetor("riskyAmountJump",      0, 1);
        IntVar riskyGeoAmountCombo  = Store.getStore().creavarsRetor("riskyGeoAmountCombo",  0, 1);
        IntVar riskyDeviceLate      = Store.getStore().creavarsRetor("riskyDeviceLate",      0, 1);

        // ---------- Decisión final ----------
        IntVar approve = Store.getStore().creavarsRetor("approve", 0, 1);
        IntVar flag    = Store.getStore().creavarsRetor("flag",    0, 1);
        IntVar block   = Store.getStore().creavarsRetor("block",   0, 1);

        // ============================================================
        //  ENTORNO: Transacciones simuladas (eventos Star)
        // ============================================================

        // Evento 1: transacción local "normal"
        // Día, país seguro, monto medio, sin urgencia, dispositivo OK
        Procesos txnLocal = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 0)),          // LOCAL
                new Tell(new XeqC(txnAmount, 120_000)),
                new Tell(new XeqC(txnDeltaMin, 10)),
                new Tell(new XeqC(deviceOk, 1)),
                new Tell(new XeqC(countryRisk, 2)),         // país muy seguro
                new Tell(new XeqC(txnHour, 10))             // 10 a.m.
        );

        // Evento 2: transacción extranjera cercana en el tiempo (posible fraude típico)
        Procesos txnForeignSoon = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 1)),          // FOREIGN
                new Tell(new XeqC(txnAmount, 350_000)),
                new Tell(new XeqC(txnDeltaMin, 1)),         // 1 minuto desde la anterior
                new Tell(new XeqC(deviceOk, 1)),
                new Tell(new XeqC(countryRisk, 6)),         // riesgo medio-alto
                new Tell(new XeqC(txnHour, 14))             // 2 p.m.
        );

        // Evento 3: monto muy alto en país riesgoso, de noche
        Procesos txnHighAmountRandom = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 1)),          // FOREIGN
                new Tell(new XeqC(txnAmount, 500_000)),
                new Tell(new XeqC(txnDeltaMin, 2)),
                new Tell(new XeqC(deviceOk, 1)),
                new Tell(new XeqC(countryRisk, 8)),         // país de alto riesgo
                new Tell(new XeqC(txnHour, 23))             // 11 p.m.
        );

        // Evento 4: dispositivo comprometido, horario nocturno, local
        Procesos txnSuspiciousDevice = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 0)),          // LOCAL
                new Tell(new XeqC(txnAmount, 90_000)),
                new Tell(new XeqC(txnDeltaMin, 5)),
                new Tell(new XeqC(deviceOk, 0)),            // dispositivo comprometido
                new Tell(new XeqC(countryRisk, 3)),         // país relativamente seguro
                new Tell(new XeqC(txnHour, 3))              // 3 a.m.
        );

        // Evento 5: transacción extranjera grande, tardía y con dispositivo comprometido
        Procesos txnForeignLargeLate = new Par(
                new Tell(new XeqC(incomingTxn, 1)),
                new Tell(new XeqC(txnCountry, 1)),          // FOREIGN
                new Tell(new XeqC(txnAmount, 700_000)),
                new Tell(new XeqC(txnDeltaMin, 15)),
                new Tell(new XeqC(deviceOk, 0)),
                new Tell(new XeqC(countryRisk, 9)),         // país muy riesgoso
                new Tell(new XeqC(txnHour, 2))              // 2 a.m.
        );

        // ---------- Entorno para la versión corta (20 unidades) ----------
        Procesos envShort = new Par(
                new Star(txnLocal,            1,  10),
                new Star(txnForeignSoon,      6, 15),
                new Star(txnHighAmountRandom, 8, 19)
        );

        // ---------- Entorno para la versión larga (100 unidades) ----------
        Procesos envLong = new Par(
                new Star(txnLocal,              1,  25),
                new Star(txnForeignSoon,       10,  40),
                new Star(txnHighAmountRandom,  20,  60),
                new Star(txnSuspiciousDevice,  40,  80),
                new Star(txnForeignLargeLate,  60, 100)
        );

        Procesos env = LONG_VERSION ? envLong : envShort;

        // ============================================================
        //  LIMPIEZA POR UNIDAD (no resetea contadores acumulados)
        // ============================================================

        Procesos clearFlags = new Par(
                new Tell(new XeqC(riskyGeo,             0)),
                new Tell(new XeqC(riskyVelocity,        0)),
                new Tell(new XeqC(riskyAmount,          0)),
                new Tell(new XeqC(riskyDevice,          0)),
                new Tell(new XeqC(riskyHighRiskCountry, 0)),
                new Tell(new XeqC(riskyNight,           0)),
                new Tell(new XeqC(riskyFrequency,       0)),
                new Tell(new XeqC(riskyAmountJump,      0)),
                new Tell(new XeqC(riskyGeoAmountCombo,  0)),
                new Tell(new XeqC(riskyDeviceLate,      0)),
                new Tell(new XeqC(approve,              0)),
                new Tell(new XeqC(flag,                 0)),
                new Tell(new XeqC(block,                0))
        );

        // ============================================================
        //  DETECTORES BASE
        // ============================================================

        // Geográfico: FOREIGN y delta < 3  => riskyGeo = 1
        Procesos geoDetector =
                new Ask(
                        new XeqC(txnCountry, 1),
                        new Ask(
                                new XltC(txnDeltaMin, 3),
                                new Tell(new XeqC(riskyGeo, 1))
                        )
                );

        // Velocidad: delta < 3 => riskyVelocity = 1
        Procesos velocityDetector =
                new Ask(
                        new XltC(txnDeltaMin, 3),
                        new Tell(new XeqC(riskyVelocity, 1))
                );

        // Monto alto: amount ≥ 200 000 => riskyAmount = 1
        Procesos amountDetector =
                new Ask(
                        new XgteqC(txnAmount, 200_000),
                        new Tell(new XeqC(riskyAmount, 1))
                );

        // Dispositivo comprometido: deviceOk == 0 => riskyDevice = 1
        Procesos deviceDetector =
                new Ask(
                        new XeqC(deviceOk, 0),
                        new Tell(new XeqC(riskyDevice, 1))
                );

        // ============================================================
        //  DETECTORES AVANZADOS (Versión científica)
        // ============================================================

        // País de alto riesgo: countryRisk > 7 => riskyHighRiskCountry = 1
        Procesos highRiskCountryDetector =
                new Ask(
                        new XgtC(countryRisk, 7),
                        new Tell(new XeqC(riskyHighRiskCountry, 1))
                );

        // Horario nocturno: txnHour < 5 => riskyNight = 1
        Procesos nightDetector =
                new Ask(
                        new XltC(txnHour, 5),
                        new Tell(new XeqC(riskyNight, 1))
                );

        // Actualización de frecuencia: cada vez que incomingTxn == 1 incrementa freqCounter
        Procesos freqUpdate =
                new Ask(
                        new XeqC(incomingTxn, 1),
                        new Change(freqCounter, Operation._INCRE, 1)
                );

        // Frecuencia alta: freqCounter > 3 => riskyFrequency = 1
        Procesos freqDetector =
                new Ask(
                        new XgtC(freqCounter, 3),
                        new Tell(new XeqC(riskyFrequency, 1))
                );

        // Actualización de "saltos" de monto: cada vez que amount >= 200 000 incrementa amountJumpCounter
        Procesos amountJumpUpdate =
                new Ask(
                        new XgteqC(txnAmount, 200_000),
                        new Change(amountJumpCounter, Operation._INCRE, 1)
                );

        // Salto de monto: amountJumpCounter > 1 => múltiples montos altos en el tiempo
        Procesos amountJumpDetector =
                new Ask(
                        new XgtC(amountJumpCounter, 1),
                        new Tell(new XeqC(riskyAmountJump, 1))
                );

        // Combinación Geo + Amount: si riskyGeo=1 y riskyAmount=1 => riskyGeoAmountCombo=1
        Procesos geoAmountComboDetector =
                new Ask(
                        new XeqC(riskyGeo, 1),
                        new Ask(
                                new XeqC(riskyAmount, 1),
                                new Tell(new XeqC(riskyGeoAmountCombo, 1))
                        )
                );

        // Dispositivo comprometido tardío (se marca cada vez que deviceOk=0)
        Procesos deviceLateDetector =
                new Ask(
                        new XeqC(deviceOk, 0),
                        new Tell(new XeqC(riskyDeviceLate, 1))
                );

        // ============================================================
        //  AGREGACIÓN CON SumWeight (FraudScore)
        // ============================================================

        IntVar sumAll = Store.getStore().creavarsRetor("sumAll", 0, 15);

        IntVar[] risks = new IntVar[] {
                riskyGeo,
                riskyVelocity,
                riskyAmount,
                riskyDevice,
                riskyHighRiskCountry,
                riskyNight,
                riskyFrequency,
                riskyAmountJump,
                riskyGeoAmountCombo,
                riskyDeviceLate
        };

        int[] weights = new int[] {
                1,  // riskyGeo
                1,  // riskyVelocity
                1,  // riskyAmount
                1,  // riskyDevice
                2,  // riskyHighRiskCountry
                2,  // riskyNight
                2,  // riskyFrequency
                1,  // riskyAmountJump
                2,  // riskyGeoAmountCombo
                2   // riskyDeviceLate
        };

        Procesos sumRisks = new Tell(new SumWeight(risks, weights, sumAll));

        // ============================================================
        //  DECISIÓN (Choice + AskChoice)
        // ============================================================
        // Umbrales:
        // sumAll >= 5  => block
        // sumAll >= 2  => flag
        // sumAll == 0  => approve

        Procesos decision = new Choice(
                new AskChoice(
                        new XgteqC(sumAll, 5),
                        new Tell(new XeqC(block, 1))
                ),
                new AskChoice(
                        new XgteqC(sumAll, 2),
                        new Tell(new XeqC(flag, 1))
                ),
                new AskChoice(
                        new XeqC(sumAll, 0),
                        new Tell(new XeqC(approve, 1))
                )
        );

        // ============================================================
        //  MOTOR CONTINUO: monitores en todas las unidades de tiempo
        // ============================================================

        Procesos monitors = new Bang(
                new Par(
                        //clearFlags,

                        // Detectores base
                        geoDetector,
                        velocityDetector,
                        amountDetector,
                        deviceDetector,

                        // Detectores avanzados
                        highRiskCountryDetector,
                        nightDetector,
                        freqUpdate,
                        freqDetector,
                        amountJumpUpdate,
                        amountJumpDetector,
                        geoAmountComboDetector,
                        deviceLateDetector,

                        // Agregación + decisión
                        sumRisks,
                        decision
                )
        );

        // ============================================================
        //  MODELO COMPLETO Y EJECUCIÓN
        // ============================================================

        Procesos model = new Par(env, monitors);
        planner.addProce(model);
        planner.run();
    }
}