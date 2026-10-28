import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.StringJoiner;

class CodigoIntermedio {

    private record Condicion(String izquierdo, String operador, String derecho) {}

    private final ArrayList<Token> tokens;
    private final ArrayList<String> instrucciones = new ArrayList<>();
    private int posicion;
    private int temporal = 1;
    private int etiqueta = 1;

    CodigoIntermedio(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }

    void generar() {
        posicion = 0;
        temporal = 1;
        etiqueta = 1;
        instrucciones.clear();
        instrucciones.add("program:");
        while (!es("EOF")) {
            sentencia();
        }
        instrucciones.add("end program");
    }

    ArrayList<String> getInstrucciones() {
        return new ArrayList<>(instrucciones);
    }

    void imprimir() {
        System.out.println("\n=== Código intermedio (tres direcciones) ===");
        for (String instruccion : instrucciones) {
            System.out.println(instruccion);
        }
    }

    void guardar(String ruta) throws IOException {
        String contenido = String.join(System.lineSeparator(), instrucciones) + System.lineSeparator();
        Files.writeString(Path.of(ruta), contenido);
    }

    private void sentencia() {
        switch (tipo()) {
            case "DEF" -> declaracion();
            case "PRINT" -> imprimirValor();
            case "IF" -> seleccion();
            case "WHILE", "FOR" -> ciclo();
            case "FUNC" -> funcion();
            default -> throw new IllegalStateException(
                    "Token inesperado al generar código intermedio: " + actual().describir());
        }
    }

    private void declaracion() {
        consumir("DEF");
        consumirTipo();
        String destino = consumir("ID").getValor();
        consumirOperador("=");
        String valor = expresion();
        consumir(";");
        emitir(destino + " = " + valor);
    }

    private void imprimirValor() {
        consumir("PRINT");
        consumir("(");
        String valor = expresion();
        consumir(")");
        consumir(";");
        emitir("print " + valor);
    }

    private void seleccion() {
        consumir("IF");
        Condicion condicion = encabezadoCondicional();
        String etiquetaElse = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        emitir("ifFalse " + condicion.izquierdo() + " " + condicion.operador() + " "
                + condicion.derecho() + " goto " + etiquetaElse);
        bloque();
        if (es("ELSE")) {
            consumir("ELSE");
            emitir("goto " + etiquetaFin);
            emitir(etiquetaElse + ":");
            bloque();
            emitir(etiquetaFin + ":");
        } else {
            emitir(etiquetaElse + ":");
        }
    }

    private void ciclo() {
        consumir(tipo());
        String etiquetaInicio = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();
        emitir(etiquetaInicio + ":");
        Condicion condicion = encabezadoCondicional();
        emitir("ifFalse " + condicion.izquierdo() + " " + condicion.operador() + " "
                + condicion.derecho() + " goto " + etiquetaFin);
        bloque();
        emitir("goto " + etiquetaInicio);
        emitir(etiquetaFin + ":");
    }

    private void funcion() {
        consumir("FUNC");
        String nombre = consumir("ID").getValor();
        consumir("(");
        StringJoiner parametros = new StringJoiner(" ");
        while (!es(")")) {
            Token token = actual();
            if (es("EOF")) {
                throw new IllegalStateException("Fin inesperado al leer los parámetros de " + nombre);
            }
            parametros.add(token.getValor());
            posicion++;
        }
        consumir(")");
        consumir("[");

        String etiquetaContinuacion = nuevaEtiqueta();
        emitir("goto " + etiquetaContinuacion);
        emitir("func " + nombre + "(" + parametros + "):");
        while (!es("]")) {
            if (es("EOF")) {
                throw new IllegalStateException("Fin inesperado dentro de la función " + nombre);
            }
            sentencia();
        }
        consumir("]");
        emitir("endfunc");
        emitir(etiquetaContinuacion + ":");
    }

    private Condicion encabezadoCondicional() {
        consumir("(");
        String izquierdo = expresion();
        Token operador = consumir("OP");
        if (!esRelacional(operador.getValor())) {
            throw new IllegalStateException("Se esperaba un operador relacional, se encontró " + operador.getValor());
        }
        String derecho = expresion();
        consumir(")");
        return new Condicion(izquierdo, operador.getValor(), derecho);
    }

    private void bloque() {
        consumir("[");
        while (!es("]")) {
            if (es("EOF")) {
                throw new IllegalStateException("Fin inesperado dentro de un bloque");
            }
            sentencia();
        }
        consumir("]");
    }

    private String expresion() {
        String resultado = operando();
        while (es("OP") && esOperadorAritmetico(actual().getValor())) {
            String operador = consumir("OP").getValor();
            String derecho = operando();
            String destino = "t" + temporal++;
            emitir(destino + " = " + resultado + " " + operador + " " + derecho);
            resultado = destino;
        }
        return resultado;
    }

    private String operando() {
        if (es("ID") || es("NUM") || es("STR")) {
            return tokens.get(posicion++).getValor();
        }
        throw new IllegalStateException("Se esperaba un operando, se encontró " + actual().describir());
    }

    private Token consumir(String tipoEsperado) {
        if (!es(tipoEsperado)) {
            throw new IllegalStateException("Se esperaba " + tipoEsperado + ", se encontró " + actual().describir());
        }
        return tokens.get(posicion++);
    }

    private void consumirTipo() {
        if (es("INT") || es("FLOAT") || es("STRING")) {
            posicion++;
            return;
        }
        throw new IllegalStateException("Se esperaba un tipo de dato, se encontró " + actual().describir());
    }

    private void consumirOperador(String operador) {
        if (!es("OP") || !actual().getValor().equals(operador)) {
            throw new IllegalStateException("Se esperaba el operador '" + operador + "'");
        }
        posicion++;
    }

    private void emitir(String instruccion) {
        instrucciones.add("    " + instruccion);
    }

    private String nuevaEtiqueta() {
        return "L" + etiqueta++;
    }

    private boolean es(String tipoEsperado) {
        return tipo().equals(tipoEsperado);
    }

    private String tipo() {
        return posicion < tokens.size() ? tokens.get(posicion).getTipo() : "EOF";
    }

    private Token actual() {
        if (posicion < tokens.size()) {
            return tokens.get(posicion);
        }
        if (tokens.isEmpty()) {
            return new Token("EOF", "", 1, 1);
        }
        Token ultimo = tokens.get(tokens.size() - 1);
        return new Token("EOF", "", ultimo.getLinea(), ultimo.getColumna() + ultimo.getValor().length());
    }

    private boolean esOperadorAritmetico(String operador) {
        return operador.equals("+") || operador.equals("-") || operador.equals("*")
                || operador.equals("/") || operador.equals("&") || operador.equals("|");
    }

    private boolean esRelacional(String operador) {
        return operador.equals(">") || operador.equals("<") || operador.equals(">=")
                || operador.equals("<=") || operador.equals("==") || operador.equals("!=");
    }
}
