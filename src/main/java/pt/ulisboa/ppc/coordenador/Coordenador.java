package pt.ulisboa.ppc.coordenador;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.ppc.cargas.Carga;
import pt.ulisboa.ppc.cargas.Cargas;
import pt.ulisboa.ppc.cargas.GeradorTarefas;
import pt.ulisboa.ppc.comum.Argumentos;
import pt.ulisboa.ppc.comum.Politica;
import pt.ulisboa.ppc.protocolo.Canal;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * Coordenador: corre no portátil. Lê a configuração, espera pelas ligações dos nós,
 * corre as repetições e mede o tempo de cada uma.
 *
 * As outras classes do pacote fazem o resto:
 *   - Distribuidor: a política do lado do coordenador (quantas tarefas por pedido);
 *   - Ligacao: atende cada ligação (HELLO, REQUEST, RESULTS);
 *   - Repeticao: estado e métricas de uma repetição;
 *   - RegistoCsv: escreve o CSV.
 */
public final class Coordenador {

    private Coordenador() {
    }

    public static void main(String[] args) throws Exception {
        Argumentos a = new Argumentos(args);
        Politica politica = Politica.deNome(a.texto("politica"));
        int porta = a.inteiro("porta", 9000);
        int ligacoes = a.inteiro("ligacoes", 6);
        int lote = a.inteiro("lote", 1);
        String nomeCarga = a.texto("carga", "sintetica");
        int aquecimento = a.inteiro("aquecimento", 5);
        int repeticoes = a.inteiro("repeticoes", 30);
        RegistoCsv csv = new RegistoCsv(Path.of(a.texto("saida", "resultados.csv")));
        boolean verificar = a.texto("verificar", "nao").equals("sim");

        if (politica == Politica.PLANA && lote != 1) {
            throw new IllegalArgumentException("Na política plana o lote tem de ser 1");
        }

        // Tarefas da configuração. "parametro" vai para o CSV: na sintética são as
        // iterações médias por tarefa, no Mandelbrot é o lado do bloco.
        Tarefa[] tarefas;
        long parametro;
        String desequilibrio;
        if (nomeCarga.equals("mandelbrot")) {
            int lado = a.inteiro("lado", 32);
            tarefas = GeradorTarefas.gerarMandelbrot(lado);
            parametro = lado;
            desequilibrio = "natural";
        } else {
            int n = a.inteiro("tarefas", 1000);
            long iteracoes = a.longo("iteracoes");
            desequilibrio = a.texto("desequilibrio", "uniforme");
            tarefas = GeradorTarefas.gerar(n, iteracoes, desequilibrio, 42);
            parametro = iteracoes;
        }
        Configuracao config = new Configuracao(politica, nomeCarga, lote, desequilibrio,
                tarefas.length, parametro, ligacoes);
        Long esperado = verificar ? checksumSequencial(Cargas.criar(nomeCarga), tarefas) : null;

        Distribuidor distribuidor = new Distribuidor(lote);
        List<Thread> threads = aceitarLigacoes(porta, ligacoes, distribuidor);

        for (int r = 0; r < aquecimento + repeticoes; r++) {
            Repeticao rep = new Repeticao(tarefas);
            long t0 = System.nanoTime();
            distribuidor.publicar(rep);
            rep.fim.await();                     // espera por todos os resultados
            long tempoNs = System.nanoTime() - t0;

            if (!rep.completa()) {
                throw new IllegalStateException("Repetição " + r + ": há tarefas perdidas ou repetidas");
            }
            if (esperado != null && rep.checksum.get() != esperado) {
                throw new IllegalStateException("Repetição " + r + ": resultado diferente do sequencial");
            }

            boolean medida = r >= aquecimento;
            System.out.printf("%s %s %3d: %10.3f ms, %d mensagens%n", politica.nome(),
                    medida ? "rep " : "aquec", r, tempoNs / 1e6, rep.mensagens.get());
            if (medida) {
                csv.escrever(config, r - aquecimento, tempoNs, rep);
            }
        }

        distribuidor.terminar();                 // as ligações respondem DONE ao próximo pedido
        for (Thread t : threads) {
            t.join();
        }
    }

    /** Aceita as ligações dos nós e lança uma thread para atender cada uma. */
    private static List<Thread> aceitarLigacoes(int porta, int ligacoes, Distribuidor distribuidor)
            throws IOException {
        List<Thread> threads = new ArrayList<>();
        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("À espera de " + ligacoes + " ligações na porta " + porta);
            for (int i = 0; i < ligacoes; i++) {
                Canal canal = new Canal(servidor.accept());
                Thread t = new Thread(new Ligacao(canal, distribuidor), "ligacao-" + i);
                t.start();
                threads.add(t);
            }
        }
        System.out.println("Todas as ligações estabelecidas");
        return threads;
    }

    /** Calcula o resultado esperado executando todas as tarefas aqui, uma a uma. */
    private static long checksumSequencial(Carga carga, Tarefa[] tarefas) {
        long x = 0;
        for (Tarefa t : tarefas) {
            x ^= carga.executar(t.id(), t.parametro());
        }
        return x;
    }
}
