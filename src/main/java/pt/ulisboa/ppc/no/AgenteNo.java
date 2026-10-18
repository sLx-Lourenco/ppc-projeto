package pt.ulisboa.ppc.no;

import java.io.IOException;
import java.net.Socket;

import pt.ulisboa.ppc.cargas.Carga;
import pt.ulisboa.ppc.cargas.Cargas;
import pt.ulisboa.ppc.comum.Argumentos;
import pt.ulisboa.ppc.comum.Politica;

/**
 * Agente de nó: corre na torre, preso a 2 cores com taskset.
 * Lê os argumentos e arranca o modo pedido:
 *   plana       - NoPlano: uma ligação por thread de cálculo, uma tarefa de cada vez
 *   hierarquica - NoHierarquico: uma ligação por nó, lotes repartidos por uma fila local
 */
public final class AgenteNo {

    private AgenteNo() {
    }

    public static void main(String[] args) throws Exception {
        Argumentos a = new Argumentos(args);
        String[] endereco = a.texto("coordenador").split(":");
        String host = endereco[0];
        int porta = Integer.parseInt(endereco[1]);
        int idNo = a.inteiro("id", 0);
        int threads = a.inteiro("threads", 2);
        Politica modo = Politica.deNome(a.texto("modo", "plana"));

        Carga carga = Cargas.criar(a.texto("carga", "sintetica"));

        switch (modo) {
            case PLANA -> NoPlano.correr(host, porta, idNo, threads, carga);
            case HIERARQUICA -> NoHierarquico.correr(host, porta, idNo, threads, carga);
        }
    }

    /** Liga ao coordenador, tentando de novo durante 30 s caso ele ainda não esteja à escuta. */
    static Socket ligar(String host, int porta) throws IOException {
        for (int tentativa = 0; ; tentativa++) {
            try {
                return new Socket(host, porta);
            } catch (IOException e) {
                if (tentativa >= 150) {
                    throw e;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ie);
                }
            }
        }
    }
}
