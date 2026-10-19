import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/*
    Generación del Grafo de Flujo de Control (CFG).

    Se construye a partir de los tokens de un programa que ya pasó el análisis
    léxico y sintáctico sin errores. Cada nodo es un bloque básico: una
    secuencia de instrucciones que se ejecutan siempre juntas, de principio a
    fin. Un bloque termina cuando hay un salto (la condición de un if, while
    o for) y comienza uno nuevo en cada destino de salto.

    Se genera un CFG para el programa principal y uno por cada función, y se
    exportan en formato DOT para visualizarlos con Graphviz.
*/

class BloqueBasico {

    private final String nombre;
    private final ArrayList<String> instrucciones = new ArrayList<>();
    private final ArrayList<Integer> lineas = new ArrayList<>();
    private boolean condicional = false;

    public BloqueBasico(String nombre) {
        this.nombre = nombre;
    }

    public void agregar(String instruccion, int linea) {
        instrucciones.add(instruccion);
        lineas.add(linea);
    }

    public String getNombre() { return nombre; }
    public ArrayList<String> getInstrucciones() { return instrucciones; }
    public ArrayList<Integer> getLineas() { return lineas; }
    public boolean esCondicional() { return condicional; }
    public void marcarCondicional() { condicional = true; }
}

// Arista del grafo; la etiqueta es "V" (verdadero), "F" (falso) o vacía
record Arista(BloqueBasico origen, BloqueBasico destino, String etiqueta) {}

// Arista que ya tiene origen pero todavía espera su bloque destino
record Pendiente(BloqueBasico origen, String etiqueta) {}

class GrafoFlujo {

    private final String nombre;
    private final ArrayList<BloqueBasico> bloques = new ArrayList<>();
    private final ArrayList<Arista> aristas = new ArrayList<>();
    private BloqueBasico actual = null;
    private ArrayList<Pendiente> pendientes = new ArrayList<>();
    private int contador = 0;

    public GrafoFlujo(String nombre) {
        this.nombre = nombre;
        BloqueBasico entrada = new BloqueBasico("ENTRY");
        bloques.add(entrada);
        pendientes.add(new Pendiente(entrada, ""));
    }

    public String getNombre() { return nombre; }
    public ArrayList<BloqueBasico> getBloques() { return bloques; }
    public ArrayList<Arista> getAristas() { return aristas; }

    // Crea un bloque y le conecta todas las aristas pendientes
    private BloqueBasico nuevoBloque(String nombreBloque) {
        BloqueBasico b = new BloqueBasico(nombreBloque);
        bloques.add(b);
        for (Pendiente p : pendientes) {
            aristas.add(new Arista(p.origen(), b, p.etiqueta()));
        }
        pendientes = new ArrayList<>();
        return b;
    }

    // Instrucción secuencial (def, print): se agrega al bloque abierto
    public void agregarInstruccion(String texto, int linea) {
        if (actual == null) actual = nuevoBloque("B" + (++contador));
        actual.agregar(texto, linea);
    }

    // Instrucción de salto (condición): termina el bloque actual
    public BloqueBasico agregarCondicion(String texto, int linea) {
        agregarInstruccion(texto, linea);
        BloqueBasico b = actual;
        b.marcarCondicional();
        actual = null;
        return b;
    }

    // Inicia una rama: el siguiente bloque sale de 'origen' con la etiqueta dada
    public void ramificar(BloqueBasico origen, String etiqueta) {
        actual = null;
        pendientes = new ArrayList<>(List.of(new Pendiente(origen, etiqueta)));
    }

    // Cierra el flujo actual y devuelve sus salidas pendientes
    public ArrayList<Pendiente> cortar() {
        if (actual != null) {
            pendientes.add(new Pendiente(actual, ""));
            actual = null;
        }
        ArrayList<Pendiente> salidas = pendientes;
        pendientes = new ArrayList<>();
        return salidas;
    }

    // El flujo continúa desde las salidas indicadas (punto de unión)
    public void continuarDesde(List<Pendiente> salidas) {
        actual = null;
        pendientes = new ArrayList<>(salidas);
    }

    // Conecta el flujo actual con un bloque ya existente (arista de regreso de un ciclo)
    public void conectarA(BloqueBasico destino) {
        for (Pendiente p : cortar()) {
            aristas.add(new Arista(p.origen(), destino, p.etiqueta()));
        }
    }

    // Conecta las salidas finales del flujo con el nodo EXIT
    public void cerrarCon(ArrayList<Pendiente> salidas) {
        pendientes = salidas;
        nuevoBloque("EXIT");
    }
}

class GeneradorCFG {

    private final ArrayList<Token> tokens;
    private int i = 0;
    private final ArrayList<GrafoFlujo> grafos = new ArrayList<>();

    public GeneradorCFG(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }

    public ArrayList<GrafoFlujo> getGrafos() {
        return grafos;
    }

    public void generar() {
        GrafoFlujo principal = new GrafoFlujo("Programa principal");
        grafos.add(principal);
        S(principal);
        principal.cerrarCon(principal.cortar());
    }

    private String tipo() {
        return i < tokens.size() ? tokens.get(i).getTipo() : "EOF";
    }

    // Texto de los tokens desde la posición actual hasta 'fin' (inclusive)
    private String textoHasta(String fin) {
        StringBuilder sb = new StringBuilder();
        int profundidad = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i++);
            if (sb.length() > 0) sb.append(" ");
            sb.append(t.getValor());
            if (t.getTipo().equals("(")) profundidad++;
            if (t.getTipo().equals(")")) profundidad--;
            if (t.getTipo().equals(fin) && profundidad <= 0) break;
        }
        return sb.toString();
    }

    // S → SENTENCIA S | ε
    private void S(GrafoFlujo g) {
        while (!tipo().equals("EOF") && !tipo().equals("]")) {
            SENTENCIA(g);
        }
    }

    private void SENTENCIA(GrafoFlujo g) {
        int linea = tokens.get(i).getLinea();
        switch (tipo()) {
            case "DEF", "PRINT" -> g.agregarInstruccion(textoHasta(";"), linea);
            case "IF" -> IF_EST(g, linea);
            case "WHILE", "FOR" -> CICLO(g, linea);
            case "FUNC" -> FUNC_DEF();
            default -> i++;
        }
    }

    //        [cond]
    //      V /    \ F
    //   [then]    [else]
    //       \      /
    //       [unión]
    private void IF_EST(GrafoFlujo g, int linea) {
        BloqueBasico condicion = g.agregarCondicion(textoHasta(")"), linea);
        ArrayList<Pendiente> union = new ArrayList<>();

        i++; // [
        g.ramificar(condicion, "V");
        S(g);
        i++; // ]
        union.addAll(g.cortar());

        if (tipo().equals("ELSE")) {
            i += 2; // else [
            g.ramificar(condicion, "F");
            S(g);
            i++; // ]
            union.addAll(g.cortar());
        } else {
            union.add(new Pendiente(condicion, "F"));
        }
        g.continuarDesde(union);
    }

    //   ──► [cond] ◄──┐
    //     V │    │ F  │
    //    [cuerpo]─────┘
    //        (F) ──► siguiente
    private void CICLO(GrafoFlujo g, int linea) {
        // La condición es destino del salto de regreso, así que inicia un bloque nuevo
        g.continuarDesde(g.cortar());
        BloqueBasico condicion = g.agregarCondicion(textoHasta(")"), linea);

        i++; // [
        g.ramificar(condicion, "V");
        S(g);
        i++; // ]
        g.conectarA(condicion);

        g.continuarDesde(List.of(new Pendiente(condicion, "F")));
    }

    // Una función tiene su propio CFG; su definición no altera el flujo de quien la contiene
    private void FUNC_DEF() {
        int linea = tokens.get(i).getLinea();
        String encabezado = textoHasta(")");
        GrafoFlujo funcion = new GrafoFlujo(encabezado + "  (línea " + linea + ")");
        grafos.add(funcion);
        i++; // [
        S(funcion);
        i++; // ]
        funcion.cerrarCon(funcion.cortar());
    }

    // ── Salida ───────────────────────────────────────────────────

    public void imprimir() {
        System.out.println("\n=== Grafo de flujo de control (CFG) ===");
        for (GrafoFlujo g : grafos) {
            System.out.println("\n[" + g.getNombre() + "]");
            for (BloqueBasico b : g.getBloques()) {
                System.out.println("  " + b.getNombre());
                for (int k = 0; k < b.getInstrucciones().size(); k++) {
                    System.out.printf("      %3d | %s%n", b.getLineas().get(k), b.getInstrucciones().get(k));
                }
                StringBuilder sucesores = new StringBuilder();
                for (Arista a : g.getAristas()) {
                    if (a.origen() != b) continue;
                    if (sucesores.length() > 0) sucesores.append(", ");
                    sucesores.append(a.destino().getNombre());
                    if (!a.etiqueta().isEmpty()) sucesores.append(" (").append(a.etiqueta()).append(")");
                }
                if (sucesores.length() > 0) System.out.println("        → " + sucesores);
            }
        }
    }

    public void exportarDot(String ruta) {
        try (PrintWriter w = new PrintWriter(new FileWriter(ruta))) {
            w.println("digraph CFG {");
            w.println("    compound=true;");
            w.println("    node [fontname=\"Consolas\", fontsize=10];");
            w.println("    edge [fontname=\"Consolas\", fontsize=10];");

            for (int n = 0; n < grafos.size(); n++) {
                GrafoFlujo g = grafos.get(n);
                String prefijo = "g" + n + "_";
                w.println();
                w.println("    subgraph cluster_" + n + " {");
                w.println("        label=\"" + escapar(g.getNombre()) + "\";");
                w.println("        style=rounded; color=gray50;");

                for (BloqueBasico b : g.getBloques()) {
                    w.println("        " + prefijo + b.getNombre() + " " + atributos(b) + ";");
                }
                for (Arista a : g.getAristas()) {
                    String estilo = switch (a.etiqueta()) {
                        case "V" -> " [label=\"V\", color=\"darkgreen\", fontcolor=\"darkgreen\"]";
                        case "F" -> " [label=\"F\", color=\"red3\", fontcolor=\"red3\"]";
                        default  -> "";
                    };
                    w.println("        " + prefijo + a.origen().getNombre() + " -> "
                            + prefijo + a.destino().getNombre() + estilo + ";");
                }
                w.println("    }");
            }
            w.println("}");
            System.out.println("\nCFG generado correctamente en: " + ruta);
        } catch (IOException e) {
            System.out.println("Error al crear el archivo del CFG: " + e.getMessage());
        }
    }

    private String atributos(BloqueBasico b) {
        if (b.getNombre().equals("ENTRY") || b.getNombre().equals("EXIT")) {
            return "[label=\"" + b.getNombre() + "\", shape=ellipse, style=filled, fillcolor=\"gray85\"]";
        }
        StringBuilder etiqueta = new StringBuilder(b.getNombre()).append("\\n");
        for (int k = 0; k < b.getInstrucciones().size(); k++) {
            etiqueta.append(b.getLineas().get(k)).append(": ")
                    .append(escapar(b.getInstrucciones().get(k))).append("\\l");
        }
        String color = b.esCondicional() ? "lightyellow" : "aliceblue";
        return "[label=\"" + etiqueta + "\", shape=box, style=filled, fillcolor=\"" + color + "\"]";
    }

    private String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Si Graphviz está instalado genera también la imagen PNG
    public void generarImagen(String rutaDot, String rutaPng) {
        try {
            Process p = new ProcessBuilder("dot", "-Tpng", rutaDot, "-o", rutaPng)
                    .redirectErrorStream(true).start();
            if (p.waitFor() == 0) {
                System.out.println("Imagen del CFG generada en: " + rutaPng);
                return;
            }
        } catch (IOException | InterruptedException e) {
            // Graphviz no está instalado o no está en el PATH
        }
        System.out.println("Para visualizarlo instala Graphviz (https://graphviz.org) y ejecuta:");
        System.out.println("dot -Tpng " + rutaDot + " -o " + rutaPng);
    }
}
