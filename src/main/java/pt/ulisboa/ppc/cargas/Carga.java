package pt.ulisboa.ppc.cargas;

/**
 * Uma carga de trabalho: sabe executar uma tarefa a partir do seu id e de um parâmetro.
 * As três políticas usam esta interface, por isso o código de cálculo é sempre o mesmo.
 */
public interface Carga {

    /**
     * Executa uma tarefa e devolve um resultado verificável.
     *
     * @param id        identificador da tarefa (também serve de semente)
     * @param parametro tamanho da tarefa (na sintética, o número de iterações)
     * @return resultado determinístico, usado para verificar a correção
     */
    long executar(int id, long parametro);
}