package pt.ulisboa.ppc.cargas;

/**
 * Carga Mandelbrot: a imagem de LARGURA x LARGURA píxeis da região clássica
 * [-2, 1] x [-1.5, 1.5] é dividida em blocos quadrados, e cada tarefa calcula um bloco.
 *
 * O id da tarefa indica o bloco (ordem por linhas) e o parâmetro é o lado do bloco em píxeis.
 * O resultado é a soma das iterações de todos os píxeis do bloco: verifica a correção
 * sem enviar píxeis pela rede.
 *
 * O desequilíbrio é natural: os blocos dentro do conjunto chegam ao limite de iterações
 * em todos os píxeis e custam muito mais do que os de fora. Como os blocos estão por
 * linhas, os blocos caros ficam seguidos na fila (desequilíbrio agrupado).
 */
public final class Mandelbrot implements Carga {

    public static final int LARGURA = 4096;
    public static final int MAX_ITERACOES = 1000;

    private static final double X_MIN = -2.0;
    private static final double X_MAX = 1.0;
    private static final double Y_MIN = -1.5;
    private static final double Y_MAX = 1.5;
    private static final double PASSO_X = (X_MAX - X_MIN) / LARGURA;
    private static final double PASSO_Y = (Y_MAX - Y_MIN) / LARGURA;

    @Override
    public long executar(int id, long parametro) {
        int lado = (int) parametro;
        int blocosPorLinha = LARGURA / lado;
        int x0 = (id % blocosPorLinha) * lado;
        int y0 = (id / blocosPorLinha) * lado;

        long soma = 0;
        for (int py = y0; py < y0 + lado; py++) {
            double ci = Y_MIN + py * PASSO_Y;
            for (int px = x0; px < x0 + lado; px++) {
                double cr = X_MIN + px * PASSO_X;
                double zr = 0;
                double zi = 0;
                int it = 0;
                while (it < MAX_ITERACOES && zr * zr + zi * zi <= 4.0) {
                    double novoZr = zr * zr - zi * zi + cr;
                    zi = 2 * zr * zi + ci;
                    zr = novoZr;
                    it++;
                }
                soma += it;
            }
        }
        return soma;
    }
}
