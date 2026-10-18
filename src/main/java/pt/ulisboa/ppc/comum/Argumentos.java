package pt.ulisboa.ppc.comum;

import java.util.HashMap;
import java.util.Map;

/** Lê argumentos da linha de comandos no formato "--nome valor". */
public final class Argumentos {

    private final Map<String, String> valores = new HashMap<>();

    public Argumentos(String[] args) {
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Argumento inválido: " + args[i]);
            }
            valores.put(args[i].substring(2), args[i + 1]);
        }
    }

    public String texto(String nome, String omissao) {
        return valores.getOrDefault(nome, omissao);
    }

    public String texto(String nome) {
        String v = valores.get(nome);
        if (v == null) {
            throw new IllegalArgumentException("Falta o argumento --" + nome);
        }
        return v;
    }

    public int inteiro(String nome, int omissao) {
        return valores.containsKey(nome) ? Integer.parseInt(valores.get(nome)) : omissao;
    }

    public long longo(String nome) {
        return Long.parseLong(texto(nome));
    }
}
