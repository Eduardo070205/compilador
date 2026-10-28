import java.util.ArrayList;
import java.util.IdentityHashMap;

class AnalizadorSemantico {

    private record Diagnostico(CodigoError codigo, Token token, String mensaje) {}

    private final TablaSimbolos tabla;
    private final ArrayList<Diagnostico> diagnosticos = new ArrayList<>();
    private final IdentityHashMap<Simbolo, Integer> usos = new IdentityHashMap<>();
    private final IdentityHashMap<Simbolo, Token> identificadores = new IdentityHashMap<>();

    AnalizadorSemantico(TablaSimbolos tabla) { this.tabla = tabla; }

    void declarar(Token identificador, String tipo, String categoria, String valor) {
        String ambito = tabla.getAmbitoActual();
        Simbolo existente = tabla.buscarEnAmbito(identificador.getValor(), ambito);
        if (existente != null) {
            CodigoError codigo = CodigoError.DECLARACION_DUPLICADA;
            if (categoria.equals("Funcion")) {
                codigo = existente.getCategoria().equals("Funcion")
                        ? CodigoError.FUNCION_DUPLICADA : CodigoError.COLISION_FUNCION_VARIABLE;
            } else if (existente.getCategoria().equals("Funcion")) {
                codigo = CodigoError.COLISION_FUNCION_VARIABLE;
            } else if (categoria.equals("Parametro") && existente.getCategoria().equals("Parametro")) {
                codigo = CodigoError.PARAMETROS_DUPLICADOS;
            }
            reportar(codigo, identificador,
                    "'" + identificador.getValor() + "' ya fue declarado en el ambito '" + ambito
                            + "' en la linea " + existente.getLinea());
            return;
        }
        Simbolo global = tabla.buscarEnAmbito(identificador.getValor(), "global");
        if (!ambito.equals("global") && global != null && global.getCategoria().equals("Variable")
                && !categoria.equals("Funcion")) {
            reportar(CodigoError.SOMBREADO_VARIABLE_GLOBAL, identificador,
                    "'" + identificador.getValor() + "' oculta la variable global declarada en la linea "
                            + global.getLinea());
        }
        tabla.agregar(identificador.getValor(), tipo, categoria, identificador.getLinea(), valor);
        Simbolo declarado = tabla.buscarEnAmbito(identificador.getValor(), ambito);
        usos.put(declarado, 0);
        identificadores.put(declarado, identificador);
    }

    String tipoDe(Token token) {
        return switch (token.getTipo()) {
            case "NUM" -> token.getValor().contains(".") ? "float" : "int";
            case "STR" -> "string";
            case "ID" -> {
                Simbolo simbolo = tabla.buscar(token.getValor(), tabla.getAmbitoActual());
                if (simbolo == null) {
                    boolean fueraDeAmbito = tabla.getSimbolos().stream()
                            .anyMatch(s -> s.getNombre().equals(token.getValor())
                                    && !s.getAmbito().equals("global")
                                    && !s.getCategoria().equals("Funcion"));
                    CodigoError codigo = fueraDeAmbito
                            ? CodigoError.IDENTIFICADOR_FUERA_AMBITO : CodigoError.VARIABLE_NO_DECLARADA;
                    reportar(codigo, token, fueraDeAmbito
                            ? "El identificador '" + token.getValor() + "' solo existe dentro de otro ambito"
                            : "El identificador '" + token.getValor() + "' no esta declarado ni es visible en este ambito");
                    yield null;
                }
                if (simbolo.getCategoria().equals("Funcion")) {
                    reportar(CodigoError.FUNCION_NO_UTILIZABLE_COMO_VALOR, token,
                            "La funcion '" + token.getValor() + "' no puede usarse como valor; la gramatica no define llamadas a funciones");
                    yield null;
                }
                usos.put(simbolo, usos.getOrDefault(simbolo, 0) + 1);
                yield simbolo.getTipo();
            }
            default -> null;
        };
    }

    void validarAsignacion(String destino, String origen, Token identificador) {
        if (origen != null && destino.equals("int") && origen.equals("float")) {
            reportar(CodigoError.PERDIDA_PRECISION, identificador,
                    "Se asigna un valor float a la variable int '" + identificador.getValor()
                            + "'; puede perderse la parte decimal");
        } else if (origen != null && !(destino.equals(origen) || (destino.equals("float") && origen.equals("int")))) {
            reportar(CodigoError.TIPOS_INCOMPATIBLES, identificador,
                    "No se puede asignar un valor de tipo '" + origen + "' a la variable '"
                            + identificador.getValor() + "' de tipo '" + destino + "'");
        }
    }

    void validarDivision(Token operador, Token divisor) {
        if (operador.getValor().equals("/") && divisor.getTipo().equals("NUM")
                && divisor.getValor().matches("0+(\\.0+)?")) {
            reportar(CodigoError.DIVISION_ENTRE_CERO, operador,
                    "El divisor de '/' es el literal cero");
        }
    }

    void validarCondicion(Token inicio, boolean tieneComparacion) {
        if (!tieneComparacion) {
            reportar(CodigoError.CONDICION_SIN_COMPARACION, inicio,
                    "La condición debe incluir una comparación relacional");
        }
    }

    String tipoResultado(Token operador, String izquierdo, String derecho) {
        if (izquierdo == null || derecho == null) return null;
        String op = operador.getValor();
        if (op.equals("+") && izquierdo.equals("string") && derecho.equals("string")) return "string";
        if (esNumerico(izquierdo) && esNumerico(derecho)
                && (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/"))) {
            return izquierdo.equals("float") || derecho.equals("float") ? "float" : "int";
        }
        if ((op.equals("&") || op.equals("|")) && izquierdo.equals("int") && derecho.equals("int")) return "int";
        reportar(CodigoError.OPERADOR_TIPOS_INVALIDOS, operador,
                "El operador '" + op + "' no admite operandos de tipo '" + izquierdo + "' y '" + derecho + "'");
        return null;
    }

    void validarComparacion(Token operador, String izquierdo, String derecho) {
        if (izquierdo == null || derecho == null) return;
        String op = operador.getValor();
        boolean orden = op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=");
        boolean comparables = izquierdo.equals(derecho) || (esNumerico(izquierdo) && esNumerico(derecho));
        if (!(orden ? esNumerico(izquierdo) && esNumerico(derecho) : comparables)) {
            reportar(CodigoError.COMPARACION_TIPOS_INVALIDOS, operador,
                    "No se pueden comparar valores de tipo '" + izquierdo + "' y '" + derecho + "' con '" + op + "'");
        }
    }

    void publicarDiagnosticos() {
        for (Simbolo simbolo : usos.keySet()) {

            /*
            if (simbolo.getCategoria().equals("Variable") && usos.get(simbolo) == 0) {
                reportar(CodigoError.VARIABLE_NO_UTILIZADA,
                        identificadores.get(simbolo),
                        "La variable '" + simbolo.getNombre() + "' fue declarada pero nunca se utiliza");
            } else

             */
            if (simbolo.getCategoria().equals("Parametro") && usos.get(simbolo) == 0) {

                reportar(CodigoError.PARAMETRO_NO_UTILIZADO,
                        identificadores.get(simbolo),
                        "El parámetro '" + simbolo.getNombre() + "' no se utiliza en el cuerpo de la función");
            }
        }
        for (Diagnostico diagnostico : diagnosticos) {
            Token token = diagnostico.token();
            Errores.reportar(diagnostico.codigo(), token.getLinea(), token.getColumna(),
                    token.getValor().length(), diagnostico.mensaje());
        }
        diagnosticos.clear();
    }

    private boolean esNumerico(String tipo) { return tipo.equals("int") || tipo.equals("float"); }

    private void reportar(CodigoError codigo, Token token, String mensaje) {
        diagnosticos.add(new Diagnostico(codigo, token, mensaje));
    }
}