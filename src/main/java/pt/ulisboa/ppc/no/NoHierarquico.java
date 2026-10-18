package pt.ulisboa.ppc.no;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import pt.ulisboa.ppc.cargas.Carga;
import pt.ulisboa.ppc.comum.Quadro;
import pt.ulisboa.ppc.protocolo.Canal;
import pt.ulisboa.ppc.protocolo.Protocolo;
import pt.ulisboa.ppc.protocolo.Resultado;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * Nó hierárquico: uma única ligação ao coordenador.
 *
 * A thread de comunicação (a thread principal) pede um lote, publica-o numa fila local
 * e espera que as threads de cálculo o terminem. Depois devolve todos os resultados
 * numa só mensagem e pede o lote seguinte.
 *
 * As threads de cálculo nunca tocam na rede: reservam tarefas do lote atual com uma
 * operação atómica, tal como as ligações reservam tarefas no coordenador.
 */
final class NoHierarquico {

    /** O lote que está a ser executado neste nó. */
    private static final class LoteLocal {
        final List<Tarefa> tarefas;
        final Resultado[] resultados;
        final AtomicInteger proxima = new AtomicInteger(0);
        final CountDownLatch fim;

        LoteLocal(List<Tarefa> tarefas) {
            this.tarefas = tarefas;
            this.resultados = new Resultado[tarefas.size()];
            this.fim = new CountDownLatch(tarefas.size());
        }
    }

    /** Lote especial que indica às threads de cálculo que devem terminar. */
    private static final LoteLocal FIM = new LoteLocal(List.of());

    private final Carga carga;
    private final Quadro<LoteLocal> atual = new Quadro<>();   // o lote que as threads de cálculo estão a executar

    private NoHierarquico(Carga carga) {
        this.carga = carga;
    }

    static void correr(String host, int porta, int idNo, int threads, Carga carga)
            throws IOException, InterruptedException {
        NoHierarquico no = new NoHierarquico(carga);

        Thread[] calculo = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            calculo[i] = new Thread(no::cicloCalculo, "calculo-" + i);
            calculo[i].start();
        }

        try (Canal canal = new Canal(AgenteNo.ligar(host, porta))) {
            canal.enviarHello(idNo, threads);
            while (true) {
                canal.enviarPedido(Integer.MAX_VALUE);   // o tamanho do lote é decidido pelo coordenador
                byte tipo = canal.lerTipo();
                if (tipo == Protocolo.DONE) {
                    break;
                }
                if (tipo != Protocolo.TASKS) {
                    throw new IOException("Mensagem inesperada: " + tipo);
                }
                LoteLocal lote = new LoteLocal(canal.lerTarefas());
                no.atual.publicar(lote);
                lote.fim.await();                                        // espera que as threads acabem o lote
                canal.enviarResultados(Arrays.asList(lote.resultados));  // resultados antes do próximo pedido
            }
        } finally {
            no.atual.publicar(FIM);
            for (Thread t : calculo) {
                t.join();
            }
        }
    }

    /** Ciclo de uma thread de cálculo: espera por um lote, tira tarefas dele até se esgotar, repete. */
    private void cicloCalculo() {
        try {
            LoteLocal lote = null;
            while (true) {
                lote = atual.esperarDiferente(lote);   // espera por um lote novo
                if (lote == FIM) {
                    return;
                }
                int n = lote.tarefas.size();
                int i;
                while ((i = lote.proxima.getAndIncrement()) < n) {
                    Tarefa t = lote.tarefas.get(i);
                    long t0 = System.nanoTime();
                    long valor = carga.executar(t.id(), t.parametro());
                    lote.resultados[i] = new Resultado(t.id(), valor, System.nanoTime() - t0);
                    lote.fim.countDown();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
