package pt.ulisboa.ppc.coordenador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.junit.jupiter.api.Test;

import pt.ulisboa.ppc.cargas.GeradorTarefas;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * Com vários pedidos ao mesmo tempo, cada tarefa tem de ser entregue exatamente uma vez,
 * nenhum pedido pode receber mais do que o lote, e depois do fim todos recebem null.
 */
class DistribuidorTest {

    @Test
    void cadaTarefaEntregueUmaVezERespeitaOLote() throws Exception {
        int n = 10_000;
        int lote = 7;
        Tarefa[] tarefas = GeradorTarefas.gerar(n, 1, "uniforme", 42);
        Distribuidor distribuidor = new Distribuidor(lote);
        AtomicIntegerArray entregues = new AtomicIntegerArray(n);
        List<String> erros = java.util.Collections.synchronizedList(new ArrayList<>());

        // 6 "ligações" a pedir ao mesmo tempo, até receberem o fim
        List<Thread> ligacoes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread t = new Thread(() -> {
                try {
                    Distribuidor.Entrega e;
                    while ((e = distribuidor.obterTarefas(Integer.MAX_VALUE)) != null) {
                        if (e.tarefas().size() > lote) {
                            erros.add("lote com " + e.tarefas().size() + " tarefas");
                        }
                        for (Tarefa tarefa : e.tarefas()) {
                            entregues.incrementAndGet(tarefa.id());
                        }
                    }
                } catch (InterruptedException ex) {
                    erros.add(ex.toString());
                }
            });
            t.start();
            ligacoes.add(t);
        }

        distribuidor.publicar(new Repeticao(tarefas));
        Thread.sleep(500);                 // tempo de sobra para esgotar a repetição
        distribuidor.terminar();
        for (Thread t : ligacoes) {
            t.join(5_000);
            assertTrue(!t.isAlive(), "uma ligação ficou presa depois do fim");
        }

        assertTrue(erros.isEmpty(), erros.toString());
        for (int i = 0; i < n; i++) {
            assertEquals(1, entregues.get(i), "tarefa " + i);
        }
    }

    @Test
    void naPlanaCadaPedidoRecebeUmaTarefa() throws Exception {
        Distribuidor distribuidor = new Distribuidor(1);
        distribuidor.publicar(new Repeticao(GeradorTarefas.gerar(5, 1, "uniforme", 42)));
        assertEquals(1, distribuidor.obterTarefas(Integer.MAX_VALUE).tarefas().size());
        distribuidor.terminar();
        assertNull(distribuidor.obterTarefas(1));
    }
}
