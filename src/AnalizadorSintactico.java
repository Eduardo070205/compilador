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

    public Token(String tipo, String valor, int linea) {
        this.tipo = tipo;
        this.valor = valor;
        this.linea = linea;
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

    @Override
    public String toString() {
        return "[" + tipo + " : " + valor + " (línea " + linea + ")]";
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
class ValidadorDelimitadores {

    public void verificar(ArrayList<Token> tokens) {
        Stack<Token> pila = new Stack<>();

        for (Token t : tokens) {
            String tipo = t.getTipo();
            if (tipo.equals("(") || tipo.equals("[")) {
                pila.push(t);
            } else if (tipo.equals(")") || tipo.equals("]")) {
                if (pila.isEmpty()) {
                    System.out.println("\n✗ Error sintáctico en la linea :" + t.getLinea());
                    System.out.println("  Se encontró \"" + t.getValor() + "\" sin una apertura correspondiente");
                    System.exit(1);
                }
                Token apertura = pila.pop();
                String cierreEsperado = apertura.getTipo().equals("(") ? ")" : "]";
                if (!tipo.equals(cierreEsperado)) {
                    System.out.println("\n✗ Error sintáctico en la linea :" + t.getLinea());
                    System.out.println("  Se esperaba \"" + cierreEsperado + "\" para cerrar \"" + apertura.getValor()
                            + "\" abierto en la linea " + apertura.getLinea() + ", se encontró \"" + t.getValor() + "\"");
                    System.exit(1);
                }
            }
        }

        if (!pila.isEmpty()) {
            System.out.println("\n✗ Error sintáctico: quedaron delimitadores sin cerrar");
            while (!pila.isEmpty()) {
                Token apertura = pila.pop();
                System.out.println("  \"" + apertura.getValor() + "\" abierto en la linea " + apertura.getLinea() + " nunca se cerró");
            }
            System.exit(1);
        }
    }
}

class Parser {

    private ArrayList<Token> tokens;
    private int i = 0;
    private int ultimaLinea = 0;
    private TablaSimbolos tabla = new TablaSimbolos();

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
        return new Token("EOF", "", ultimaLinea);
    }

    // S → SENTENCIA | SENTENCIA S
    public void S() {

        if (actual().getTipo().equals("EOF")){
            return;
        }
        if (actual().getTipo().equals("]")){
            return;
        }
        SENTENCIA();
        if (!actual().getTipo().equals("EOF") && !actual().getTipo().equals("]")) {
            S();
        }
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
            default -> error("def, if, while, for, func o print");
        }
    }

    // DEF_VAR → def TIPO ID = E ;
    private void DEF_VAR() {
        match("DEF");
        String tipoDecl = actual().getValor();
        TIPO();
        String nombre = actual().getValor();
        int lineaDecl = actual().getLinea();
        match("ID");
        match("OP");
        int inicioExp = i;
        EXP();
        String valorExp = construirTexto(inicioExp, i);
        match(";");
        tabla.agregar(nombre, tipoDecl, "Variable", lineaDecl, valorExp);
        //System.out.println("Declaración de variable válida");
    }

    // TIPO → int | string | float
    private void TIPO() {
        if (esTipo(actual().getTipo())) {
            //System.out.println("  match(TIPO) ← " + actual().getValor());
            ultimaLinea = actual().getLinea();
            i++;
        } else {
            error("int, string o float");
        }
    }

    private boolean esTipo(String tipo) {
        return tipo.equals("INT") || tipo.equals("STRING") || tipo.equals("FLOAT");
    }

    // E → T E'
    private void EXP() {
        T();
        EXP_PRIMA();
    }

    // T → ID | NUM | STR
    private void T() {
        if (actual().getTipo().equals("ID")){
            match("ID");  return;
        }
        if (actual().getTipo().equals("NUM")){
            match("NUM"); return;
        }
        if (actual().getTipo().equals("STR")){
            match("STR"); return;
        }
        error("identificador, número o cadena");
    }

    // E' → OP T E' | ε  (OP aritmético; excluye "=" y los relacionales)
    private void EXP_PRIMA() {
        if (actual().getTipo().equals("OP") && !actual().getValor().equals("=") && !esRelacional(actual().getValor())) {
            match("OP");
            T();
            EXP_PRIMA();
        }
    }

    private boolean esRelacional(String valor) {
        return valor.equals(">") || valor.equals("<") || valor.equals(">=")
            || valor.equals("<=") || valor.equals("==") || valor.equals("!=");
    }

    // CONDICION → EXP ( OP_REL EXP )?
    private void CONDICION() {
        EXP();
        if (actual().getTipo().equals("OP") && esRelacional(actual().getValor())) {
            match("OP");
            EXP();
        }
    }

    // IF_EST → if ( CONDICION ) [ S ] ( else [ S ] )?
    private void IF_EST() {
        match("IF");
        match("(");
        CONDICION();
        match(")");
        match("[");
        S();
        match("]");
        if (actual().getTipo().equals("ELSE")) {
            match("ELSE");
            match("[");
            S();
            match("]");
        }
        //System.out.println("Sentencia if válida");
    }

    // WHILE_EST → while ( CONDICION ) [ S ]
    private void WHILE_EST() {
        match("WHILE");
        match("(");
        CONDICION();
        match(")");
        match("[");
        S();
        match("]");
        //System.out.println("Sentencia while válida");
    }

    // FOR_EST → for ( CONDICION ) [ S ]
    private void FOR_EST() {
        match("FOR");
        match("(");
        CONDICION();
        match(")");
        match("[");
        S();
        match("]");
        //System.out.println("Sentencia for válida");
    }

    // FUNC_DEF → func ID ( PARAMS ) [ S ]
    private void FUNC_DEF() {
        match("FUNC");
        String nombreFuncion = actual().getValor();
        int lineaFuncion = actual().getLinea();
        match("ID");
        match("(");
        tabla.entrarAmbito(nombreFuncion);
        int inicioParams = i;
        PARAMS();
        String textoParams = construirTexto(inicioParams, i);
        match(")");
        match("[");
        S();
        match("]");
        tabla.salirAmbito();
        tabla.agregar(nombreFuncion, "-", "Funcion", lineaFuncion, "(" + textoParams + ")");
        //System.out.println("Definición de función válida");
    }

    // PARAMS → PARAM PARAMS' | ε
    private void PARAMS() {
        if (esTipo(actual().getTipo())) {
            PARAM();
            PARAMS_PRIMA();
        }
    }

    // PARAMS' → , PARAM PARAMS' | ε
    private void PARAMS_PRIMA() {
        if (actual().getTipo().equals(",")) {
            match(",");
            PARAM();
            PARAMS_PRIMA();
        }
    }

    // PARAM → TIPO ID
    private void PARAM() {
        String tipoParam = actual().getValor();
        TIPO();
        String nombreParam = actual().getValor();
        int lineaParam = actual().getLinea();
        match("ID");
        tabla.agregar(nombreParam, tipoParam, "Parametro", lineaParam, "");
    }

    // PRINT_EST → print ( EXP ) ;
    private void PRINT_EST() {
        match("PRINT");
        match("(");
        EXP();
        match(")");
        match(";");
        //System.out.println("Sentencia print válida");
    }

    private void match(String tipoEsperado) {
        if (actual().getTipo().equals(tipoEsperado)) {
            //System.out.println("  match(" + tipoEsperado + ") ← " + actual().getValor());
            ultimaLinea = actual().getLinea();
            i++;
        } else {
            error(tipoEsperado);
        }
    }

    private void error(String esperado) {
        System.out.println("\n✗ Error sintáctico en la linea :" + actual().getLinea());
        System.out.println("  Se esperaba : " + esperado);
        System.out.println("  Se encontró : " + actual().getTipo() + " (\"" + actual().getValor() + "\")");
        System.exit(1);
    }
}

class Conversor {

    public ArrayList<Token> convertir(ArrayList<String> lexemas, ArrayList<Integer> lineas) {

        Tokens clasificador = new Tokens();
        ArrayList<Token> tokens = new ArrayList<>();

        ArrayList<String> ids  = clasificador.identificador(lexemas);
        ArrayList<String> prs  = clasificador.palabraReservadas(lexemas);
        ArrayList<String> nums = clasificador.numero(lexemas);
        ArrayList<String> opes = clasificador.operador(lexemas);
        ArrayList<String> sibs = clasificador.simbolo(lexemas);
        ArrayList<String> strs = clasificador.cadenaTexto(lexemas);

        for (int idx = 0; idx < lexemas.size(); idx++) {
            String lexema = lexemas.get(idx);
            if (lexema.isEmpty()) continue;
            int linea = lineas.get(idx);

            if (prs.contains(lexema)) {
                tokens.add(new Token(lexema.toUpperCase(), lexema, linea));
            } else if (ids.contains(lexema)) {
                tokens.add(new Token("ID", lexema, linea));
            } else if (nums.contains(lexema)) {
                tokens.add(new Token("NUM", lexema, linea));
            } else if (strs.contains(lexema)) {
                tokens.add(new Token("STR", lexema, linea));
            } else if (opes.contains(lexema)) {
                tokens.add(new Token("OP", lexema, linea));
            } else if (sibs.contains(lexema)) {
                tokens.add(new Token(lexema, lexema, linea));
            } else {
                tokens.add(new Token("NO_VALIDO", lexema, linea));
            }
        }

        return tokens;
    }
}

