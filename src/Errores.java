public class Errores {



    public static void hayError(String tipoError, int linea){

        if(tipoError == "01"){

            errLexico(linea);

        } else if (tipoError == "02") {

            errSintactico(linea);

        }else if (tipoError == "00"){

            System.out.println("Sin errores encontrados");

        }

    }
    public static void errLexico(int linea){

        System.out.println("Se encontro un error Lexico en la linea:  " + linea);

    }

    public static void errSintactico(int linea){

        System.out.println("Se encontro un error Sintanctico en la linea: " + linea);

    }

}
