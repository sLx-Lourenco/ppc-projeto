package pt.ulisboa.ppc.coordenador;

import java.io.IOException;
import java.util.List;

import pt.ulisboa.ppc.protocolo.Canal;
import pt.ulisboa.ppc.protocolo.Protocolo;
import pt.ulisboa.ppc.protocolo.Resultado;

/**
 * Atende uma ligação de um nó (ou, na plana, de uma thread de um nó), numa thread própria.
 *
 * Recebe o HELLO e depois responde a cada REQUEST com tarefas do Distribuidor,
 * e regista cada RESULTS na repetição a que as tarefas pertencem.
 * Quando o Distribuidor indica o fim, responde DONE e termina.
 */
final class Ligacao implements Runnable {

    private final Canal canal;
    private final Distribuidor distribuidor;

    Ligacao(Canal canal, Distribuidor distribuidor) {
        this.canal = canal;
        this.distribuidor = distribuidor;
    }

    @Override
    public void run() {
        try (canal) {
            if (canal.lerTipo() != Protocolo.HELLO) {
                throw new IOException("Esperava HELLO");
            }
            canal.lerInt();   // id do nó (informativo)
            canal.lerInt();   // número de threads do nó (informativo)

            Repeticao repDasTarefas = null;
            while (true) {
                byte tipo = canal.lerTipo();
                if (tipo == Protocolo.REQUEST) {
                    int pedido = canal.lerInt();
                    Distribuidor.Entrega e = distribuidor.obterTarefas(pedido);
                    if (e == null) {
                        canal.enviarDone();
                        return;
                    }
                    int antes = canal.bytesEnviados();
                    canal.enviarTarefas(e.tarefas());
                    e.rep().bytesEnviados.addAndGet(canal.bytesEnviados() - antes);
                    e.rep().mensagens.addAndGet(2);   // o REQUEST e o TASKS
                    repDasTarefas = e.rep();
                } else if (tipo == Protocolo.RESULTS) {
                    List<Resultado> resultados = canal.lerResultados();
                    Repeticao rep = repDasTarefas;
                    rep.mensagens.incrementAndGet();
                    for (Resultado r : resultados) {
                        rep.recebidos.incrementAndGet(r.id());
                        rep.checksum.accumulateAndGet(r.valor(), (x, y) -> x ^ y);
                        rep.somaDuracoesNs.addAndGet(r.duracaoNs());
                        rep.fim.countDown();
                    }
                } else {
                    throw new IOException("Mensagem inesperada: " + tipo);
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
