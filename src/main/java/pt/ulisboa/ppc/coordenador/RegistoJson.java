package pt.ulisboa.ppc.coordenador;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Guarda uma execução do coordenador num ficheiro JSON próprio, em data/<categoria>/.
 *
 * O ficheiro tem a configuração, o ambiente e a lista das repetições medidas.
 * É reescrito no fim de cada repetição: se a execução falhar a meio, ficam guardadas
 * as repetições que já acabaram.
 */
final class RegistoJson {

    /** Categorias aceites; cada uma é uma pasta dentro de data/. */
    static final Set<String> CATEGORIAS = Set.of("testes", "piloto", "final");

    private static final DateTimeFormatter NOME_FICHEIRO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final Path ficheiro;
    private final String inicio;                       // tudo o que vem antes da lista de repetições
    private final List<String> repeticoes = new ArrayList<>();

    /**
     * @param pasta     pasta base (normalmente "data")
     * @param categoria "testes", "piloto" ou "final"
     * @param nome      etiqueta livre acrescentada ao nome do ficheiro (pode ser vazia)
     * @param c         configuração da experiência
     * @param commit    versão do código (o correr.sh passa o commit do git)
     * @param comando   os argumentos do coordenador, para se poder repetir a execução
     */
    RegistoJson(Path pasta, String categoria, String nome, Configuracao c, String commit, String comando)
            throws IOException {
        if (!CATEGORIAS.contains(categoria)) {
            throw new IllegalArgumentException("Categoria desconhecida: " + categoria + " (usa " + CATEGORIAS + ")");
        }
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String nomeFicheiro = agora.format(NOME_FICHEIRO) + "_" + c.politica().nome() + "_" + c.carga()
                + "_" + c.parametro() + "_" + c.desequilibrio() + "_lote" + c.lote()
                + (nome.isEmpty() ? "" : "_" + nome) + ".json";
        Path dir = pasta.resolve(categoria);
        Files.createDirectories(dir);
        this.ficheiro = dir.resolve(nomeFicheiro);

        this.inicio = "{\n"
                + "  \"data\": " + texto(agora.toString()) + ",\n"
                + "  \"commit\": " + texto(commit) + ",\n"
                + "  \"comando\": " + texto(comando) + ",\n"
                + "  \"configuracao\": {\n"
                + "    \"politica\": " + texto(c.politica().nome()) + ",\n"
                + "    \"carga\": " + texto(c.carga()) + ",\n"
                + "    \"lote\": " + c.lote() + ",\n"
                + "    \"desequilibrio\": " + texto(c.desequilibrio()) + ",\n"
                + "    \"tarefas\": " + c.tarefas() + ",\n"
                + "    \"parametro\": " + c.parametro() + ",\n"
                + "    \"ligacoes\": " + c.ligacoes() + ",\n"
                + "    \"aquecimento\": " + c.aquecimento() + ",\n"
                + "    \"repeticoes_pedidas\": " + c.repeticoes() + "\n"
                + "  },\n"
                + "  \"ambiente\": {\n"
                + "    \"coordenador\": " + texto(InetAddress.getLocalHost().getHostName()) + ",\n"
                + "    \"java\": " + texto(System.getProperty("java.version")) + ",\n"
                + "    \"sistema\": " + texto(System.getProperty("os.name") + " " + System.getProperty("os.version")) + "\n"
                + "  },\n";
    }

    /** Acrescenta uma repetição medida e regrava o ficheiro. */
    void escrever(int repeticao, long tempoNs, Repeticao rep) throws IOException {
        repeticoes.add("    {\"repeticao\": " + repeticao
                + ", \"tempo_ns\": " + tempoNs
                + ", \"mensagens\": " + rep.mensagens.get()
                + ", \"bytes_enviados\": " + rep.bytesEnviados.get()
                + ", \"soma_duracoes_ns\": " + rep.somaDuracoesNs.get() + "}");
        gravar();
    }

    Path ficheiro() {
        return ficheiro;
    }

    /**
     * Escreve primeiro num ficheiro temporário e depois muda-lhe o nome.
     * A mudança de nome é atómica: o ficheiro final nunca fica escrito a meio.
     */
    private void gravar() throws IOException {
        String json = inicio
                + "  \"repeticoes\": [\n" + String.join(",\n", repeticoes) + "\n  ]\n"
                + "}\n";
        Path tmp = ficheiro.resolveSibling(ficheiro.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        Files.move(tmp, ficheiro, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Um texto entre aspas, com as aspas e as barras escapadas como o JSON exige. */
    static String texto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}