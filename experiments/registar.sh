#!/bin/bash
# Corre um comando e guarda tudo numa pasta própria em data/testes/.
#
# Uso:   experiments/registar.sh <nome> <comando...>
# Exemplo:
#   experiments/registar.sh plana_localhost java -cp target/ppc.jar \
#       pt.ulisboa.ppc.coordenador.Coordenador --ligacoes 6 --tarefas 20000 --iteracoes 5972
#
# O script acrescenta sozinho "--saida <pasta>/resultados.csv" ao comando,
# por isso não se passa --saida.

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Uso: $0 <nome> <comando...>" >&2
    exit 1
fi

NOME=$1
shift
DIR="data/testes/$(date +%Y-%m-%d_%H%M%S)_${NOME}"
mkdir -p "$DIR"

# Registo do ambiente, para se saber mais tarde em que condições o teste correu
{
    echo "data:      $(date -Iseconds)"
    echo "maquina:   $(hostname)"
    echo "commit:    $(git rev-parse --short HEAD 2>/dev/null || echo sem-commit)"
    echo "por commitar: $(git status --porcelain 2>/dev/null | wc -l) ficheiros alterados"
    echo "java:      $(java -version 2>&1 | grep -m1 version)"
    echo "comando:   $* --saida $DIR/resultados.csv"
} > "$DIR/info.txt"

"$@" --saida "$DIR/resultados.csv" 2>&1 | tee "$DIR/saida.log"

echo "Guardado em $DIR"
