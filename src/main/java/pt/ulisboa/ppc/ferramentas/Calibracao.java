package pt.ulisboa.ppc.ferramentas;

import java.util.Arrays;

import pt.ulisboa.ppc.cargas.Sintetica;

/**
 * Mede quanto tempo demora uma iteração da tarefa sintética nesta máquina
 * e calcula quantas iterações correspondem a cada tamanho de tarefa.
 * Depois confirma, medindo diretamente tarefas de cada tamanho.
 */
public final class Calibracao {

    private static final double[] ALVOS_US = {10, 100, 1_000, 10_000, 100_000};

    public static void main(String[] args) {
        Sintetica carga = new Sintetica();
        long sumidouro = 0;   // acumula os resultados para o JIT não eliminar o trabalho

        // Fase 1: tempo por iteração, com tarefas grandes
        long n = 100_000_000L;
        for (int r = 0; r < 10; r++) {        // aquecimento do JIT, descartado
            sumidouro ^= carga.executar(r, n);
        }
        long[] amostras = new long[20];
        for (int r = 0; r < amostras.length; r++) {
            long t0 = System.nanoTime();
            sumidouro ^= carga.executar(r, n);
            amostras[r] = System.nanoTime() - t0;
        }
        Arrays.sort(amostras);
        double nsPorIteracao = (double) amostras[amostras.length / 2] / n;
        System.out.printf("ns por iteração (mediana): %.4f%n%n", nsPorIteracao);

        // Fase 2: confirmar diretamente cada tamanho
        System.out.println("alvo (µs)   iterações   medido (µs, média de muitas tarefas)");
        for (double alvoUs : ALVOS_US) {
            long iteracoes = Math.round(alvoUs * 1_000 / nsPorIteracao);
            int repeticoes = (int) Math.max(20, 1_000_000 / alvoUs);   // cerca de 1 s por tamanho
            long t0 = System.nanoTime();
            for (int r = 0; r < repeticoes; r++) {
                sumidouro ^= carga.executar(r, iteracoes);
            }
            double medidoUs = (System.nanoTime() - t0) / 1_000.0 / repeticoes;
            System.out.printf("%9.0f %11d %12.2f%n", alvoUs, iteracoes, medidoUs);
        }

        System.out.println("\n(ignorar) " + sumidouro);
    }
}