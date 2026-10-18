package pt.ulisboa.ppc.coordenador;

import pt.ulisboa.ppc.comum.Politica;

/**
 *  * A configuração de uma experiência, tal como fica registada no ficheiro JSON.
 *
 * @param politica      política de distribuição
 * @param carga         nome da carga ("sintetica" ou "mandelbrot")
 * @param lote          máximo de tarefas por pedido
 * @param desequilibrio "uniforme", "baralhada", "agrupada" (sintética) ou "natural" (Mandelbrot)
 * @param tarefas       número de tarefas por repetição
 * @param parametro     sintética: iterações médias por tarefa; Mandelbrot: lado do bloco
 * @param ligacoes      número de ligações que o coordenador espera
 * @param aquecimento   repetições de aquecimento (não gravadas)
 * @param repeticoes    repetições medidas pedidas

*/
record Configuracao(Politica politica, String carga, int lote, String desequilibrio,
        int tarefas, long parametro, int ligacoes, int aquecimento, int repeticoes) {
}
