package pt.ulisboa.ppc.cargas;

/**
 * Tarefa sintética: um ciclo de cálculo puro sobre inteiros (xorshift64),
 * sem alocar memória. O tamanho é dado em iterações, não em tempo.
 */
public final class Sintetica implements Carga {

    @Override
    public long executar(int id, long iteracoes) {
        long x = id + 0x9E3779B97F4A7C15L;   // semente diferente para cada tarefa
        for (long i = 0; i < iteracoes; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
        }
        return x;
    }
}