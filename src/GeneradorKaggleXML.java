import java.io.*;
import java.util.Scanner;

public class GeneradorKaggleXML {
    public static void main(String[] args) {
        // Debes colocar el dataset descargado en la raíz del proyecto
        String archivoCSV = "creditcard.csv";
        String archivoXML = "FraudeMasivo.xml";

        try (PrintWriter writer = new PrintWriter(new FileWriter(archivoXML));
             Scanner scanner = new Scanner(new File(archivoCSV))) {

            writer.println("<list>");
            if (scanner.hasNextLine()) scanner.nextLine(); // Saltar cabecera

            while (scanner.hasNextLine()) {
                String[] columnas = scanner.nextLine().split(",");
                if (columnas.length > 30) {
                    // Mapeo estándar de Kaggle: 'Time' (Columna 0) y 'Amount' (Columna 29)
                    int unidadTiempo = (int) Double.parseDouble(columnas[0].trim());
                    int montoTx = (int) Double.parseDouble(columnas[29].trim());

                    if (montoTx > 0) { // Filtro para optimizar la lista varsEnviroment del Ambient
                        writer.println("  <constraintNtcc>");
                        writer.println("    <varname1>montoTx</varname1>");
                        writer.println("    <varConst>" + montoTx + "</varConst>");
                        writer.println("    <unitTime>" + unidadTiempo + "</unitTime>");
                        writer.println("    <paramvarx class=\"JaCoP.core.IntVar\">");
                        writer.println("      <id>montoTx</id><index>-1</index><weight>1</weight>");
                        writer.println("    </paramvarx>");
                        writer.println("    <constraint class=\"JaCoP.constraints.XeqC\">");
                        writer.println("      <c>" + montoTx + "</c>");
                        writer.println("    </constraint>");
                        writer.println("  </constraintNtcc>");
                    }
                }
            }
            writer.println("</list>");
            System.out.println("Archivo de entorno XML generado masivamente.");
        } catch (IOException e) {
            System.err.println("Error de lectura: " + e.getMessage());
        }
    }
}