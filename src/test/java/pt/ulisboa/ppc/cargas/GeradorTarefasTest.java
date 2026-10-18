package pt.ulisboa.ppc.cargas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import pt.ulisboa.ppc.protocolo.Tarefa;

/** O gerador tem de dar o mesmo trabalho total às três variantes da carga sintética. */
class GeradorTarefasTest {

    private static final int N = 20_000;
    private static final long MEDIA = 5_972;   // 10 µs na torre

    private static long total(Tarefa[] tarefas) {
        return Arrays.stream(tarefas).mapToLong(Tarefa::parametro).sum();
    }

    @Test
    void asTresVariantesTemOMesmoTrabalhoTotal() {
        long uniforme = total(GeradorTarefas.gerar(N, MEDIA, "uniforme", 42));
        for (String d : new String[] {"baralhada", "agrupada"}) {
            long t = total(GeradorTarefas.gerar(N, MEDIA, d, 42));
            double diferenca = Math.abs(t - uniforme) / (double) uniforme;
            assertTrue(diferenca < 0.001, d + ": diferença de " + diferenca);   // só arredondamento
        }
    }

    @Test
    void naAgrupadaAsLongasEstaoNoInicio() {
        Tarefa[] t = GeradorTarefas.gerar(N, MEDIA, "agrupada", 42);
        long longa = t[0].parametro();
        for (int i = 0; i < N / 10; i++) {
            assertEquals(longa, t[i].parametro());
        }
        for (int i = N / 10; i < N; i++) {
            assertEquals(longa / 10, t[i].parametro());
        }
    }

    @Test
    void aBaralhadaTemAsMesmasTarefasQueAAgrupada() {
        long[] agrupada = Arrays.stream(GeradorTarefas.gerar(N, MEDIA, "agrupada", 42))
                .mapToLong(Tarefa::parametro).sorted().toArray();
        long[] baralhada = Arrays.stream(GeradorTarefas.gerar(N, MEDIA, "baralhada", 42))
                .mapToLong(Tarefa::parametro).sorted().toArray();
        assertArrayEquals(agrupada, baralhada);
    }

    @Test
    void aMesmaSementeDaAMesmaOrdem() {
        assertArrayEquals(GeradorTarefas.gerar(N, MEDIA, "baralhada", 42),
                GeradorTarefas.gerar(N, MEDIA, "baralhada", 42));
    }

    @Test
    void mandelbrotTemUmaTarefaPorBloco() {
        assertEquals(512 * 512, GeradorTarefas.gerarMandelbrot(8).length);
        assertEquals(8 * 8, GeradorTarefas.gerarMandelbrot(512).length);
        assertThrows(IllegalArgumentException.class, () -> GeradorTarefas.gerarMandelbrot(100));
    }
}
