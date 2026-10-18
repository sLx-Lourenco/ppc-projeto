package pt.ulisboa.ppc.protocolo;

/**
 * O resultado de uma tarefa, devolvido pelo nó.
 *
 * @param id        identificador da tarefa
 * @param valor     o valor calculado (para verificar a correção)
 * @param duracaoNs quanto tempo a tarefa demorou a executar no nó (métrica secundária)
 */
public record Resultado(int id, long valor, long duracaoNs) {
}