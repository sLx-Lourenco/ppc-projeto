package pt.ulisboa.ppc.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** A tarefa sintética tem de ser determinística: é assim que se verificam as políticas. */
class SinteticaTest {

    private final Sintetica carga = new Sintetica();

    @Test
    void mesmaTarefaDaSempreOMesmoResultado() {
        assertEquals(carga.executar(7, 5_972), carga.executar(7, 5_972));
    }

    @Test
    void tarefasDiferentesDaoResultadosDiferentes() {
        assertNotEquals(carga.executar(7, 5_972), carga.executar(8, 5_972));
        assertNotEquals(carga.executar(7, 5_972), carga.executar(7, 5_973));
    }
}
