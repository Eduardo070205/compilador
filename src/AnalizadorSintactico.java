import java.util.ArrayList;

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

    public Token(String tipo, String valor) {
        this.tipo = tipo;
        this.valor = valor;
    }

    public String getTipo() {

        return tipo;

    }
    public String getValor() {

        return valor;

    }

    @Override
    public String toString() {
        return "[" + tipo + " : " + valor + "]";
    }
}

class Parser {

    private ArrayList<Token> tokens;
    private int i = 0;
    private int linea = 0;

    public Parser(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }

    private Token actual() {
        if (i < tokens.size()) return tokens.get(i);
        return new Token("EOF", "");
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
        linea = linea + 1;
        if (!actual().getTipo().equals("EOF") && !actual().getTipo().equals("]")) {
            S();
        }
    }

    // SENTENCIA → DEF_VAR | IF_EST | WHILE_EST | FUNC_DEF
    private void SENTENCIA() {
        switch (actual().getTipo()) {
            case "DEF"   -> DEF_VAR();
            case "IF"    -> IF_EST();
            case "WHILE" -> WHILE_EST();
            case "FUNC"  -> FUNC_DEF();
            default -> error("def, if, while o func");
        }
    }

    // DEF_VAR → def TIPO ID = E ;
    private void DEF_VAR() {
        match("DEF");
        TIPO();
        match("ID");
        match("OP");
        EXP();
        match(";");
        //System.out.println("Declaración de variable válida");
    }

    // TIPO → int | string | float
    private void TIPO() {
        String tipoActual = actual().getTipo();
        if (tipoActual.equals("INT") || tipoActual.equals("STRING") || tipoActual.equals("FLOAT")) {
            //System.out.println("  match(TIPO) ← " + actual().getValor());
            i++;
        } else {
            error("int, string o float");
        }
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

    // E' → OP T E' | ε
    private void EXP_PRIMA() {
        if (actual().getTipo().equals("OP") && !actual().getValor().equals("=")) {
            match("OP");
            T();
            EXP_PRIMA();
        }
    }

    // IF_EST → if ( EXP ) [ S ]
    private void IF_EST() {
        match("IF");
        match("(");
        EXP();
        match(")");
        match("[");
        S();
        match("]");
        //System.out.println("Sentencia if válida");
    }

    // WHILE_EST → while ( EXP ) [ S ]
    private void WHILE_EST() {
        match("WHILE");
        match("(");
        EXP();
        match(")");
        match("[");
        S();
        match("]");
        //System.out.println("Sentencia while válida");
    }

    // FUNC_DEF → func ID ( ) [ S ]
    private void FUNC_DEF() {
        match("FUNC");
        match("ID");
        match("(");
        match(")");
        match("[");
        S();
        match("]");
        //System.out.println("Definición de función válida");
    }

    private void match(String tipoEsperado) {
        if (actual().getTipo().equals(tipoEsperado)) {
            //System.out.println("  match(" + tipoEsperado + ") ← " + actual().getValor());
            i++;
        } else {
            error(tipoEsperado);
        }
    }

    private void error(String esperado) {
        System.out.println("\n✗ Error sintáctico en la linea :" + linea);
        System.out.println("  Se esperaba : " + esperado);
        System.out.println("  Se encontró : " + actual().getTipo() + " (\"" + actual().getValor() + "\")");
        System.exit(1);
    }
}

class Conversor {

    public ArrayList<Token> convertir(ArrayList<String> lexemas) {

        Tokens clasificador = new Tokens();
        ArrayList<Token> tokens = new ArrayList<>();

        ArrayList<String> ids  = clasificador.identificador(lexemas);
        ArrayList<String> prs  = clasificador.palabraReservadas(lexemas);
        ArrayList<String> nums = clasificador.numero(lexemas);
        ArrayList<String> opes = clasificador.operador(lexemas);
        ArrayList<String> sibs = clasificador.simbolo(lexemas);
        ArrayList<String> strs = clasificador.cadenaTexto(lexemas);

        for (String lexema : lexemas) {
            if (lexema.isEmpty()) continue;

            if (prs.contains(lexema)) {
                tokens.add(new Token(lexema.toUpperCase(), lexema));
            } else if (ids.contains(lexema)) {
                tokens.add(new Token("ID", lexema));
            } else if (nums.contains(lexema)) {
                tokens.add(new Token("NUM", lexema));
            } else if (strs.contains(lexema)) {
                tokens.add(new Token("STR", lexema));
            } else if (opes.contains(lexema)) {
                tokens.add(new Token("OP", lexema));
            } else if (sibs.contains(lexema)) {
                tokens.add(new Token(lexema, lexema));
            } else {
                tokens.add(new Token("NO_VALIDO", lexema));
            }
        }

        return tokens;
    }
}

