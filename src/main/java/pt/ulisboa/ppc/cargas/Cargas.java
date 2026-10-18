package pt.ulisboa.ppc.cargas;

/** Cria a carga pelo nome. O coordenador e os nós usam isto para terem a mesma carga. */
public final class Cargas {

    private Cargas() {
    }

    public static Carga criar(String nome) {
        return switch (nome) {
            case "sintetica" -> new Sintetica();
            case "mandelbrot" -> new Mandelbrot();
            default -> throw new IllegalArgumentException("Carga desconhecida: " + nome);
        };
    }
}
