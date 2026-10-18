package pt.ulisboa.ppc.no;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.ppc.cargas.Carga;
import pt.ulisboa.ppc.protocolo.Canal;
import pt.ulisboa.ppc.protocolo.Protocolo;
import pt.ulisboa.ppc.protocolo.Resultado;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * Nó plano: cada thread de cálculo tem a sua própria ligação ao coordenador
 * e pede uma tarefa de cada vez. O coordenador vê cada thread como um worker.
 */
final class NoPlano {

    private NoPlano() {
    }

    static void correr(String host, int porta, int idNo, int threads, Carga carga) throws InterruptedException {
        Thread[] calculo = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            calculo[i] = new Thread(() -> cicloThread(host, porta, idNo, threads, carga), "calculo-" + i);
            calculo[i].start();
        }
        for (Thread t : calculo) {
            t.join();
        }
    }

    /** Ciclo de uma thread: pede, executa, devolve, até receber DONE. */
    private static void cicloThread(String host, int porta, int idNo, int threads, Carga carga) {
        try (Canal canal = new Canal(AgenteNo.ligar(host, porta))) {
            canal.enviarHello(idNo, threads);
            while (true) {
                canal.enviarPedido(1);
                byte tipo = canal.lerTipo();
                if (tipo == Protocolo.DONE) {
                    return;
                }
                if (tipo != Protocolo.TASKS) {
                    throw new IOException("Mensagem inesperada: " + tipo);
                }
                List<Tarefa> tarefas = canal.lerTarefas();
                List<Resultado> resultados = new ArrayList<>(tarefas.size());
                for (Tarefa t : tarefas) {
                    long t0 = System.nanoTime();
                    long valor = carga.executar(t.id(), t.parametro());
                    resultados.add(new Resultado(t.id(), valor, System.nanoTime() - t0));
                }
                canal.enviarResultados(resultados);   // os resultados vão sempre antes do próximo pedido
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
