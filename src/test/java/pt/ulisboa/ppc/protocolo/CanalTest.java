package pt.ulisboa.ppc.protocolo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.junit.jupiter.api.Test;

/** O que um lado envia tem de ser lido igual do outro lado. */
class CanalTest {

    @Test
    void mensagensChegamIguais() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0);
             Canal cliente = new Canal(new Socket("localhost", servidor.getLocalPort()));
             Canal outroLado = new Canal(servidor.accept())) {

            List<Tarefa> tarefas = List.of(new Tarefa(1, 5_972), new Tarefa(2, 59_715));
            cliente.enviarHello(3, 2);
            cliente.enviarPedido(100);
            cliente.enviarTarefas(tarefas);
            cliente.enviarResultados(List.of(new Resultado(1, -42L, 10_000L)));
            cliente.enviarDone();

            assertEquals(Protocolo.HELLO, outroLado.lerTipo());
            assertEquals(3, outroLado.lerInt());
            assertEquals(2, outroLado.lerInt());
            assertEquals(Protocolo.REQUEST, outroLado.lerTipo());
            assertEquals(100, outroLado.lerInt());
            assertEquals(Protocolo.TASKS, outroLado.lerTipo());
            assertEquals(tarefas, outroLado.lerTarefas());
            assertEquals(Protocolo.RESULTS, outroLado.lerTipo());
            assertEquals(List.of(new Resultado(1, -42L, 10_000L)), outroLado.lerResultados());
            assertEquals(Protocolo.DONE, outroLado.lerTipo());
        }
    }

    @Test
    void tamanhoDasMensagensEmBytes() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0);
             Canal cliente = new Canal(new Socket("localhost", servidor.getLocalPort()));
             Canal outroLado = new Canal(servidor.accept())) {

            cliente.enviarTarefas(List.of(new Tarefa(1, 1), new Tarefa(2, 2)));
            assertEquals(1 + 4 + 2 * 12, cliente.bytesEnviados());   // tipo + n + 12 bytes por tarefa
            assertEquals(Protocolo.TASKS, outroLado.lerTipo());
        }
    }
}
