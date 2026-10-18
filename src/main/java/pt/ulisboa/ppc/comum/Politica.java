package pt.ulisboa.ppc.comum;

/**
 * As políticas de distribuição de tarefas em estudo.
 * O nome é o que se escreve na linha de comandos (--politica, --modo) e no JSON.
 */
public enum Politica {

    /** Cada thread de cálculo tem a sua ligação e pede uma tarefa de cada vez. */
    PLANA("plana"),

    /** Uma ligação por nó; o coordenador entrega lotes e o nó reparte-os pelas suas threads. */
    HIERARQUICA("hierarquica");

    private final String nome;

    Politica(String nome) {
        this.nome = nome;
    }

    /** O nome usado na linha de comandos e no JSON. */
    public String nome() {
        return nome;
    }

    /** Converte o nome da linha de comandos na política correspondente. */
    public static Politica deNome(String nome) {
        for (Politica p : values()) {
            if (p.nome.equals(nome)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Política desconhecida: " + nome);
    }
}
