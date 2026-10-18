package pt.ulisboa.ppc.cargas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * Gera a lista de tarefas de uma configuração.
 * Sintética: as três variantes têm a mesma duração média (iteracoesMedias por tarefa).
 * Mandelbrot: um bloco da imagem por tarefa.
 */
public final class GeradorTarefas {

    private GeradorTarefas() {
    }

    /**
     * Tarefas do Mandelbrot: um bloco por tarefa, por ordem de linhas.
     *
     * @param lado lado do bloco em píxeis (tem de dividir a largura da imagem)
     */
    public static Tarefa[] gerarMandelbrot(int lado) {
        if (Mandelbrot.LARGURA % lado != 0) {
            throw new IllegalArgumentException("O lado " + lado + " não divide " + Mandelbrot.LARGURA);
        }
        int blocosPorLinha = Mandelbrot.LARGURA / lado;
        Tarefa[] tarefas = new Tarefa[blocosPorLinha * blocosPorLinha];
        for (int i = 0; i < tarefas.length; i++) {
            tarefas[i] = new Tarefa(i, lado);
        }
        return tarefas;
    }

    /**
     * Tarefas da carga sintética.
     *
     * @param n               número de tarefas
     * @param iteracoesMedias iterações médias por tarefa (vem da calibração)
     * @param desequilibrio   "uniforme", "baralhada" ou "agrupada"
     * @param semente         semente fixa, para a ordem ser igual em todas as execuções
     */
    public static Tarefa[] gerar(int n, long iteracoesMedias, String desequilibrio, long semente) {
        if (desequilibrio.equals("uniforme")) {
            Tarefa[] tarefas = new Tarefa[n];
            for (int i = 0; i < n; i++) {
                tarefas[i] = new Tarefa(i, iteracoesMedias);
            }
            return tarefas;
        }

        // Enviesada: 90% curtas (c) e 10% longas (10c), com média igual à uniforme:
        // 0.9c + 0.1 * 10c = 1.9c = iteracoesMedias  =>  c = iteracoesMedias / 1.9
        long curta = Math.round(iteracoesMedias / 1.9);
        long longa = 10 * curta;
        int nLongas = n / 10;

        List<Long> parametros = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parametros.add(i < nLongas ? longa : curta);   // as longas ficam todas no início
        }
        if (desequilibrio.equals("baralhada")) {
            Collections.shuffle(parametros, new Random(semente));
        } else if (!desequilibrio.equals("agrupada")) {
            throw new IllegalArgumentException("Desequilíbrio desconhecido: " + desequilibrio);
        }

        Tarefa[] tarefas = new Tarefa[n];
        for (int i = 0; i < n; i++) {
            tarefas[i] = new Tarefa(i, parametros.get(i));
        }
        return tarefas;
    }
}
