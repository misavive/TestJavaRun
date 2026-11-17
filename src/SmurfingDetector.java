import utcclang.*;
import utccEnums.*;
import JaCoP.core.IntVar;
import JaCoP.constraints.*;

public class SmurfingDetector {

    public static void main(String[] args) {
        // 1. Configuración del Motor
        Planner planner = new Planner();
        Store.setFilename("Smurfing_Test_Log");
        Planner.setTimeUnit(50); // Ejecución por 50 unidades de tiempo

        // 2. Variables del Store (Celdas y Señales)
        // Contador persistente de transacciones pequeñas (Celda)
        IntVar smurfCounter = Store.getStore().creavarsRetor("smurfCounter", 0, 20);
        // Señales del entorno
        IntVar txnAmount = Store.getStore().creavarsRetor("txnAmount", 0, 100000);
        IntVar incoming = Store.getStore().creavarsRetor("incoming", 0, 1);
        // Acciones de respuesta
        IntVar blockUser = Store.getStore().creavarsRetor("blockUser", 0, 1);
        IntVar reportUIF = Store.getStore().creavarsRetor("reportUIF", 0, 1);

        // ============================================================
        //  ENTORNO: Generador de Tráfico (El Pitufo)
        // ============================================================
        // Simula depósitos de $9,000 (justo debajo del umbral de $10k)
        Procesos depositoSospechoso = new Par(
                new Tell(new XeqC(incoming, 1)),
                new Tell(new XeqC(txnAmount, 9000))
        );

        // El pitufo ataca en las unidades 5, 10, 15 y 20
        Procesos ataquePitufeo = new Par(
                new Star(depositoSospechoso, 5, 5),
                new Star(depositoSospechoso, 10, 10),
                new Star(depositoSospechoso, 15, 15),
                new Star(depositoSospechoso, 20, 20)
        );

        // ============================================================
        //  LÓGICA DEL DETECTOR (El ajuste arquitectónico)
        // ============================================================

        // A. Monitor de Acumulación (Memoria)
        // Si entra dinero y es < 10,000, incrementamos el contador
        Procesos monitor = new Ask(
                new XeqC(incoming, 1),
                new Ask(
                        new XltC(txnAmount, 10000),
                        new Change(smurfCounter, Operation._INCRE, 1)
                )
        );

        // B. Reacción Paralela Sincronizada (PRUEBA DE CHOICEPAR + PARC)
        // Si el contador llega a 3, disparamos dos acciones concurrentes
        Procesos reaccionCritica = new Ask(
                new XgteqC(smurfCounter, 3),
                new ChoicePar(
                        // PRUEBA DE FUEGO: Usar Parc para envolver las acciones
                        // Hilo 1: Bloqueo inmediato
                        new Parc(new Tell(new XeqC(blockUser, 1))),
                        // Hilo 2: Reporte regulatorio (corre en paralelo real)
                        new Parc(new Tell(new XeqC(reportUIF, 1)))
                )
        );

        // ============================================================
        //  EJECUCIÓN
        // ============================================================
        // Usamos Bang para que los monitores vivan en cada unidad de tiempo
        Procesos sistema = new Bang(new Par(monitor, reaccionCritica));

        // Arrancamos el Planner
        planner.addProce(new Par(ataquePitufeo, sistema));
        planner.run();
    }
}