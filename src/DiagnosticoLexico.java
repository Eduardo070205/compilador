import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
    Diagnóstico y recuperación de errores léxicos.

    Por cada lexema no válido determina QUÉ error es (cadena sin cerrar,
    identificador sin '?', palabra reservada mal escrita, número mal formado,
    tokens pegados, etc.), lo reporta en Errores y devuelve los tokens que
    "probablemente" quiso escribir el programador. Así el análisis sintáctico
    puede continuar y reportar también sus errores en la misma ejecución.
*/
class DiagnosticoLexico {

    private static final Tokens CLASIFICADOR = new Tokens();

    // Separa un lexema en piezas candidatas a token, aunque estén mal formadas
    private static final Pattern PIEZA = Pattern.compile(
            "\"[^\"]*\"?"                          // cadena (posiblemente sin cerrar)
          + "|[0-9]+[a-zA-Z_][a-zA-Z0-9_]*\\??"    // identificador que inicia con dígito
          + "|[a-zA-Z_][a-zA-Z0-9_]*\\??"          // identificador o palabra reservada
          + "|\\.?[0-9][0-9.]*"                    // número (posiblemente mal formado)
          + "|[+\\-*/&=|!<>]+"                     // operador(es)
          + "|[\\[\\],;()]"                        // símbolo
          + "|.");                                 // cualquier otro carácter

    // Operadores que suelen escribirse por error y el operador correcto del lenguaje
    private static final Map<String, String> OPERADORES_SUGERIDOS = Map.of(
            "===", "==",
            "=>", ">=",
            "=<", "<=",
            "<>", "!=",
            "=!", "!=",
            "!==", "!=",
            "&&", "&",
            "||", "|");

    // Tipo de token de un lexema válido, o null si no es válido
    static String tipoValido(String lexema) {
        ArrayList<String> uno = new ArrayList<>(List.of(lexema));
        if (!CLASIFICADOR.palabraReservadas(uno).isEmpty()) return lexema.toUpperCase();
        if (!CLASIFICADOR.identificador(uno).isEmpty())     return "ID";
        if (!CLASIFICADOR.numero(uno).isEmpty())            return "NUM";
        if (!CLASIFICADOR.cadenaTexto(uno).isEmpty())       return "STR";
        if (!CLASIFICADOR.operador(uno).isEmpty())          return "OP";
        if (!CLASIFICADOR.simbolo(uno).isEmpty())           return lexema;
        return null;
    }

    // Convierte un lexema en token(s); si no es válido reporta el error y lo recupera
    public ArrayList<Token> analizar(String lexema, int linea, int columna) {
        ArrayList<Token> salida = new ArrayList<>();

        String tipo = tipoValido(lexema);
        if (tipo != null) {
            salida.add(new Token(tipo, lexema, linea, columna));
            return salida;
        }

        // Una comilla sin su pareja: la cadena se extiende hasta el fin de la línea
        if (lexema.startsWith("\"") && lexema.indexOf('"', 1) == -1) {
            recuperarCadena(lexema, linea, columna, salida);
            return salida;
        }

        ArrayList<String> piezas = new ArrayList<>();
        ArrayList<Integer> desplazamientos = new ArrayList<>();
        Matcher m = PIEZA.matcher(lexema);
        while (m.find()) {
            piezas.add(m.group());
            desplazamientos.add(m.start());
        }

        if (piezas.size() > 1) {
            Errores.reportar(CodigoError.TOKENS_SIN_SEPARAR, linea, columna, lexema.length(),
                    "'" + lexema + "' contiene varios tokens juntos; sepáralos con espacios: '"
                    + String.join(" ", piezas) + "'");
        }

        for (int k = 0; k < piezas.size(); k++) {
            String pieza = piezas.get(k);
            int col = columna + desplazamientos.get(k);
            String tipoPieza = tipoValido(pieza);
            if (tipoPieza != null) {
                salida.add(new Token(tipoPieza, pieza, linea, col));
            } else if (pieza.startsWith("\"")) {
                recuperarCadena(pieza, linea, col, salida);
            } else {
                diagnosticarPieza(pieza, linea, col, salida);
            }
        }
        return salida;
    }

    private void diagnosticarPieza(String pieza, int linea, int col, ArrayList<Token> salida) {

        if (pieza.matches("[0-9]+[a-zA-Z_][a-zA-Z0-9_]*\\??")) {
            String sugerido = pieza.replaceFirst("^[0-9]+", "");
            Errores.reportar(CodigoError.IDENTIFICADOR_INICIA_DIGITO, linea, col, pieza.length(),
                    "El identificador '" + pieza + "' no puede iniciar con un dígito; debe iniciar con una letra");
            salida.add(new Token("ID", sugerido.endsWith("?") ? sugerido : sugerido + "?", linea, col));
            return;
        }

        if (pieza.matches("[a-zA-Z_][a-zA-Z0-9_]*\\??")) {
            diagnosticarPalabra(pieza, linea, col, salida);
            return;
        }

        if (pieza.matches("\\.?[0-9][0-9.]*")) {
            Errores.reportar(CodigoError.NUMERO_MAL_FORMADO, linea, col, pieza.length(),
                    "El número '" + pieza + "' está mal formado; usa enteros (10) o decimales con un solo punto (3.5)");
            salida.add(new Token("NUM", pieza, linea, col));
            return;
        }

        if (pieza.matches("[+\\-*/&=|!<>]+")) {
            String sugerido = OPERADORES_SUGERIDOS.get(pieza);
            if (sugerido != null) {
                Errores.reportar(CodigoError.OPERADOR_NO_VALIDO, linea, col, pieza.length(),
                        "El operador '" + pieza + "' no existe en el lenguaje; ¿quisiste decir '" + sugerido + "'?");
                salida.add(new Token("OP", sugerido, linea, col));
            } else {
                Errores.reportar(CodigoError.OPERADOR_NO_VALIDO, linea, col, pieza.length(),
                        "El operador '" + pieza + "' no existe en el lenguaje (válidos: + - * / = == != < > <= >= & | !)");
            }
            return;
        }

        Errores.reportar(CodigoError.CARACTER_NO_RECONOCIDO, linea, col, pieza.length(),
                "El carácter '" + pieza + "' no pertenece al lenguaje");
    }

    // Palabras formadas por letras/dígitos/'_' que no son token válido
    private void diagnosticarPalabra(String pieza, int linea, int col, ArrayList<Token> salida) {

        if (pieza.contains("_")) {
            String limpio = pieza.replace("_", "");
            Errores.reportar(CodigoError.CARACTER_NO_RECONOCIDO, linea, col + pieza.indexOf('_'), 1,
                    "El carácter '_' no está permitido en identificadores ('" + pieza + "')");
            if (!limpio.isEmpty()) {
                salida.add(new Token("ID", limpio.endsWith("?") ? limpio : limpio + "?", linea, col));
            }
            return;
        }

        // Aquí la palabra no termina en '?' (si terminara, sería un identificador válido)
        String reservada = reservadaParecida(pieza);
        if (reservada != null) {
            if (pieza.equalsIgnoreCase(reservada)) {
                Errores.reportar(CodigoError.PALABRA_RESERVADA_MAL_ESCRITA, linea, col, pieza.length(),
                        "Las palabras reservadas se escriben en minúsculas: '" + pieza + "' → '" + reservada + "'");
            } else {
                Errores.reportar(CodigoError.PALABRA_RESERVADA_MAL_ESCRITA, linea, col, pieza.length(),
                        "'" + pieza + "' no es válido; ¿quisiste decir la palabra reservada '" + reservada
                        + "'? (si es un identificador, debe terminar en '?': '" + pieza + "?')");
            }
            salida.add(new Token(reservada.toUpperCase(), reservada, linea, col));
            return;
        }

        Errores.reportar(CodigoError.IDENTIFICADOR_SIN_TERMINADOR, linea, col, pieza.length(),
                "El identificador '" + pieza + "' debe terminar con '?': '" + pieza + "?'");
        salida.add(new Token("ID", pieza + "?", linea, col));
    }

    // La cadena sin cerrar llega hasta el final de la línea; si termina en ';'
    // se asume que el ';' es el fin de la sentencia y se recupera por separado.
    private void recuperarCadena(String lexema, int linea, int col, ArrayList<Token> salida) {
        String contenido = lexema.stripTrailing();
        Token puntoYComa = null;
        if (contenido.length() > 1 && contenido.endsWith(";")) {
            puntoYComa = new Token(";", ";", linea, col + contenido.length() - 1);
            contenido = contenido.substring(0, contenido.length() - 1).stripTrailing();
        }
        Errores.reportar(CodigoError.CADENA_SIN_CERRAR, linea, col, contenido.length(),
                "La cadena " + contenido + " no se cerró; agrega '\"' al final (una cadena no puede ocupar varias líneas)");
        salida.add(new Token("STR", contenido + "\"", linea, col));
        if (puntoYComa != null) salida.add(puntoYComa);
    }

    // Palabra reservada igual ignorando mayúsculas o a un error de tecleo de distancia
    private String reservadaParecida(String palabra) {
        String minuscula = palabra.toLowerCase();
        for (String r : Tokens.PALABRAS_RESERVADAS) {
            if (r.equals(minuscula)) return r;
        }
        if (palabra.length() < 3) return null;
        for (String r : Tokens.PALABRAS_RESERVADAS) {
            if (r.length() >= 3 && distancia(minuscula, r) <= 1) return r;
        }
        return null;
    }

    // Distancia de edición (inserción, borrado, sustitución y transposición de letras vecinas)
    private int distancia(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int x = 0; x <= a.length(); x++) d[x][0] = x;
        for (int y = 0; y <= b.length(); y++) d[0][y] = y;
        for (int x = 1; x <= a.length(); x++) {
            for (int y = 1; y <= b.length(); y++) {
                int costo = a.charAt(x - 1) == b.charAt(y - 1) ? 0 : 1;
                d[x][y] = Math.min(Math.min(d[x - 1][y] + 1, d[x][y - 1] + 1), d[x - 1][y - 1] + costo);
                if (x > 1 && y > 1 && a.charAt(x - 1) == b.charAt(y - 2) && a.charAt(x - 2) == b.charAt(y - 1)) {
                    d[x][y] = Math.min(d[x][y], d[x - 2][y - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
