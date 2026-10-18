package pt.ulisboa.ppc.coordenador;

import java.util.Arrays;
import java.util.List;

import pt.ulisboa.ppc.comum.Quadro;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * A política do lado do coordenador: como se entregam as tarefas a quem as pede.
 *
 * As tarefas da repetição atual estão num array. Cada pedido reserva um intervalo de
 * no máximo "lote" tarefas com uma operação atómica (getAndAdd), por isso duas ligações
 * nunca recebem a mesma tarefa e não há lock por pedido.
 *   - Plana: lote = 1 (uma tarefa por pedido).
 *   - Hierárquica: lote = B.
 *
 * Quando a repetição atual se esgota, os pedidos esperam pela repetição seguinte.
 * Depois de terminar(), todos os pedidos recebem null (fim da experiência).
 */
final class Distribuidor {

    /** Tarefas entregues num pedido, e a repetição a que pertencem. */
    record Entrega(Repeticao rep, List<Tarefa> tarefas) {
    }

    /** Repetição especial que indica que não há mais trabalho. */
    private static final Repeticao FIM = new Repeticao(new Tarefa[0]);

    private final int lote;
    private final Quadro<Repeticao> atual = new Quadro<>();

    Distribuidor(int lote) {
        this.lote = lote;
    }

    /** Começa uma nova repetição e acorda os pedidos que estavam à espera. */
    void publicar(Repeticao rep) {
        atual.publicar(rep);
    }

    /** Termina a experiência: os pedidos seguintes recebem null. */
    void terminar() {
        atual.publicar(FIM);
    }

    /**
     * Reserva tarefas para um pedido: no máximo min(pedido, lote).
     * Se a repetição atual já não tiver tarefas, espera pela seguinte.
     *
     * @return as tarefas reservadas, ou null quando a experiência terminou
     */
    Entrega obterTarefas(int pedido) throws InterruptedException {
        int k = Math.min(pedido, lote);
        while (true) {
            Repeticao rep = atual.atual();
            if (rep == FIM) {
                return null;
            }
            if (rep != null) {
                int inicio = rep.proxima.getAndAdd(k);   // reserva atómica de um intervalo
                if (inicio < rep.tarefas.length) {
                    int fim = Math.min(inicio + k, rep.tarefas.length);
                    return new Entrega(rep, Arrays.asList(rep.tarefas).subList(inicio, fim));
                }
            }
            atual.esperarDiferente(rep);
        }
    }
}
