package pt.ulisboa.ppc.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Cortar a imagem em blocos maiores ou menores não pode mudar o resultado.
 * Para o teste ser rápido, compara-se só uma zona: um bloco de 128 contra os
 * 16 blocos de 32 que o cobrem.
 */
class MandelbrotTest {

    private final Mandelbrot carga = new Mandelbrot();

    /** Soma dos blocos de lado "pequeno" que cobrem o bloco (bx, by) de lado "grande". */
    private long somaSubblocos(int bx, int by, int grande, int pequeno) {
        int fator = grande / pequeno;
        int blocosPorLinha = Mandelbrot.LARGURA / pequeno;
        long soma = 0;
        for (int y = by * fator; y < (by + 1) * fator; y++) {
            for (int x = bx * fator; x < (bx + 1) * fator; x++) {
                soma += carga.executar(y * blocosPorLinha + x, pequeno);
            }
        }
        return soma;
    }

    private long bloco(int bx, int by, int lado) {
        return carga.executar(by * (Mandelbrot.LARGURA / lado) + bx, lado);
    }

    @Test
    void blocoGrandeIgualASomaDosPequenos() {
        // um bloco na fronteira do conjunto (custos muito diferentes entre píxeis)
        assertEquals(bloco(12, 14, 128), somaSubblocos(12, 14, 128, 32));
        // um bloco fora do conjunto
        assertEquals(bloco(0, 0, 128), somaSubblocos(0, 0, 128, 32));
    }

    @Test
    void blocoDentroDoConjuntoChegaAoLimiteEmTodosOsPixeis() {
        // x de 2176 a 2303 e y de 1792 a 1919: dentro do cardioide principal
        assertEquals(128L * 128 * Mandelbrot.MAX_ITERACOES, bloco(17, 14, 128));
    }
}
