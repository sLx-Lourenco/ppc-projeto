package pt.ulisboa.ppc.coordenador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pt.ulisboa.ppc.comum.Politica;
import pt.ulisboa.ppc.protocolo.Tarefa;

/**
 * O registo cria um ficheiro por execução na pasta da categoria, com todas as repetições,
 * sem deixar ficheiros temporários, e recusa categorias desconhecidas.
 */
class RegistoJsonTest {

    private static final Configuracao CONFIG = new Configuracao(Politica.HIERARQUICA, "mandelbrot",
            20, "natural", 1024, 128, 3, 5, 30);

    @Test
    void umFicheiroPorExecucaoComTodasAsRepeticoes() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson registo = new RegistoJson(pasta, "piloto", "cabo", CONFIG, "a1b2c3d", "--lote 20");

        Repeticao rep = new Repeticao(new Tarefa[0]);
        rep.mensagens.set(156);
        registo.escrever(0, 1_000_000, rep);
        registo.escrever(1, 2_000_000, rep);

        List<Path> ficheiros;
        try (Stream<Path> s = Files.list(pasta.resolve("piloto"))) {
            ficheiros = s.toList();
        }
        assertEquals(1, ficheiros.size(), "um só ficheiro, sem .tmp");
        String nome = ficheiros.get(0).getFileName().toString();
        assertTrue(nome.endsWith("_hierarquica_mandelbrot_128_natural_lote20_cabo.json"), nome);

        String json = Files.readString(ficheiros.get(0));
        assertTrue(json.contains("\"commit\": \"a1b2c3d\""), "commit");
        assertTrue(json.contains("\"lote\": 20"), "configuração");
        assertTrue(json.contains("{\"repeticao\": 0, \"tempo_ns\": 1000000, \"mensagens\": 156"), "repetição 0");
        assertTrue(json.contains("{\"repeticao\": 1, \"tempo_ns\": 2000000"), "repetição 1");
        assertTrue(json.endsWith("  ]\n}\n"), "ficheiro fechado");
    }

    @Test
    void textoEscapaAspasEBarras() {
        assertEquals("\"a\\\"b\\\\c\"", RegistoJson.texto("a\"b\\c"));
    }

    @Test
    void recusaCategoriaDesconhecida() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        assertThrows(IllegalArgumentException.class,
                () -> new RegistoJson(pasta, "finl", "", CONFIG, "x", ""));
    }
}