public class Errores {



    public static void hayError(String tipoError, int linea){

        if(tipoError.equals("01")){

            errLexico(linea, "");

        } else if (tipoError.equals("02")) {

            errSintactico(linea);

        }else if (tipoError.equals("00")){

            System.out.println("Sin errores encontrados");

        }

    }
    public static void errLexico(int linea, String token){

        if (token.isEmpty()) {
            System.out.println("Se encontro un error Lexico en la linea:  " + linea);
        } else {
            System.out.println("Se encontro un error Lexico en la linea " + linea + ": token no valido \"" + token + "\"");
        }

    }

    public static void errSintactico(int linea){

        System.out.println("Se encontro un error Sintanctico en la linea: " + linea);

    }

}
