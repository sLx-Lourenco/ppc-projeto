package pt.ulisboa.ppc.coordenador;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Escreve uma linha por repetição medida num ficheiro CSV (acrescenta, não apaga). */
final class RegistoCsv {

    private static final String CABECALHO = "politica,carga,lote,desequilibrio,tarefas,parametro,"
            + "ligacoes,repeticao,tempo_ns,mensagens,bytes_enviados,soma_duracoes_ns";

    private final Path ficheiro;

    RegistoCsv(Path ficheiro) {
        this.ficheiro = ficheiro;
    }

    void escrever(Configuracao c, int repeticao, long tempoNs, Repeticao rep) throws IOException {
        boolean novo = !Files.exists(ficheiro);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(ficheiro,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            if (novo) {
                w.println(CABECALHO);
            }
            w.printf("%s,%s,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    c.politica().nome(), c.carga(), c.lote(), c.desequilibrio(), c.tarefas(),
                    c.parametro(), c.ligacoes(), repeticao, tempoNs, rep.mensagens.get(),
                    rep.bytesEnviados.get(), rep.somaDuracoesNs.get());
        }
    }
}
