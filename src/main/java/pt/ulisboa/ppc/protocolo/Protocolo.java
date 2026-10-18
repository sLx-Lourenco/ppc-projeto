package pt.ulisboa.ppc.protocolo;

/** Tipos de mensagem. Cada mensagem começa com um destes bytes. */
public final class Protocolo {

    /** Nó → coordenador: apresenta-se (id do nó, número de threads). */
    public static final byte HELLO = 1;

    /** Nó → coordenador: pede até n tarefas. */
    public static final byte REQUEST = 2;

    /** Coordenador → nó: envia n tarefas (n = 1 na plana, n = tamanho do lote na hierárquica). */
    public static final byte TASKS = 3;

    /** Nó → coordenador: devolve n resultados. */
    public static final byte RESULTS = 4;

    /** Coordenador → nó: não há mais trabalho. */
    public static final byte DONE = 5;

    private Protocolo() {
        // só constantes, não se instancia
    }
}