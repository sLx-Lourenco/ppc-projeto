package pt.ulisboa.ppc.ferramentas;

import java.util.Arrays;

import pt.ulisboa.ppc.cargas.Mandelbrot;

/**
 * Executa a imagem inteira num só core, para cada tamanho de bloco, e mostra
 * quanto custa cada tarefa. Serve para descrever o desequilíbrio natural do Mandelbrot
 * e para ter a linha de base sequencial.
 */
public final class CaracterizarMandelbrot {

    private static final int[] LADOS = {8, 32, 128, 512};

    public static void main(String[] args) {
        Mandelbrot carga = new Mandelbrot();
        long sumidouro = 0;

        // aquecimento do JIT: a imagem inteira duas vezes, com blocos de 64
        for (int r = 0; r < 2; r++) {
            int n = (Mandelbrot.LARGURA / 64) * (Mandelbrot.LARGURA / 64);
            for (int id = 0; id < n; id++) {
                sumidouro += carga.executar(id, 64);
            }
        }

        System.out.println("lado  tarefas  total(ms)  média(µs)  mediana(µs)  mínimo(µs)  p99(µs)  máximo(µs)  soma das iterações");
        for (int lado : LADOS) {
            int blocosPorLinha = Mandelbrot.LARGURA / lado;
            int n = blocosPorLinha * blocosPorLinha;
            long[] duracoes = new long[n];
            long somaIteracoes = 0;

            long t0 = System.nanoTime();
            for (int id = 0; id < n; id++) {
                long ti = System.nanoTime();
                somaIteracoes += carga.executar(id, lado);
                duracoes[id] = System.nanoTime() - ti;
            }
            long totalNs = System.nanoTime() - t0;

            Arrays.sort(duracoes);
            double media = totalNs / 1_000.0 / n;
            System.out.printf("%4d %8d %10.1f %10.1f %12.1f %11.1f %8.1f %11.1f %19d%n",
                    lado, n, totalNs / 1e6, media,
                    duracoes[n / 2] / 1_000.0, duracoes[0] / 1_000.0,
                    duracoes[(int) (n * 0.99)] / 1_000.0, duracoes[n - 1] / 1_000.0, somaIteracoes);
            sumidouro += somaIteracoes;
        }
        System.out.println("\n(ignorar) " + sumidouro);
    }
}
