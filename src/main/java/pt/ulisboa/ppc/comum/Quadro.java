package pt.ulisboa.ppc.comum;

/**
 * Guarda um valor "atual" e deixa outras threads esperarem até ele mudar.
 *
 * Usa-se nos dois lados:
 *   - no coordenador, o valor é a repetição atual (as ligações esperam pela seguinte);
 *   - no nó hierárquico, o valor é o lote atual (as threads de cálculo esperam pelo seguinte).
 *
 * A leitura do valor atual não usa lock (o campo é volatile), porque é feita em cada pedido.
 * Publicar e esperar usam o monitor deste objeto (synchronized, wait e notifyAll).
 */
public final class Quadro<T> {

    private volatile T atual = null;

    /** Publica um valor novo e acorda todas as threads que estão à espera. */
    public synchronized void publicar(T novo) {
        atual = novo;
        notifyAll();
    }

    /** O valor atual (null se ainda não foi publicado nenhum). */
    public T atual() {
        return atual;
    }

    /** Espera até haver um valor publicado e diferente de "anterior", e devolve-o. */
    public synchronized T esperarDiferente(T anterior) throws InterruptedException {
        while (atual == anterior || atual == null) {
            wait();
        }
        return atual;
    }
}
