import java.util.ArrayList;
import java.util.Stack;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;

/*
    MATERIA: LEGUAJES Y AUTÓMATAS
    TEMA: Analizador Sistáctico
    NOMBRE DEL PROGRAMA: Analizador Sintáctico
    NOMBRE DEL ALUMNO: Eduardo De la Cruz Cortez
    FECHA: 17 de Mayo de 2026
*/

class Token {

    private String tipo;
    private String valor;
    private int linea;
    private int columna;

    public Token(String tipo, String valor, int linea, int columna) {
        this.tipo = tipo;
        this.valor = valor;
        this.linea = linea;
        this.columna = columna;
    }

    public String getTipo() {

        return tipo;

    }
    public String getValor() {

        return valor;

    }

    public int getLinea() {

        return linea;

    }

    public int getColumna() {

        return columna;

    }

    // Descripción legible del token para los mensajes de error
    public String describir() {
        return switch (tipo) {
            case "EOF" -> "el fin del archivo";
            case "ID"  -> "el identificador '" + valor + "'";
            case "NUM" -> "el número '" + valor + "'";
            case "STR" -> "la cadena " + valor;
            case "OP"  -> "el operador '" + valor + "'";
            default    -> Character.isLetter(tipo.charAt(0))
                    ? "la palabra reservada '" + valor + "'"
                    : "el símbolo '" + valor + "'";
        };
    }

    @Override
    public String toString() {
        return "[" + tipo + " : " + valor + " (línea " + linea + ", columna " + columna + ")]";
    }
}

class Simbolo {

    private String nombre;
    private String tipo;
    private String categoria;
    private String ambito;
    private int linea;
    private String valor;

    public Simbolo(String nombre, String tipo, String categoria, String ambito, int linea, String valor) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.categoria = categoria;
        this.ambito = ambito;
        this.linea = linea;
        this.valor = valor;
    }

    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getCategoria() { return categoria; }
    public String getAmbito() { return ambito; }
    public int getLinea() { return linea; }
    public String getValor() { return valor; }

    @Override
    public String toString() {
        return String.format("%-15s | %-8s | %-10s | %-12s | %-5d | %s",
                nombre, tipo, categoria, ambito, linea, valor);
    }
}

class TablaSimbolos {

    private ArrayList<Simbolo> simbolos = new ArrayList<>();
    private ArrayList<String> pilaAmbitos = new ArrayList<>();

    public TablaSimbolos() {
        pilaAmbitos.add("global");
    }

    public void entrarAmbito(String nombre) {
        pilaAmbitos.add(nombre);
    }

    public void salirAmbito() {
        if (pilaAmbitos.size() > 1) {
            pilaAmbitos.remove(pilaAmbitos.size() - 1);
        }
    }

    private String ambitoActual() {
        return pilaAmbitos.get(pilaAmbitos.size() - 1);
    }

    public String getAmbitoActual() { return ambitoActual(); }

    public Simbolo buscarEnAmbito(String nombre, String ambito) {
        for (int i = simbolos.size() - 1; i >= 0; i--) {
            Simbolo simbolo = simbolos.get(i);
            if (simbolo.getNombre().equals(nombre) && simbolo.getAmbito().equals(ambito)) return simbolo;
        }
        return null;
    }

    public Simbolo buscar(String nombre, String ambito) {
        Simbolo simbolo = buscarEnAmbito(nombre, ambito);
        return simbolo != null || ambito.equals("global") ? simbolo : buscarEnAmbito(nombre, "global");
    }

    public void agregar(String nombre, String tipo, String categoria, int linea, String valor) {
        simbolos.add(new Simbolo(nombre, tipo, categoria, ambitoActual(), linea, valor));
    }

    public ArrayList<Simbolo> getSimbolos() {
        return simbolos;
    }

    public void imprimir() {
        System.out.println("\n=== Tabla de símbolos ===");
        System.out.printf("%-15s | %-8s | %-10s | %-12s | %-5s | %s%n",
                "NOMBRE", "TIPO", "CATEGORIA", "AMBITO", "LINEA", "VALOR");
        for (Simbolo s : simbolos) {
            System.out.println(s);
        }
    }

    public void imprimirArchivo(String ruta) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(ruta))) {
            writer.println("------------------------------------------------------------------------------");
            writer.printf("%-15s | %-8s | %-10s | %-12s | %-5s | %s%n",
                    "NOMBRE", "TIPO", "CATEGORIA", "AMBITO", "LINEA", "VALOR");
            writer.println("------------------------------------------------------------------------------");
            for (Simbolo s : simbolos) {
                writer.println(s);
            }
            writer.println("------------------------------------------------------------------------------");
            System.out.println("Archivo guardado exitosamente en: " + ruta);
        } catch (IOException e) {
            System.out.println("Error al crear el archivo de la tabla de símbolos: " + e.getMessage());
        }
    }
}

// Verifica, con una pila, que cada "(" tenga su ")" y cada "[" tenga su "]",
// respetando el orden de apertura/cierre, antes de iniciar el análisis sintáctico.
// Reporta todos los delimitadores sin pareja (no sólo el primero).
class ValidadorDelimitadores {

    public boolean verificar(ArrayList<Token> tokens) {
        Stack<Token> pila = new Stack<>();
        boolean correcto = true;

        for (Token t : tokens) {
            String tipo = t.getTipo();
            if (tipo.equals("[")) {
                // En el lenguaje un bloque nunca va dentro de paréntesis,
                // así que los '(' pendientes antes de un '[' quedaron sin cerrar
                while (!pila.isEmpty() && pila.peek().getTipo().equals("(")) {
                    Token abierto = pila.pop();
                    Errores.reportar(CodigoError.DELIMITADOR_SIN_CIERRE, abierto.getLinea(), abierto.getColumna(), 1,
                            "'(' no se cerró: falta ')' antes del '[' de la línea " + t.getLinea());
                    correcto = false;
                }
                pila.push(t);
            } else if (tipo.equals("(")) {
                pila.push(t);
            } else if (tipo.equals(")") || tipo.equals("]")) {
                String apertura = tipo.equals(")") ? "(" : "[";

                if (!contieneApertura(pila, apertura)) {
                    Errores.reportar(CodigoError.DELIMITADOR_SIN_APERTURA, t.getLinea(), t.getColumna(), 1,
                            "Se encontró '" + tipo + "' sin un '" + apertura + "' que le corresponda");
                    correcto = false;
                    continue;
                }

                // Los delimitadores abiertos después de la apertura correspondiente nunca se cerraron
                while (!pila.peek().getTipo().equals(apertura)) {
                    Token abierto = pila.pop();
                    Errores.reportar(CodigoError.DELIMITADOR_SIN_CIERRE, abierto.getLinea(), abierto.getColumna(), 1,
                            "'" + abierto.getValor() + "' no se cerró: falta '" + cierreDe(abierto.getTipo())
                            + "' antes del '" + tipo + "' de la línea " + t.getLinea());
                    correcto = false;
                }
                pila.pop();
            }
        }

        while (!pila.isEmpty()) {
            Token abierto = pila.pop();
            Errores.reportar(CodigoError.DELIMITADOR_SIN_CIERRE, abierto.getLinea(), abierto.getColumna(), 1,
                    "'" + abierto.getValor() + "' nunca se cerró: falta '" + cierreDe(abierto.getTipo()) + "'");
            correcto = false;
        }
        return correcto;
    }

    // Busca la apertura desde el tope de la pila; un ')' no puede cerrar un '('
    // que esté fuera del bloque '[' actual
    private boolean contieneApertura(Stack<Token> pila, String apertura) {
        for (int k = pila.size() - 1; k >= 0; k--) {
            String tipo = pila.get(k).getTipo();
            if (tipo.equals(apertura)) return true;
            if (apertura.equals("(") && tipo.equals("[")) return false;
        }
        return false;
    }

    private String cierreDe(String apertura) {
        return apertura.equals("(") ? ")" : "]";
    }
}

/*
    Analizador sintáctico descendente recursivo con recuperación de errores
    en "modo pánico": al encontrar un error lo reporta, descarta tokens hasta
    un punto seguro (un ';', el inicio de otra sentencia o el fin de un bloque)
    y continúa, de modo que se reportan todos los errores en una sola ejecución.
*/
class Parser {

    // Se lanza al detectar un error para abandonar la sentencia actual
    private static class ErrorSintactico extends RuntimeException {
        ErrorSintactico() {
            super(null, null, false, false);
        }
    }

    private ArrayList<Token> tokens;
    private int i = 0;
    private TablaSimbolos tabla = new TablaSimbolos();
    private AnalizadorSemantico semantico = new AnalizadorSemantico(tabla);

    public Parser(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }

    public TablaSimbolos getTabla() {
        return tabla;
    }

    private String construirTexto(int desde, int hasta) {
        StringBuilder sb = new StringBuilder();
        for (int idx = desde; idx < hasta && idx < tokens.size(); idx++) {
            if (idx > desde) sb.append(" ");
            sb.append(tokens.get(idx).getValor());
        }
        return sb.toString();
    }

    private Token actual() {
        if (i < tokens.size()) return tokens.get(i);
        if (tokens.isEmpty()) return new Token("EOF", "", 1, 1);
        Token ultimo = tokens.get(tokens.size() - 1);
        return new Token("EOF", "", ultimo.getLinea(), ultimo.getColumna() + ultimo.getValor().length());
    }

    private Token anterior() {
        return i > 0 ? tokens.get(i - 1) : actual();
    }

    private boolean es(String tipo) {
        return actual().getTipo().equals(tipo);
    }

    private boolean esOperador(String valor) {
        return es("OP") && actual().getValor().equals(valor);
    }

    private boolean esInicioSentencia(String tipo) {
        return tipo.equals("DEF") || tipo.equals("IF") || tipo.equals("WHILE")
            || tipo.equals("FOR") || tipo.equals("FUNC") || tipo.equals("PRINT");
    }

    // PROGRAMA → S EOF
    public void programa() {
        while (!es("EOF")) {
            S();
            if (es("]")) {
                Errores.reportar(CodigoError.DELIMITADOR_SIN_APERTURA, actual().getLinea(), actual().getColumna(), 1,
                        "Se encontró ']' sin un bloque abierto");
                i++;
            }
        }
        semantico.publicarDiagnosticos();
    }

    // S → SENTENCIA S | ε   (se detiene en ']' o al final del archivo)
    public void S() {
        while (!es("EOF") && !es("]")) {
            int inicio = i;
            try {
                SENTENCIA();
            } catch (ErrorSintactico e) {
                // Garantiza avanzar al menos un token para no ciclar en el mismo error
                if (i == inicio && !es("[")) i++;
                sincronizar();
            }
        }
    }

    // Modo pánico: descarta tokens hasta después de un ';', antes del inicio
    // de otra sentencia o antes de un ']'. Los bloques [ ... ] de la sentencia
    // con error se saltan completos para no confundir su contenido.
    private void sincronizar() {
        while (!es("EOF")) {
            if (es(";")) {
                i++;
                return;
            }
            if (es("[")) {
                saltarBloque();
                continue;
            }
            if (es("]") || esInicioSentencia(actual().getTipo())) return;
            i++;
        }
    }

    private void saltarBloque() {
        int profundidad = 0;
        do {
            if (es("[")) profundidad++;
            else if (es("]")) profundidad--;
            i++;
        } while (profundidad > 0 && !es("EOF"));
    }

    // SENTENCIA → DEF_VAR | IF_EST | WHILE_EST | FOR_EST | FUNC_DEF | PRINT_EST
    private void SENTENCIA() {
        switch (actual().getTipo()) {
            case "DEF"   -> DEF_VAR();
            case "IF"    -> IF_EST();
            case "WHILE" -> WHILE_EST();
            case "FOR"   -> FOR_EST();
            case "FUNC"  -> FUNC_DEF();
            case "PRINT" -> PRINT_EST();
            case "ELSE"  -> error(CodigoError.SENTENCIA_INVALIDA,
                    "'else' sin un 'if' previo (el 'else' debe ir justo después del bloque ']' del 'if')");
            case "INT", "STRING", "FLOAT" -> error(CodigoError.SENTENCIA_INVALIDA,
                    "Falta 'def' antes del tipo '" + actual().getValor() + "' para declarar una variable");
            case "ID" -> error(CodigoError.SENTENCIA_INVALIDA,
                    "Una sentencia no puede iniciar con " + actual().describir()
                    + " (¿olvidaste 'def <tipo>' para declararla?)");
            default -> error(CodigoError.SENTENCIA_INVALIDA,
                    "Se esperaba el inicio de una sentencia (def, if, while, for, func o print), se encontró "
                    + actual().describir());
        }
    }

    // DEF_VAR → def TIPO ID = E ;
    private void DEF_VAR() {
        match("DEF");
        String tipoDecl = actual().getValor();
        if (!esTipo(actual().getTipo())) {
            errorEsperado(CodigoError.TIPO_ESPERADO, "el tipo de dato (int, string o float) después de 'def'");
        }
        TIPO();
        Token identificador = actual();
        String nombre = identificador.getValor();
        if (!es("ID")) {
            errorEsperado(CodigoError.IDENTIFICADOR_ESPERADO,
                    "el nombre de la variable (identificador terminado en '?') después de '" + tipoDecl + "'");
        }
        match("ID");
        if (!esOperador("=")) {
            errorEsperado(CodigoError.FALTA_ASIGNACION, "'=' después de la variable '" + nombre + "'");
        }
        match("OP");
        int inicioExp = i;
        String tipoExp = EXP("el valor de la variable '" + nombre + "'");
        String valorExp = construirTexto(inicioExp, i);
        semantico.validarAsignacion(tipoDecl, tipoExp, identificador);
        finSentencia("la declaración de '" + nombre + "'");
        semantico.declarar(identificador, tipoDecl, "Variable", valorExp);
    }

    // TIPO → int | string | float
    private void TIPO() {
        if (esTipo(actual().getTipo())) {
            i++;
        } else {
            errorEsperado(CodigoError.TIPO_ESPERADO, "un tipo de dato (int, string o float)");
        }
    }

    private boolean esTipo(String tipo) {
        return tipo.equals("INT") || tipo.equals("STRING") || tipo.equals("FLOAT");
    }

    // E → T E' con verificacion semantica de operandos
    private String EXP(String contexto) {
        String tipo = T(contexto);
        while (es("OP") && !actual().getValor().equals("=") && !esRelacional(actual().getValor())) {
            Token operador = actual();
            match("OP");
            Token tokenDerecho = actual();
            String tipoDerecho = T("operando después de '" + operador.getValor() + "'");
            semantico.validarDivision(operador, tokenDerecho);
            tipo = semantico.tipoResultado(operador, tipo, tipoDerecho);
        }
        return tipo;
    }

    private String T(String contexto) {
        if (es("ID") || es("NUM") || es("STR")) {
            Token token = actual();
            i++;
            return semantico.tipoDe(token);
        }
        errorEsperado(CodigoError.EXPRESION_INVALIDA, "un identificador, número o cadena como " + contexto);
        return null;
    }

    private boolean esRelacional(String valor) {
        return valor.equals(">") || valor.equals("<") || valor.equals(">=")
            || valor.equals("<=") || valor.equals("==") || valor.equals("!=");
    }

    // CONDICION → EXP ( OP_REL EXP )?
    private void CONDICION(String estructura) {
        if (es(")")) {
            error(CodigoError.CONDICION_INVALIDA, "La condición del '" + estructura + "' está vacía");
        }
        Token inicio = actual();
        String tipoIzquierdo = EXP("la condición del '" + estructura + "'");
        if (esOperador("=")) {
            error(CodigoError.CONDICION_INVALIDA,
                    "En la condición del '" + estructura + "' se usó '=' (asignación); para comparar usa '=='");
        }
        boolean tieneComparacion = false;
        if (es("OP") && esRelacional(actual().getValor())) {
            tieneComparacion = true;
            Token operador = actual();
            match("OP");
            String tipoDerecho = EXP("operando derecho de '" + operador.getValor() + "'");
            semantico.validarComparacion(operador, tipoIzquierdo, tipoDerecho);
        }
        semantico.validarCondicion(inicio, tieneComparacion);
    }

    // ENCABEZADO → ( CONDICION )
    private void ENCABEZADO(String estructura) {
        esperar("(", CodigoError.DELIMITADOR_ESPERADO, "'(' después de '" + estructura + "'");
        try {
            CONDICION(estructura);
            esperar(")", CodigoError.DELIMITADOR_ESPERADO, "')' para cerrar la condición del '" + estructura + "'");
        } catch (ErrorSintactico e) {
            // Recuperación local: se descarta la condición y se sigue con el bloque
            saltarHastaCierreParentesis();
        }
    }

    // Descarta tokens hasta consumir el ')' que cierra el paréntesis actual
    private void saltarHastaCierreParentesis() {
        int profundidad = 0;
        while (!es("EOF") && !es("[") && !es("]") && !es(";")) {
            if (es("(")) {
                profundidad++;
            } else if (es(")")) {
                if (profundidad == 0) {
                    i++;
                    return;
                }
                profundidad--;
            }
            i++;
        }
    }

    // BLOQUE → [ S ]
    private void BLOQUE(String estructura) {
        esperar("[", CodigoError.DELIMITADOR_ESPERADO, "'[' para abrir el bloque del '" + estructura + "'");
        S();
        esperar("]", CodigoError.DELIMITADOR_ESPERADO, "']' para cerrar el bloque del '" + estructura + "'");
    }

    // IF_EST → if ( CONDICION ) [ S ] ( else [ S ] )?
    private void IF_EST() {
        match("IF");
        ENCABEZADO("if");
        BLOQUE("if");
        if (es("ELSE")) {
            match("ELSE");
            BLOQUE("else");
        }
    }

    // WHILE_EST → while ( CONDICION ) [ S ]
    private void WHILE_EST() {
        match("WHILE");
        ENCABEZADO("while");
        BLOQUE("while");
    }

    // FOR_EST → for ( CONDICION ) [ S ]
    private void FOR_EST() {
        match("FOR");
        ENCABEZADO("for");
        BLOQUE("for");
    }

    // FUNC_DEF → func ID ( PARAMS ) [ S ]
    private void FUNC_DEF() {
        match("FUNC");
        Token identificadorFuncion = actual();
        String nombreFuncion = identificadorFuncion.getValor();
        int lineaFuncion = actual().getLinea();
        if (!es("ID")) {
            errorEsperado(CodigoError.IDENTIFICADOR_ESPERADO,
                    "el nombre de la función (identificador terminado en '?') después de 'func'");
        }
        match("ID");
        esperar("(", CodigoError.DELIMITADOR_ESPERADO, "'(' después del nombre de la función '" + nombreFuncion + "'");
        String ambitoFuncion = tabla.buscarEnAmbito(nombreFuncion, "global") == null
                ? nombreFuncion : nombreFuncion + "#" + lineaFuncion;
        tabla.entrarAmbito(ambitoFuncion);
        String textoParams = "";
        try {
            int inicioParams = i;
            try {
                PARAMS();
                textoParams = construirTexto(inicioParams, i);
                if (es("ID") || esTipo(actual().getTipo())) {
                    errorEsperado(CodigoError.PARAMETRO_INVALIDO, "',' entre los parámetros de la función '" + nombreFuncion + "'");
                }
                esperar(")", CodigoError.DELIMITADOR_ESPERADO, "')' para cerrar los parámetros de '" + nombreFuncion + "'");
            } catch (ErrorSintactico e) {
                // Recuperación local: se descartan los parámetros y se analiza el cuerpo
                saltarHastaCierreParentesis();
            }
            BLOQUE("func " + nombreFuncion);
        } finally {
            // Aunque haya error, se sale del ámbito para no dejar la tabla inconsistente
            tabla.salirAmbito();
        }
        semantico.declarar(identificadorFuncion, "-", "Funcion", "(" + textoParams + ")");
    }

    // PARAMS → PARAM PARAMS' | ε
    private void PARAMS() {
        if (es("ID")) {
            error(CodigoError.PARAMETRO_INVALIDO,
                    "Al parámetro '" + actual().getValor() + "' le falta el tipo de dato (int, string o float)");
        }
        if (esTipo(actual().getTipo())) {
            PARAM();
            PARAMS_PRIMA();
        }
    }

    // PARAMS' → , PARAM PARAMS' | ε
    private void PARAMS_PRIMA() {
        if (es(",")) {
            match(",");
            if (es(")")) {
                error(CodigoError.PARAMETRO_INVALIDO, "Se esperaba otro parámetro después de ','");
            }
            if (es("ID")) {
                error(CodigoError.PARAMETRO_INVALIDO,
                        "Al parámetro '" + actual().getValor() + "' le falta el tipo de dato (int, string o float)");
            }
            PARAM();
            PARAMS_PRIMA();
        }
    }

    // PARAM → TIPO ID
    private void PARAM() {
        String tipoParam = actual().getValor();
        if (!esTipo(actual().getTipo())) {
            errorEsperado(CodigoError.PARAMETRO_INVALIDO, "el tipo del parámetro (int, string o float)");
        }
        TIPO();
        Token identificadorParam = actual();
        String nombreParam = identificadorParam.getValor();
        if (!es("ID")) {
            errorEsperado(CodigoError.PARAMETRO_INVALIDO, "el nombre del parámetro después de '" + tipoParam + "'");
        }
        match("ID");
        semantico.declarar(identificadorParam, tipoParam, "Parametro", "");
    }

    // PRINT_EST → print ( EXP ) ;
    private void PRINT_EST() {
        match("PRINT");
        esperar("(", CodigoError.DELIMITADOR_ESPERADO, "'(' después de 'print'");
        EXP("el valor a imprimir");
        esperar(")", CodigoError.DELIMITADOR_ESPERADO, "')' para cerrar el 'print'");
        finSentencia("la sentencia 'print'");
    }

    private void match(String tipoEsperado) {
        if (es(tipoEsperado)) {
            i++;
        } else {
            errorEsperado(CodigoError.SENTENCIA_INVALIDA, "'" + tipoEsperado + "'");
        }
    }

    private void esperar(String tipoEsperado, CodigoError codigo, String descripcion) {
        if (es(tipoEsperado)) {
            i++;
        } else {
            errorEsperado(codigo, descripcion);
        }
    }

    // El ';' faltante se señala justo después del último token de la sentencia,
    // que es donde debería ir (y no en la línea siguiente).
    private void finSentencia(String sentencia) {
        if (es(";")) {
            i++;
            return;
        }
        if (es("OP") && esRelacional(actual().getValor())) {
            error(CodigoError.EXPRESION_INVALIDA, "El operador relacional '" + actual().getValor()
                    + "' sólo puede usarse en condiciones de if, while o for");
        }
        Token ultimo = anterior();
        Errores.reportar(CodigoError.FALTA_PUNTO_Y_COMA, ultimo.getLinea(),
                ultimo.getColumna() + ultimo.getValor().length(), 1,
                "Falta ';' al final de " + sentencia + "; después se encontró " + actual().describir()
                + (actual().getLinea() != ultimo.getLinea() ? " en la línea " + actual().getLinea() : ""));
        throw new ErrorSintactico();
    }

    private void errorEsperado(CodigoError codigo, String esperado) {
        error(codigo, "Se esperaba " + esperado + ", se encontró " + actual().describir());
    }

    private void error(CodigoError codigo, String mensaje) {
        Token t = actual();
        Errores.reportar(codigo, t.getLinea(), t.getColumna(), t.getValor().length(), mensaje);
        throw new ErrorSintactico();
    }
}

class Conversor {

    // Convierte los lexemas en tokens. Los lexemas no válidos se diagnostican
    // (reportando su error léxico) y se sustituyen por los tokens recuperados.
    public ArrayList<Token> convertir(ArrayList<String> lexemas, ArrayList<Integer> lineas, ArrayList<Integer> columnas) {

        DiagnosticoLexico diagnostico = new DiagnosticoLexico();
        ArrayList<Token> tokens = new ArrayList<>();

        for (int idx = 0; idx < lexemas.size(); idx++) {
            String lexema = lexemas.get(idx);
            if (lexema.isEmpty()) continue;
            tokens.addAll(diagnostico.analizar(lexema, lineas.get(idx), columnas.get(idx)));
        }

        return tokens;
    }
}
