#!/bin/bash
# Corre uma experiência: o coordenador no portátil e os 3 nós na torre.
#
# Uso:   experiments/correr.sh <nome> <ip-do-portátil> <politica> <argumentos do coordenador...>
#
#   <politica> é "plana" ou "hierarquica". O script trata do resto:
#     plana:       os nós abrem uma ligação por thread (6 ligações) e o lote é 1
#     hierarquica: uma ligação por nó (3 ligações); o lote passa-se com --lote
#
#   A carga passa-se com --carga (sintetica por omissão, ou mandelbrot); o script
#   passa-a também aos nós.
#
#   A categoria passa-se com --categoria (testes por omissão, piloto ou final).
#
# Exemplos:
#   experiments/correr.sh cabo 10.42.0.1 plana --tarefas 20000 --iteracoes 5972 --verificar sim
#   experiments/correr.sh cabo 10.42.0.1 hierarquica --lote 100 --tarefas 20000 --iteracoes 5972
#   experiments/correr.sh cabo 10.42.0.1 hierarquica --lote 20 --carga mandelbrot --lado 128
#
# O coordenador guarda a execução num ficheiro JSON em data/<categoria>/
# (o nome do ficheiro leva a data, a configuração e o <nome>).

set -euo pipefail

if [ $# -lt 3 ]; then
    echo "Uso: $0 <nome> <ip-do-portátil> <plana|hierarquica> <argumentos do coordenador...>" >&2
    exit 1
fi

NOME=$1
COORD_IP=$2
POLITICA=$3
shift 3

TORRE=fixo
NOS=3
THREADS=2
JVM_NO="-Xms512m -Xmx512m -XX:+UseSerialGC"

# A carga tem de ser a mesma no coordenador e nos nós: procura-se --carga nos argumentos
CARGA=sintetica
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]} - 1; i++)); do
    if [ "${ARGS[$i]}" = "--carga" ]; then
        CARGA="${ARGS[$((i + 1))]}"
    fi
done

case "$POLITICA" in
    plana)       LIGACOES=$((NOS * THREADS)) ;;
    hierarquica) LIGACOES=$NOS ;;
    *) echo "Política desconhecida: $POLITICA (usa plana ou hierarquica)" >&2; exit 1 ;;
esac

# 1. Compilar e copiar o jar para a torre
mvn -q package -DskipTests
scp -q target/ppc.jar "$TORRE":~/ppc.jar

# 2. Lançar os nós na torre, cada um preso a 2 cores (0-1, 2-3, 4-5), no modo da política.
#    Ficam a tentar ligar durante 30 s, até o coordenador abrir a porta.
for i in $(seq 1 $NOS); do
    CORES="$(( (i-1)*2 ))-$(( (i-1)*2+1 ))"
    ssh "$TORRE" "nohup taskset -c $CORES java $JVM_NO -cp ~/ppc.jar pt.ulisboa.ppc.no.AgenteNo \
        --coordenador $COORD_IP:9000 --id $i --threads $THREADS --modo $POLITICA --carga $CARGA \
        > ~/no$i.log 2>&1 < /dev/null &"
done

# 3. Versão do código, para o JSON: o commit atual, com "-alterado" se houver
#    alterações ao código por commitar (os ficheiros de data/ não contam).
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo sem-commit)

# 4. Correr o coordenador no portátil
java -cp target/ppc.jar pt.ulisboa.ppc.coordenador.Coordenador \
    --politica "$POLITICA" --ligacoes "$LIGACOES" --nome "$NOME" --commit "$COMMIT" "$@"
