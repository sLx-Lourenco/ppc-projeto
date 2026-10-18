package pt.ulisboa.ppc.protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma ligação TCP com mensagens binárias simples.
 * Cada método enviar* escreve uma mensagem completa e faz flush.
 */
public final class Canal implements Closeable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public Canal(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);   // envia cada mensagem logo, sem esperar para juntar
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    // ---------- enviar ----------

    public void enviarHello(int idNo, int threads) throws IOException {
        out.writeByte(Protocolo.HELLO);
        out.writeInt(idNo);
        out.writeInt(threads);
        out.flush();
    }

    public void enviarPedido(int maxTarefas) throws IOException {
        out.writeByte(Protocolo.REQUEST);
        out.writeInt(maxTarefas);
        out.flush();
    }

    public void enviarTarefas(List<Tarefa> tarefas) throws IOException {
        out.writeByte(Protocolo.TASKS);
        out.writeInt(tarefas.size());
        for (Tarefa t : tarefas) {
            out.writeInt(t.id());
            out.writeLong(t.parametro());
        }
        out.flush();
    }

    public void enviarResultados(List<Resultado> resultados) throws IOException {
        out.writeByte(Protocolo.RESULTS);
        out.writeInt(resultados.size());
        for (Resultado r : resultados) {
            out.writeInt(r.id());
            out.writeLong(r.valor());
            out.writeLong(r.duracaoNs());
        }
        out.flush();
    }

    public void enviarDone() throws IOException {
        out.writeByte(Protocolo.DONE);
        out.flush();
    }

    // ---------- receber ----------

    /** Lê o tipo da próxima mensagem. O resto lê-se com o método correspondente. */
    public byte lerTipo() throws IOException {
        return in.readByte();
    }

    /** Lê um inteiro: o id do nó e as threads (HELLO) ou o máximo de tarefas (REQUEST). */
    public int lerInt() throws IOException {
        return in.readInt();
    }

    /** Lê o corpo de uma mensagem TASKS. */
    public List<Tarefa> lerTarefas() throws IOException {
        int n = in.readInt();
        List<Tarefa> tarefas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tarefas.add(new Tarefa(in.readInt(), in.readLong()));
        }
        return tarefas;
    }

    /** Lê o corpo de uma mensagem RESULTS. */
    public List<Resultado> lerResultados() throws IOException {
        int n = in.readInt();
        List<Resultado> resultados = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            resultados.add(new Resultado(in.readInt(), in.readLong(), in.readLong()));
        }
        return resultados;
    }

    /** Bytes enviados por este canal até agora (métrica secundária). */
    public int bytesEnviados() {
        return out.size();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}