import utcclang.*;
import utccEnums.*;
import JaCoP.core.IntVar;
import JaCoP.constraints.*;

public class UCIMonitor_jNtcc {
    public static void main(String[] args) {
        Planner planner = new Planner();
        Store.setFilename("UCI_Clinical_Triage");
        Planner.setTimeUnit(100); // Monitoreo continuo

        // ---------- Variables de Estado (Signos Vitales) ----------
        IntVar heartRate = Store.getStore().creavarsRetor("heartRate", 0, 200);
        IntVar oxygenSat = Store.getStore().creavarsRetor("oxygenSat", 0, 100);
        IntVar riskScore = Store.getStore().creavarsRetor("riskScore", 0, 10); // Celda acumulativa

        // ---------- Variables de Actuación ----------
        IntVar alertLevel = Store.getStore().creavarsRetor("alertLevel", 0, 2); // 0: Normal, 1: Alerta, 2: Crítico
        IntVar callSpecialist = Store.getStore().creavarsRetor("callSpecialist", 0, 1);

        // ============================================================
        //  ESCENARIO: Comportamiento del Paciente (Entorno)
        // ============================================================
        // El paciente presenta una caída eventual de oxígeno
        Procesos hipoxiaEvento = new Par(
                new Tell(new XeqC(heartRate, 110)),
                new Tell(new XltC(oxygenSat, 85))
        );

        // Simulación: Evento de hipoxia ocurre aleatoriamente entre t=10 y t=30
        Procesos entorno = new Star(hipoxiaEvento, 10, 30);

        // ============================================================
        //  LÓGICA DE MONITOREO (Aporte Científico)
        // ============================================================

        // 1. Análisis de Riesgo Acumulado (Uso de Change y Celdas)
        // Si la saturación es < 90, incrementamos el riesgo cada unidad de tiempo
        Procesos monitorSaturacion = new Ask(
                new XltC(oxygenSat, 90),
                new Change(riskScore, Operation._INCRE, 1)
        );

        // 2. Triage No-Determinístico (Uso de ChoicePar y Parc)
        // Si hay múltiples alarmas, el sistema usa ChoicePar para decidir la acción de hilos
        Procesos triageDecision = new Ask(
                new XgtC(riskScore, 5),
                new ChoicePar(
                        new Parc(new Tell(new XeqC(alertLevel, 2))), // Opción A: Activar código azul
                        new Parc(new Tell(new XeqC(callSpecialist, 1))) // Opción B: Llamar especialista
                )
        );

        // 3. Control de Excepciones (Uso de Unless)
        // A menos que el médico confirme recepción (simulado por variable), repetir alerta en el futuro
        IntVar doctorArrived = Store.getStore().creavarsRetor("doctorArrived", 0, 1);
        Procesos alertaPersistente = new Unless(
                new XeqC(doctorArrived, 1),
                new Next(new Tell(new XeqC(callSpecialist, 1)))
        );

        // 4. Monitoreo por Ventanas de Tiempo (Uso de BangPn)
        // Durante las primeras 50 unidades, realizar un chequeo intensivo
        Procesos chequeoIntensivo = new BangPn(
                new Tell(new XeqC(alertLevel, 1)),
                1, 50
        );

        // ============================================================
        //  EJECUCIÓN DEL MODELO
        // ============================================================
        Procesos sistemaSoporte = new Bang(new Par(
                monitorSaturacion,
                triageDecision,
                alertaPersistente
        ));

        planner.addProce(new Par(entorno, sistemaSoporte, chequeoIntensivo));
        planner.run();
    }
}