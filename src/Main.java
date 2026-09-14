import java.io.*;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {

        ArrayList<String> tokens = new ArrayList<String>();
        ArrayList<String> tokensNoValidos = new ArrayList<String>();
        Tokens token = new Tokens();
        StringBuilder cadena = new StringBuilder();
        boolean dentroDeComillas = false;
        String ruta1 = "archivos/codigo_1.txt";
        File archivo;
        FileReader fr = null;
        String rutaSalida = "archivos/salida.txt";
        BufferedReader br = null;
        int linea = 0;
        ArrayList<String> pr_tipo = new ArrayList<>();

        try {

            archivo = new File(ruta1);
            fr = new FileReader(archivo);
            br = new BufferedReader(fr);
            String fila = null;

            while ((fila = br.readLine()) != null) {

                linea = linea + 1;
                dentroDeComillas = tokenizarLinea(fila, tokens, cadena, dentroDeComillas);
            }

            ArrayList<String> ids  = token.identificador(tokens);
            ArrayList<String> pr   = token.palabraReservadas(tokens);
            ArrayList<String> num  = token.numero(tokens);
            ArrayList<String> ope  = token.operador(tokens);
            ArrayList<String> simb = token.simbolo(tokens);
            ArrayList<String> str  = token.cadenaTexto(tokens);

            ArrayList<String> todosLosValidos = new ArrayList<>();
            todosLosValidos.addAll(ids);
            todosLosValidos.addAll(pr);
            todosLosValidos.addAll(num);
            todosLosValidos.addAll(ope);
            todosLosValidos.addAll(simb);
            todosLosValidos.addAll(str);

            ArrayList<String> lexemasOriginales = new ArrayList<>(tokens);

            tokens.removeAll(todosLosValidos);

            for (String t : tokens) {
                if (!t.isEmpty()) tokensNoValidos.add(t);
            }

            /*
            // ── Imprimir resultados léxicos ──────────────────────────
            System.out.println("Identificadores");
            for (String s : ids)  System.out.println(s);
            System.out.println("Palabras reservadas");
            for (String s : pr)   System.out.println(s);
            System.out.println("Números");
            for (String s : num)  System.out.println(s);
            System.out.println("Operadores");
            for (String s : ope)  System.out.println(s);
            System.out.println("Simbolos");
            for (String s : simb) System.out.println(s);
            System.out.println("Cadenas de texto");
            for (String s : str)  System.out.println(s);
            System.out.println("No validos");
            for (String s : tokensNoValidos) System.out.println(s);
            */


            for (String string : pr) {

                if (string.equals("def")) {

                    pr_tipo.add("Definicion de variable");

                }
                if (string.equals("if")) {

                    pr_tipo.add("Sentencia if");

                }
                if (string.equals("else")) {

                    pr_tipo.add("Sentencia else");

                }
                if (string.equals("while")) {

                    pr_tipo.add("Bucle while");

                }
                if (string.equals("for")) {

                    pr_tipo.add("Bucle for");

                }
                if (string.equals("int")) {

                    pr_tipo.add("Tipo dato entero");

                }
                if (string.equals("string")) {

                    pr_tipo.add("Tipo dato cadena");

                }
                if (string.equals("float")) {

                    pr_tipo.add("Tipo dato flotante");

                }
                if (string.equals("func")) {

                    pr_tipo.add("Funcion");

                }

            }

            System.out.println("\npr_tipo:");
            for (int i = 0; i < pr_tipo.size(); i++) {
                System.out.println("[" + i + "] " + pr_tipo.get(i));
            }

            int a = 0;

            // ── Guardar salida léxica ────────────────────────────────
            try (PrintWriter writer = new PrintWriter(new FileWriter(rutaSalida))) {
                writer.println("------------------------------------------------------------------------------");
                writer.printf("%-25s | %-20s | %-25s%n", "TOKEN", "CATEGORÍA", "TIPO DATO");
                writer.println("------------------------------------------------------------------------------");
                for (String id : ids)  writer.printf("%-25s | %-20s | %-25s%n", id, "Identificador", "string");
                for (String p  : pr) {

                    writer.printf("%-25s | %-20s | %-25s%n", p,  "Palabra Reservada", pr_tipo.get(a));

                    a++;

                }

                for (String n  : num)  writer.printf("%-25s | %-20s | %-25s%n", n,  "Número", "");
                for (String o  : ope)  writer.printf("%-25s | %-20s | %-25s%n", o,  "Operador", "");
                for (String s  : simb) writer.printf("%-25s | %-20s | %-25s%n", s,  "Símbolo", "");
                for (String c  : str)  writer.printf("%-25s | %-20s | %-25s%n", c,  "Cadena", "");
                for (String nv : tokensNoValidos) writer.printf("%-25s | %-20s%n", nv, "No válido");
                writer.println("-------------------------------------------------------");
                System.out.println("Archivo guardado exitosamente en: " + rutaSalida);
            } catch (IOException e) {
                System.out.println("Error al crear el archivo de salida: " + e.getMessage());
            }

            if(tokensNoValidos.size() > 0){

                while ((fila = br.readLine()) != null) {

                    linea = linea + 1;
                    dentroDeComillas = tokenizarLinea(fila, tokens, cadena, dentroDeComillas);
                }

                Errores.hayError("01", linea);

            }else{

                ArrayList<String> lexemasOrdenados = new ArrayList<>();
                lexemasOrdenados.addAll(pr);
                lexemasOrdenados.addAll(ids);
                lexemasOrdenados.addAll(num);
                lexemasOrdenados.addAll(ope);
                lexemasOrdenados.addAll(simb);

                Conversor conversor = new Conversor();
                ArrayList<Token> tokensParseados = conversor.convertir(lexemasOriginales);

                System.out.println("\n=== Análisis sintáctico ===");
                Parser parser = new Parser(tokensParseados);
                parser.S();
                System.out.println("\nPrograma sintácticamente correcto.");

            }



        } catch (FileNotFoundException e) {
            System.out.println("No se encontro el archivo");
        } catch (IOException e) {
            System.out.println("Error en el archivo");
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                System.out.println("Error al cerrar el archivo");
            }
        }
    }

    // Separa una línea en lexemas por espacios, sin partir el contenido
    // que va entre comillas dobles (para soportar cadenas con espacios).
    private static boolean tokenizarLinea(String fila, ArrayList<String> tokens, StringBuilder cadena, boolean dentroDeComillas) {
        for (int i = 0; i < fila.length(); i++) {
            char c = fila.charAt(i);
            if (c == '"') {
                dentroDeComillas = !dentroDeComillas;
                cadena.append(c);
            } else if (c == ' ' && !dentroDeComillas) {
                tokens.add(cadena.toString());
                cadena.setLength(0);
            } else {
                cadena.append(c);
            }
        }
        tokens.add(cadena.toString());
        cadena.setLength(0);
        return dentroDeComillas;
    }
}

class Tokens {

    public ArrayList<String> identificador(ArrayList<String> lexemas) {
        ArrayList<String> ids = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            if (lexemas.get(i).matches("^[a-zA-Z][a-zA-Z0-9,]*\\?$")) {
                ids.add(lexemas.get(i));
            }
        }
        return ids;
    }

    public ArrayList<String> palabraReservadas(ArrayList<String> lexemas) {
        String[] pReservadas = {"def", "if", "else", "while", "for", "int", "string", "float", "func"};
        ArrayList<String> palabrasUsadas = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            for (int y = 0; y < pReservadas.length; y++) {
                if (lexemas.get(i).equals(pReservadas[y])) {
                    palabrasUsadas.add(lexemas.get(i));
                }
            }
        }
        return palabrasUsadas;
    }

    public ArrayList<String> numero(ArrayList<String> lexemas) {
        ArrayList<String> numeros = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            // Admite enteros (10) y flotantes (3.5)
            if (lexemas.get(i).matches("[0-9]+(\\.[0-9]+)?")) {
                numeros.add(lexemas.get(i));
            }
        }
        return numeros;
    }

    public ArrayList<String> cadenaTexto(ArrayList<String> lexemas) {
        ArrayList<String> cadenas = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            // Literal de cadena delimitado por comillas dobles, puede contener espacios
            if (lexemas.get(i).matches("^\".*\"$")) {
                cadenas.add(lexemas.get(i));
            }
        }
        return cadenas;
    }

    public ArrayList<String> operador(ArrayList<String> lexemas) {
        ArrayList<String> operadores = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            if (lexemas.get(i).matches("[+\\-*/&=|!]")) {
                operadores.add(lexemas.get(i));
            }
        }
        return operadores;
    }

    public ArrayList<String> simbolo(ArrayList<String> lexemas) {
        ArrayList<String> simbolos = new ArrayList<String>();
        for (int i = 0; i < lexemas.size(); i++) {
            // Agregados ( y ) para soportar if, while y func
            if (lexemas.get(i).matches("[\\[\\],;()]")) {
                simbolos.add(lexemas.get(i));
            }
        }
        return simbolos;
    }
}