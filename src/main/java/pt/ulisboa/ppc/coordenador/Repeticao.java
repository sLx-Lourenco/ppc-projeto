package pt.ulisboa.ppc.coordenador;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import pt.ulisboa.ppc.protocolo.Tarefa;

/** O estado partilhado de uma repetição: as tarefas por distribuir e as métricas recolhidas. */
final class Repeticao {

    final Tarefa[] tarefas;
    final AtomicInteger proxima = new AtomicInteger(0);    // índice da próxima tarefa por entregar
    final CountDownLatch fim;                              // chega a 0 quando todos os resultados chegarem
    final AtomicIntegerArray recebidos;                    // quantas vezes chegou o resultado de cada tarefa
    final AtomicLong checksum = new AtomicLong(0);         // XOR de todos os resultados
    final AtomicLong mensagens = new AtomicLong(0);
    final AtomicLong bytesEnviados = new AtomicLong(0);
    final AtomicLong somaDuracoesNs = new AtomicLong(0);

    Repeticao(Tarefa[] tarefas) {
        this.tarefas = tarefas;
        this.fim = new CountDownLatch(tarefas.length);
        this.recebidos = new AtomicIntegerArray(tarefas.length);
    }

    /** Confirma que cada tarefa foi executada exatamente uma vez. */
    boolean completa() {
        for (int i = 0; i < recebidos.length(); i++) {
            if (recebidos.get(i) != 1) {
                return false;
            }
        }
        return true;
    }
}
