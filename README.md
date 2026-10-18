# Distribuição de tarefas plana vs. hierárquica vs. hierárquica com work stealing

Projeto de Programação Paralela e Concorrente (FCUL). Gonçalo Lourenço (fc68092).

O estudo compara três políticas de distribuição de tarefas num cluster pequeno: um coordenador (portátil) e 3 nós de 2 cores (emulados na torre), ligados por cabo. A pergunta central é a partir de que tamanho de tarefa poupar comunicação deixa de compensar o pior equilíbrio de carga.

> Estado a 16 de outubro de 2026. Este ficheiro vai sendo atualizado ao longo do projeto e acabará por ser o README de reprodução.

## Estado

| Parte | Estado |
| --- | --- |
| Setup da torre | Feito e validado (`infra/setup_torre.md`) |
| Projeto Maven | Feito |
| Organização do código e testes automáticos | Feita: pacotes por responsabilidade, 13 testes JUnit |
| Carga sintética e calibração | Feito, calibrada na torre |
| Protocolo de comunicação | Feito |
| Política plana | Feita e testada (localhost, Wi-Fi e cabo direto) |
| Scripts de execução e registo | Feitos (`experiments/`) |
| Política hierárquica | Feita e testada (localhost e cabo direto) |
| Política hierárquica com work stealing | Por fazer |
| Carga Mandelbrot | Feita, caracterizada na torre e testada |
| Cabo direto portátil–torre | Feito: latência de 0,117 ms, 941 Mbit/s |
| Piloto, experiências finais, análise, paper | Por fazer |

## Estrutura do repositório

```
ppc-projeto/
├── pom.xml                      projeto Maven (Java 21, versões dos plugins fixadas)
├── .gitignore
├── README.md                    este ficheiro
├── src/main/java/pt/ulisboa/ppc/
│   ├── protocolo/               o que passa na rede (igual no coordenador e nos nós)
│   │   ├── Tarefa.java          record (id, parâmetro)
│   │   ├── Resultado.java       record (id, valor, duração em ns)
│   │   ├── Protocolo.java       tipos de mensagem
│   │   └── Canal.java           ligação TCP com mensagens binárias
│   ├── cargas/                  o cálculo das tarefas
│   │   ├── Carga.java           interface comum a todas as cargas
│   │   ├── Cargas.java          cria a carga pelo nome (coordenador e nós usam a mesma)
│   │   ├── Sintetica.java       tarefa sintética (xorshift64, tamanho em iterações)
│   │   ├── Mandelbrot.java      carga real: um bloco da imagem por tarefa
│   │   └── GeradorTarefas.java  listas de tarefas: uniforme, baralhada, agrupada, Mandelbrot
│   ├── comum/                   utilitários usados dos dois lados
│   │   ├── Argumentos.java      leitura de argumentos "--nome valor"
│   │   ├── Politica.java        enum PLANA / HIERARQUICA (nome na linha de comando)
│   │   └── Quadro.java          publicar um valor e esperar por um valor novo (wait/notifyAll)
│   ├── coordenador/             corre no portátil
│   │   ├── Coordenador.java     main: lê a configuração, aceita ligações, corre e mede as repetições
│   │   ├── Configuracao.java    record com os parâmetros de uma experiência (vai para o CSV)
│   │   ├── Distribuidor.java    a política do lado do coordenador: quantas tarefas por pedido
│   │   ├── Ligacao.java         atende uma ligação (HELLO, REQUEST, RESULTS)
│   │   ├── Repeticao.java       estado e métricas de uma repetição
│   │   └── RegistoCsv.java      escreve uma linha do CSV por repetição medida
│   ├── no/                      corre na torre
│   │   ├── AgenteNo.java        main: arranca o modo pedido
│   │   ├── NoPlano.java         uma ligação por thread, uma tarefa de cada vez
│   │   └── NoHierarquico.java   uma ligação por nó, lote repartido por uma fila local
│   └── ferramentas/             programas de medição avulsos (não fazem parte das experiências)
│       ├── Calibracao.java      mede ns por iteração e confirma cada tamanho de tarefa
│       └── CaracterizarMandelbrot.java  custo das tarefas num core, por tamanho de bloco
├── src/test/java/pt/ulisboa/ppc/   testes JUnit 5 (correm no mvn package)
│   ├── cargas/                  SinteticaTest, GeradorTarefasTest, MandelbrotTest
│   ├── protocolo/               CanalTest
│   └── coordenador/             DistribuidorTest
├── experiments/
│   ├── registar.sh              corre um comando e guarda tudo em data/testes/
│   └── correr.sh                lança os nós na torre e o coordenador no portátil, por política
├── data/
│   ├── calibracao_torre.txt     resultado da calibração na torre
│   ├── caracterizacao_mandelbrot_torre.txt   custo das tarefas do Mandelbrot na torre
│   └── testes/                  testes de desenvolvimento (pastas por teste, ping e iperf)
├── infra/
│   └── setup_torre.md           configuração completa da torre
├── analysis/                    (vazio por agora)
└── paper/                       (vazio por agora)
```

## Código

### Ideia central

As três políticas usam o mesmo código de cálculo (`Carga`) e o mesmo protocolo (`Canal`). A política está apenas em quantas tarefas o coordenador entrega por pedido (`--lote`) e em como o nó as reparte:

| Política | Ligações por nó | Tarefas por pedido | Estado |
| --- | --- | --- | --- |
| Plana | uma por thread de cálculo | 1 | Feita |
| Hierárquica | uma, com thread de comunicação e fila local | lote B | Feita |
| Hierárquica com work stealing | como a hierárquica, e um nó sem trabalho recebe metade da fila do nó mais carregado (via coordenador) | lote B | Por fazer |

### Organização

As dependências entre pacotes vão sempre no mesmo sentido: `protocolo` e `comum` não dependem de nada do projeto; `cargas` só usa a `Tarefa` do `protocolo`; `coordenador` e `no` usam os três, mas nunca um ao outro; `ferramentas` só usa `cargas`. Assim, o que vai pela rede e o cálculo são garantidamente iguais nos dois lados.

No coordenador, cada classe tem uma só responsabilidade. A política está toda no `Distribuidor`: a plana é o caso lote = 1. O work stealing vai entrar aqui e no `no/`, sem mexer no resto.

O padrão "uma thread publica, as outras esperam por um valor novo" aparece no coordenador (nova repetição) e no nó hierárquico (novo lote). Está escrito uma só vez, no `Quadro`.

### Protocolo

TCP com `setTcpNoDelay(true)` (sem o algoritmo de Nagle) e mensagens binárias com `DataOutputStream`. Cada mensagem começa com um byte de tipo.

| Mensagem | Sentido | Conteúdo | Tamanho |
| --- | --- | --- | --- |
| `HELLO` | nó → coordenador | id do nó, threads | 9 bytes |
| `REQUEST` | nó → coordenador | máximo de tarefas pedidas | 5 bytes |
| `TASKS` | coordenador → nó | n tarefas (id, parâmetro) | 5 + 12n bytes |
| `RESULTS` | nó → coordenador | n resultados (id, valor, duração) | 5 + 20n bytes |
| `DONE` | coordenador → nó | fim da experiência | 1 byte |

Regra para evitar deadlock: um nó envia sempre os resultados antes do próximo pedido.

### Funcionamento do coordenador

- As tarefas de uma repetição estão num array; cada pedido reserva um intervalo com uma operação atómica (`proxima.getAndAdd(k)`).
- As ligações ficam abertas entre repetições: os pedidos que chegam no fim de uma repetição esperam pela seguinte. No fim, cada ligação recebe `DONE`.
- As primeiras repetições (`--aquecimento`) servem de aquecimento do JIT e não são gravadas.
- Em cada repetição verifica que cada tarefa chegou exatamente uma vez. Com `--verificar sim`, compara também o XOR dos resultados com a versão sequencial. Se algo falhar, o programa para.

## Cargas

### Sintética

Ciclo xorshift64 sobre inteiros, sem alocar memória. Cada iteração tem 6 operações dependentes, por isso o tempo é linear nas iterações.

Cargas com a mesma duração média por tarefa:

| Carga | Composição |
| --- | --- |
| Uniforme | todas as tarefas com as iterações médias |
| Baralhada | 90% curtas (c) e 10% longas (10c), em ordem aleatória (semente 42), com c = média / 1,9 |
| Agrupada | as mesmas tarefas, com as longas todas no início da fila |

### Calibração na torre (core 0, 14 de outubro de 2026)

`ns por iteração = 1,6746`, que corresponde a ~6 ciclos a 3,6 GHz (frequência base do Ryzen 7 3700X): confirma que o boost está desligado.

| Tamanho | Iterações | Medido |
| --- | --- | --- |
| 10 µs | 5 972 | 10,03 µs |
| 100 µs | 59 715 | 100,69 µs |
| 1 ms | 597 153 | 1 000,07 µs |
| 10 ms | 5 971 530 | 10 000,29 µs |
| 100 ms | 59 715 298 | 100 006,92 µs |

### Mandelbrot

Imagem de 4096 × 4096 píxeis da região [-2, 1] × [-1,5, 1,5], com no máximo 1000 iterações por píxel. Cada tarefa calcula um bloco quadrado (lado 8, 32, 128 ou 512 píxeis); o `id` indica o bloco, por ordem de linhas, e o resultado é a soma das iterações do bloco. O desequilíbrio é natural e agrupado: os blocos dentro do conjunto custam muito mais e ficam seguidos na fila. As contas de vírgula flutuante dão o mesmo resultado no portátil e na torre, por isso a verificação funciona entre máquinas.

### Caracterização do Mandelbrot na torre (core 0, 14 de outubro de 2026)

| Lado | Tarefas | Total | Média | Mediana | p99 | Máximo |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | 262 144 | 8 045 ms | 30,7 µs | 0,4 µs | 180 µs | 207 µs |
| 32 | 16 384 | 8 111 ms | 495 µs | 4,7 µs | 2,9 ms | 3,7 ms |
| 128 | 1 024 | 8 126 ms | 7,9 ms | 77 µs | 46 ms | 46 ms |
| 512 | 64 | 8 103 ms | 127 ms | 1,6 ms | 736 ms | 736 ms |

- A soma das iterações é igual em todos os tamanhos (2 897 110 360): é a mesma imagem, cortada de forma diferente.
- O trabalho total é ~8,1 s num core, o que dá um ideal de ~1,35 s nos 6 cores dos nós.
- A média é dezenas de vezes maior do que a mediana: a maioria dos blocos é barata e poucos são muito caros. Com blocos de 8, metade das tarefas demora menos de 0,4 µs, muito abaixo da ida e volta na rede (117 µs).
- Com blocos de 512 há só 64 tarefas, e a maior demora 736 ms, mais de metade do tempo ideal: aqui o equilíbrio de carga é o que conta.

## Como compilar e correr

### Compilar

```bash
mvn package          # corre os testes e gera target/ppc.jar
```

### Testes automáticos

Correm sozinhos no `mvn package` (ou só eles com `mvn test`). O build para se algum falhar.

| Teste | O que garante |
| --- | --- |
| `SinteticaTest` | a mesma tarefa dá sempre o mesmo resultado; tarefas diferentes dão resultados diferentes |
| `GeradorTarefasTest` | as três cargas sintéticas têm o mesmo trabalho total; na agrupada as longas estão no início e valem 10× as curtas; a baralhada tem as mesmas tarefas noutra ordem; o número de blocos do Mandelbrot está certo |
| `MandelbrotTest` | um bloco de 128 vale a soma dos seus 16 sub-blocos de 32 (o corte não muda a imagem); um bloco dentro do conjunto vale 128 × 128 × 1000 |
| `CanalTest` | todas as mensagens chegam iguais ao outro lado; os bytes contados batem com o tamanho do protocolo |
| `DistribuidorTest` | com 6 threads a pedir ao mesmo tempo, cada tarefa é entregue exatamente uma vez e nenhum lote passa o limite; no fim todas recebem DONE |

O comportamento de ponta a ponta verifica-se com `--verificar sim` (compara com a versão sequencial em cada repetição).

### Caracterização do Mandelbrot

```bash
ssh fixo 'taskset -c 0 java -cp ~/ppc.jar pt.ulisboa.ppc.ferramentas.CaracterizarMandelbrot'
```

### Calibração na torre

```bash
scp target/ppc.jar fixo:~/ppc.jar
ssh fixo 'taskset -c 0 java -cp ~/ppc.jar pt.ulisboa.ppc.ferramentas.Calibracao'
```

### Teste local (tudo no portátil)

Terminal 1:

```bash
java -cp target/ppc.jar pt.ulisboa.ppc.coordenador.Coordenador --ligacoes 6 --tarefas 20000 \
    --iteracoes 5972 --desequilibrio baralhada --aquecimento 2 --repeticoes 3 --verificar sim
```

Terminal 2:

```bash
for i in 1 2 3; do java -cp target/ppc.jar pt.ulisboa.ppc.no.AgenteNo --coordenador localhost:9000 --id $i --threads 2 & done; wait
```

### Experiência portátil–torre

```bash
experiments/correr.sh <nome> <ip-do-portátil> <plana|hierarquica> <argumentos do coordenador...>
```

Com o cabo direto, o IP do portátil é `10.42.0.1`. Exemplos:

```bash
experiments/correr.sh cabo 10.42.0.1 plana --tarefas 20000 --iteracoes 5972 --verificar sim
experiments/correr.sh cabo 10.42.0.1 hierarquica --lote 100 --tarefas 20000 --iteracoes 5972 --verificar sim
experiments/correr.sh cabo 10.42.0.1 hierarquica --lote 20 --carga mandelbrot --lado 128 --verificar sim
```

O script compila, copia o jar para a torre, lança os 3 nós com `taskset` (cores 0-1, 2-3, 4-5) e as flags `-Xms512m -Xmx512m -XX:+UseSerialGC`, e corre o coordenador através do `registar.sh`. A política define o resto:

| Política | Modo dos nós | Ligações | Lote |
| --- | --- | --- | --- |
| `plana` | `plana` | 6 (uma por thread) | 1 |
| `hierarquica` | `hierarquica` | 3 (uma por nó) | o de `--lote` |

### Argumentos do coordenador

| Argumento | Omissão | Significado |
| --- | --- | --- |
| `--politica` | obrigatório | `plana` ou `hierarquica` (gravado no CSV) |
| `--porta` | 9000 | Porta de escuta |
| `--ligacoes` | 6 | Ligações a esperar (plana: nós × threads) |
| `--lote` | 1 | Máximo de tarefas por pedido (na plana tem de ser 1) |
| `--carga` | sintetica | `sintetica` ou `mandelbrot` (o `correr.sh` passa-a também aos nós) |
| `--tarefas` | 1000 | Sintética: número de tarefas por repetição |
| `--iteracoes` | obrigatório na sintética | Sintética: iterações médias por tarefa (da calibração) |
| `--desequilibrio` | uniforme | Sintética: `uniforme`, `baralhada` ou `agrupada` |
| `--lado` | 32 | Mandelbrot: lado do bloco em píxeis (tem de dividir 4096) |
| `--aquecimento` | 5 | Repetições descartadas |
| `--repeticoes` | 30 | Repetições medidas |
| `--saida` | resultados.csv | Ficheiro CSV (acrescenta linhas) |
| `--verificar` | nao | `sim` compara com a versão sequencial |

### Argumentos do nó

| Argumento | Omissão | Significado |
| --- | --- | --- |
| `--coordenador` | obrigatório | `ip:porta` do coordenador |
| `--id` | 0 | Identificador do nó |
| `--threads` | 2 | Threads de cálculo |
| `--modo` | plana | `plana` ou `hierarquica` |
| `--carga` | sintetica | `sintetica` ou `mandelbrot` (tem de ser a do coordenador) |

### Formato do CSV

Uma linha por repetição medida:

`politica, carga, lote, desequilibrio, tarefas, parametro, ligacoes, repeticao, tempo_ns, mensagens, bytes_enviados, soma_duracoes_ns`

O `parametro` são as iterações médias por tarefa na sintética e o lado do bloco no Mandelbrot. No Mandelbrot, o `desequilibrio` é `natural`. (Os CSV de testes anteriores a 14 de outubro, 22h30, não têm a coluna `carga` e chamam `iteracoes` ao `parametro`.)

## Registo de testes

Cada teste corrido com `experiments/registar.sh` (ou `correr.sh`) fica em `data/testes/<data>_<nome>/` (com o `correr.sh`, o nome começa pela política):

| Ficheiro | Conteúdo |
| --- | --- |
| `info.txt` | data, máquina, commit do git, alterações por commitar, versão do Java, comando exato |
| `saida.log` | tudo o que apareceu no terminal |
| `resultados.csv` | o CSV do coordenador |

Estes testes de desenvolvimento não entram nos resultados do paper. As medições a sério vão para `data/piloto/` e `data/final/`.

### Testes feitos

| Data | Teste | Resultado |
| --- | --- | --- |
| 14 out | Plana em localhost (20 000 tarefas de 10 µs, baralhada) | Correto; ~200 ms por repetição, contra ~35 ms de cálculo ideal |
| 14 out | Plana portátil–torre por Wi-Fi (mesma configuração) | Correto; ~9,2 s por repetição, ~2,8 ms por tarefa, mais de 99% do tempo à espera da rede |
| 14 out | iperf3 e ping por Wi-Fi | ~103 Mbit/s, instável (0 a 205 Mbit/s, com uma paragem); ping de 2,8 a 12,8 ms |
| 14 out | iperf3 e ping por cabo direto | 941 Mbit/s estáveis; ping 0,117 ms de média (0,100 a 0,187, desvio 0,012) com pings seguidos |
| 14 out | Plana portátil–torre por cabo direto (mesma configuração) | Correto; ~708 ms por repetição (variação de 0,5%), ~212 µs por tarefa |
| 15 out | Hierárquica, lote 100, por cabo direto (mesma configuração) | Correto; ~70 ms nas repetições estáveis (1.ª medida: 92 ms), 600 mensagens |
| 15 out | Caracterização do Mandelbrot num core da torre | ~8,1 s em todos os tamanhos; soma das iterações igual (2 897 110 360) |
| 15 out | Mandelbrot, plana e hierárquica, lados 8, 128 e 512, por cabo direto | Ver "Primeira comparação com o Mandelbrot" |
| 16 out | Reorganização do código: código antigo e novo em localhost, plana e hierárquica, sintética e Mandelbrot, com `--verificar sim` | Mensagens, bytes e CSV iguais nos dois (18 000 / 180 / 156 mensagens); 13 testes JUnit a passar |

### Rede: Wi-Fi vs. cabo direto

| Medida | Wi-Fi | Cabo direto |
| --- | --- | --- |
| Latência (pings seguidos) | ~2,8 ms, com saltos até 12,8 ms | **0,117 ms** (desvio 0,012 ms) |
| Largura de banda | ~103 Mbit/s, instável | **941 Mbit/s**, estável |
| Plana, 20 000 tarefas de 10 µs | ~9 230 ms | **~708 ms** |

O Wi-Fi não serve para medir. A latência deve ser medida com pings seguidos (`sudo ping -c 200 -i 0.002 fixo`): com o ping normal (um por segundo) dá 0,341 ms, porque entre pacotes o adaptador e os cores entram em poupança de energia.

### Primeira comparação (cabo direto, 20 000 tarefas de 10 µs, baralhada)

| Política | Tempo por repetição | Mensagens | Face ao ideal (~33 ms) |
| --- | --- | --- | --- |
| Plana | ~708 ms | 60 000 | ~21× |
| Hierárquica, lote 100 | ~70 ms | 600 | ~2× |

A hierárquica é ~10 vezes mais rápida nesta configuração, como a H1 prevê para tarefas pequenas. Ainda fica ~2× acima do ideal: cada nó espera uma ida e volta por lote e, no fim de cada lote, uma thread pode ficar parada enquanto a outra acaba uma tarefa longa. Lotes maiores reduzem as esperas, mas pioram o equilíbrio; o valor do lote escolhe-se no piloto.

A primeira repetição medida da hierárquica (92 ms) ficou acima das seguintes (~70 ms): 2 repetições de aquecimento não chegaram. No piloto, o número de repetições de aquecimento tem de ser escolhido com um gráfico do tempo por repetição.

### Primeira comparação com o Mandelbrot (cabo direto, 14 de outubro)

Testes curtos (2 de aquecimento, 3 medidas), sem valor estatístico, mas que já mostram as duas pontas das hipóteses. Ideal: ~1,35 s (8,1 s de cálculo em 6 cores).

| Lado | Tarefas | Plana | Hierárquica | Lote | Mensagens (plana / hier.) | Melhor |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | 262 144 | ~10,3 s | **~1,48 s** | 1000 | 786 432 / 789 | Hierárquica, ~7× |
| 128 | 1 024 | **~1,38 s** | ~1,41 s | 20 | 3 072 / 156 | Plana, ~2% (por confirmar) |
| 512 | 64 | **~1,50 s** | ~2,89 s | 22 | 192 / 9 | Plana, ~1,9× |

- **H1 (tarefas pequenas):** com blocos de 8, a plana passa ~87% do tempo à espera. O tempo a mais dá (10,3 − 1,35) × 6 / 262 144 ≈ **205 µs por tarefa**, praticamente o mesmo custo fixo medido com a carga sintética (~212 µs). O custo da plana é previsível a partir da latência.
- **H2 (tarefas grandes, desequilíbrio):** com blocos de 512 e lote 22, cada nó recebe um só lote de blocos seguidos (partição fixa). Os blocos do conjunto estão no meio da imagem e ficam quase todos no mesmo nó, que demora ~2,9 s enquanto os outros acabam cedo.
- **H3 (cruzamento):** a vantagem passa da hierárquica para a plana algures entre os blocos de 8 e de 128.
- O resultado da hierárquica depende muito do lote: com blocos de 512, um lote menor equilibraria melhor. É este o caso que o work stealing (RQ2) deve corrigir.

### Primeira leitura da plana (cabo direto)

Cada thread fez ~3 333 tarefas em 0,708 s, ou seja, ~212 µs por tarefa, dos quais só ~10 µs de cálculo (95% de espera). A ida e volta da rede é de 117 µs; os ~85 µs restantes são o custo fixo de acordar threads e processar mensagens. O ideal seria 20 000 × 10 µs / 6 ≈ 33 ms, por isso a plana fica ~21 vezes acima do ideal nesta configuração.

Previsão para a H1, a partir da latência medida:

| Tarefa | Cálculo / (cálculo + 117 µs) na plana |
| --- | --- |
| 10 µs | ~8% |
| 100 µs | ~46% |
| 1 ms | ~90% |
| 10 ms | ~99% |

## Próximos passos

1. Tempo parado por thread, medido nos nós.
2. Linhas de base (1 core, 1 nó) e estudo do aquecimento da JVM.
3. Escolha do tamanho do lote e do número de repetições de aquecimento.
4. Piloto até ao check-in de 11 de novembro.
5. Work stealing.
