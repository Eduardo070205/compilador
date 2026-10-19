import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
    Control centralizado de errores del compilador.

    En lugar de detenerse en el primer error, cada fase (léxica y sintáctica)
    reporta aquí todos los errores que encuentra. Al final se imprimen
    ordenados por línea, con el fragmento del código fuente señalado y un
    resumen, y se guardan en un archivo.
*/

enum Fase {
    LEXICO("léxico"),
    SINTACTICO("sintáctico");

    private final String nombre;

    Fase(String nombre) { this.nombre = nombre; }

    public String getNombre() { return nombre; }
}

// Catálogo de errores que puede detectar el compilador.
enum CodigoError {

    // ── Errores léxicos ──────────────────────────────────────────
    CARACTER_NO_RECONOCIDO     ("L01", Fase.LEXICO, "Carácter no reconocido"),
    CADENA_SIN_CERRAR          ("L02", Fase.LEXICO, "Cadena de texto sin comilla de cierre"),
    IDENTIFICADOR_INICIA_DIGITO("L03", Fase.LEXICO, "Identificador que inicia con dígito"),
    IDENTIFICADOR_SIN_TERMINADOR("L04", Fase.LEXICO, "Identificador sin '?' final"),
    PALABRA_RESERVADA_MAL_ESCRITA("L05", Fase.LEXICO, "Palabra reservada mal escrita"),
    NUMERO_MAL_FORMADO         ("L06", Fase.LEXICO, "Número mal formado"),
    TOKENS_SIN_SEPARAR         ("L07", Fase.LEXICO, "Tokens sin separar por espacio"),
    OPERADOR_NO_VALIDO         ("L08", Fase.LEXICO, "Operador no válido"),

    // ── Errores sintácticos ──────────────────────────────────────
    SENTENCIA_INVALIDA         ("S01", Fase.SINTACTICO, "Sentencia no válida"),
    FALTA_PUNTO_Y_COMA         ("S02", Fase.SINTACTICO, "Falta ';'"),
    TIPO_ESPERADO              ("S03", Fase.SINTACTICO, "Se esperaba un tipo de dato"),
    IDENTIFICADOR_ESPERADO     ("S04", Fase.SINTACTICO, "Se esperaba un identificador"),
    FALTA_ASIGNACION           ("S05", Fase.SINTACTICO, "Falta '='"),
    EXPRESION_INVALIDA         ("S06", Fase.SINTACTICO, "Expresión inválida o incompleta"),
    DELIMITADOR_ESPERADO       ("S07", Fase.SINTACTICO, "Falta un delimitador"),
    CONDICION_INVALIDA         ("S08", Fase.SINTACTICO, "Condición inválida"),
    PARAMETRO_INVALIDO         ("S09", Fase.SINTACTICO, "Parámetro inválido"),
    DELIMITADOR_SIN_APERTURA   ("S10", Fase.SINTACTICO, "Delimitador de cierre sin apertura"),
    DELIMITADOR_SIN_CIERRE     ("S11", Fase.SINTACTICO, "Delimitador sin cerrar");

    private final String codigo;
    private final Fase fase;
    private final String titulo;

    CodigoError(String codigo, Fase fase, String titulo) {
        this.codigo = codigo;
        this.fase = fase;
        this.titulo = titulo;
    }

    public String getCodigo() { return codigo; }
    public Fase getFase() { return fase; }
    public String getTitulo() { return titulo; }
}

record ErrorCompilacion(CodigoError codigo, int linea, int columna, int longitud, String mensaje) {}

public class Errores {

    private static final ArrayList<ErrorCompilacion> errores = new ArrayList<>();
    private static List<String> fuente = new ArrayList<>();
    private static int omitidos = 0;

    // Líneas del código fuente, para mostrar el fragmento donde está cada error.
    public static void setFuente(List<String> lineas) {
        fuente = lineas;
    }

    public static void reportar(CodigoError codigo, int linea, int columna, int longitud, String mensaje) {
        for (ErrorCompilacion e : errores) {
            // Evita reportar dos veces el mismo error en la misma posición
            if (e.codigo() == codigo && e.linea() == linea && e.columna() == columna) return;
            // Un error sintáctico en una línea que ya tiene un error léxico casi
            // siempre es consecuencia de éste (errores en cascada): se omite.
            if (codigo.getFase() == Fase.SINTACTICO && e.codigo().getFase() == Fase.LEXICO && e.linea() == linea) {
                omitidos++;
                return;
            }
        }
        errores.add(new ErrorCompilacion(codigo, linea, columna, Math.max(1, longitud), mensaje));
    }

    public static boolean hayErrores() {
        return !errores.isEmpty();
    }

    public static int contar(Fase fase) {
        int total = 0;
        for (ErrorCompilacion e : errores) {
            if (e.codigo().getFase() == fase) total++;
        }
        return total;
    }

    private static List<ErrorCompilacion> ordenados() {
        ArrayList<ErrorCompilacion> lista = new ArrayList<>(errores);
        lista.sort(Comparator.comparing((ErrorCompilacion e) -> e.codigo().getFase())
                .thenComparingInt(ErrorCompilacion::linea)
                .thenComparingInt(ErrorCompilacion::columna));
        return lista;
    }

    public static void imprimir() {
        System.out.println("\n=== Errores de compilación ===");
        for (ErrorCompilacion e : ordenados()) {
            System.out.println(formatear(e));
        }
        System.out.println(resumen());
    }

    public static void guardar(String ruta) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(ruta))) {
            writer.println("------------------------------------------------------------------------------");
            if (errores.isEmpty()) {
                writer.println("Compilación sin errores.");
            } else {
                for (ErrorCompilacion e : ordenados()) {
                    writer.println(formatear(e));
                }
                writer.println(resumen());
            }
            writer.println("------------------------------------------------------------------------------");
            System.out.println("Archivo guardado exitosamente en: " + ruta);
        } catch (IOException e) {
            System.out.println("Error al crear el archivo de errores: " + e.getMessage());
        }
    }

    private static String resumen() {
        int lexicos = contar(Fase.LEXICO);
        int sintacticos = contar(Fase.SINTACTICO);
        String texto = "\nTotal: " + errores.size() + " error(es) — "
                + lexicos + " léxico(s), " + sintacticos + " sintáctico(s)";
        if (omitidos > 0) {
            texto += "\n(Se omitieron " + omitidos + " error(es) sintáctico(s) en líneas con errores léxicos;"
                    + " corrige primero los léxicos y vuelve a compilar)";
        }
        return texto;
    }

    //  ✗ [L04] Error léxico en la línea 1, columna 9: Identificador sin '?' final
    //      El identificador 'contador' debe terminar con '?': 'contador?'
    //        1 | def int contador = 0 ;
    //          |         ^^^^^^^^
    private static String formatear(ErrorCompilacion e) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n✗ [").append(e.codigo().getCodigo()).append("] Error ")
          .append(e.codigo().getFase().getNombre())
          .append(" en la línea ").append(e.linea())
          .append(", columna ").append(e.columna())
          .append(": ").append(e.codigo().getTitulo());
        sb.append("\n    ").append(e.mensaje());

        if (e.linea() >= 1 && e.linea() <= fuente.size()) {
            String codigo = fuente.get(e.linea() - 1);
            String numero = String.valueOf(e.linea());
            sb.append("\n    ").append(numero).append(" | ").append(codigo);
            sb.append("\n    ").append(" ".repeat(numero.length())).append(" | ");
            // Respeta los tabuladores para que el marcador quede alineado
            for (int c = 0; c < e.columna() - 1; c++) {
                sb.append(c < codigo.length() && codigo.charAt(c) == '\t' ? '\t' : ' ');
            }
            sb.append("^".repeat(e.longitud()));
        }
        return sb.toString();
    }
}
