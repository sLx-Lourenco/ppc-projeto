package pt.ulisboa.ppc.protocolo;

/** Uma tarefa a executar: o seu identificador e o parâmetro da carga (na sintética, as iterações). */
public record Tarefa(int id, long parametro) {
}