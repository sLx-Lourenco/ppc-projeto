# Setup da torre (PC fixo)

Projeto de Programação Paralela e Concorrente. Gonçalo Lourenço (fc68092).
Configurado a 14 de outubro de 2026.

A torre faz de "cluster": corre 3 nós, cada um preso a 2 cores. O portátil é o coordenador e liga-se à torre por cabo direto (desde 14 de outubro, 22h).

## 1. Hardware

### Torre

| Componente | Detalhes |
| --- | --- |
| Modelo | ASUS ROG Strix GA15DH (pré-montado), BIOS versão 303 |
| Processador | AMD Ryzen 7 3700X, 8 cores físicos (16 threads com SMT) |
| Memória | 32 GB DDR4 (2 × 16 GB Corsair), D.O.C.P Profile#1 a 3200 MHz |
| Disco do Windows | INTEL SSDPEKNW512G8 (512 GB, NVMe, BitLocker). **Não mexer.** |
| Disco do Ubuntu | SSD externo Crucial X10 (Micron CT1000X10SSD9, 1 TB), ligado por USB-C |
| Placa de rede | `enp8s0` |

### Portátil (coordenador)

| Componente | Detalhes |
| --- | --- |
| Modelo | Lenovo IdeaPad 5 2-in-1 14IRH9 |
| Processador | Intel Core i7-13620H, 16 GB de RAM |
| Sistema | Ubuntu 24.04.5 LTS |
| Utilizador | `slx` |
| Adaptador de rede | USB-C para Ethernet Gigabit, interface `enx00e06c79be37` |

## 2. BIOS da torre

Entra-se com **Del** no arranque. O menu de arranque é **F8**.

| Definição | Valor | Notas |
| --- | --- | --- |
| EZ System Tuning | Normal | Não usar "Performance", que faz overclock |
| D.O.C.P | Profile#1, DDR4-3200 | Não mudar daqui em diante |
| Ordem de arranque | 1.º SSD externo (ubuntu), 2.º pen, 3.º Windows | Sem o SSD ligado, arranca o Windows |
| SMT, boost e C-states | Como vieram | São controlados no Ubuntu |
| QFan Control (CPU FAN) | Standard ou Turbo | Recomendado, para evitar thermal throttling |

## 3. Instalação do Ubuntu

| Ecrã | Escolha |
| --- | --- |
| Imagem | `ubuntu-24.04.5-live-server-amd64.iso`, gravada numa pen com o "Criador de discos de arranque" |
| Tipo | Ubuntu Server (não o minimized), sem drivers de terceiros |
| Rede | DHCP automático (router); proxy em branco; mirror por omissão |
| Disco | *Use an entire disk* → SSD externo de 931.513G; LVM ligado, **sem encriptação** (LUKS desligado) |
| Perfil | Nome `Lourenco`; servidor `fixo`; utilizador `fixo` |
| SSH | *Install OpenSSH server*, com autenticação por palavra-passe |
| Snaps | Nenhum |

Resultado: Ubuntu 24.04.5 LTS, kernel `6.8.0-147-generic`.

## 4. Acesso por SSH a partir do portátil

No portátil:

```bash
ssh-keygen -t ed25519
ssh-copy-id fixo@192.168.1.90
```

Em `~/.ssh/config` do portátil:

```
Host fixo
    HostName 192.168.1.90
    User fixo
```

Para entrar: `ssh fixo` (sem palavra-passe).

> Durante a instalação a torre estava no router, com o IP `192.168.1.90`. Desde a mudança para o cabo direto (secção 8), o `HostName` é `10.42.0.2`.

## 5. Configuração da torre

### Atualizar

```bash
sudo apt update && sudo apt upgrade -y
sudo reboot
```

### Software

```bash
sudo apt install -y openjdk-21-jre-headless linux-tools-common linux-tools-$(uname -r) lm-sensors
sudo apt install -y iperf3 htop sysstat     # medir a rede e ver o uso dos cores
```

No `iperf3`, responder **No** a "Iniciar o Iperf3 automaticamente como um daemon": o servidor só se liga quando é preciso (`iperf3 -s -1`).

### Desligar o que interfere nas medições

```bash
# Impedir a suspensão
sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target

# Desligar as atualizações automáticas (não podem correr a meio de uma medição)
sudo systemctl disable --now unattended-upgrades apt-daily.timer apt-daily-upgrade.timer

# Serviços do sistema só nos cores 6 e 7 (os cores 0 a 5 ficam para os nós)
sudo systemctl set-property system.slice AllowedCPUs=6-7

# Tarefas agendadas que podem correr a meio de uma medição (ver todas com: systemctl list-timers --all)
sudo systemctl disable --now sysstat-collect.timer sysstat-summary.timer motd-news.timer
```

As tarefas agendadas que ficam ligadas (`fstrim`, `logrotate`, `man-db`) correm nos cores 6-7.

### SMT desligado pelo kernel

Escrever `off` em `/sys/devices/system/cpu/smt/control` dava **I/O error**, por isso o SMT é desligado com a opção de arranque `nosmt`. Fica só no GRUB do SSD do Ubuntu e não afeta o Windows.

```bash
sudo sed -i 's/^GRUB_CMDLINE_LINUX_DEFAULT="\(.*\)"/GRUB_CMDLINE_LINUX_DEFAULT="\1 nosmt"/' /etc/default/grub
sudo update-grub
```

Em `/etc/default/grub` fica: `GRUB_CMDLINE_LINUX_DEFAULT=" nosmt"`.

### Serviço que prepara o CPU em cada arranque

Ficheiro `/etc/systemd/system/cpu-performance.service`:

```ini
[Unit]
Description=Configuracao do CPU para medicoes

[Service]
Type=oneshot
ExecStart=/usr/bin/cpupower frequency-set -g performance
ExecStart=/usr/bin/cpupower idle-set -D 2
ExecStart=-/bin/sh -c 'echo 0 > /sys/devices/system/cpu/cpufreq/boost'

[Install]
WantedBy=multi-user.target
```

| Linha | Efeito |
| --- | --- |
| `frequency-set -g performance` | Governador `performance`: frequência fixa |
| `idle-set -D 2` | Desativa os estados de repouso profundos (C-states), que demoram mais a acordar e penalizariam as tarefas pequenas |
| `echo 0 > .../boost` | Desliga o boost, para a frequência não variar com a temperatura |

Ativado com:

```bash
sudo systemctl enable cpu-performance.service
sudo systemctl daemon-reload
sudo reboot
```

## 6. Validação

Comando, na torre:

```bash
cat /sys/devices/system/cpu/smt/control /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor /sys/devices/system/cpu/cpufreq/boost; nproc; systemctl is-failed cpu-performance.service
```

Resultado obtido a 14 de outubro de 2026:

| Saída | Significado |
| --- | --- |
| `off` | SMT desligado |
| `performance` | Governador |
| `0` | Boost desligado |
| `8` | Cores físicos disponíveis |
| `inactive` | O serviço correu até ao fim, sem erros |

- **Java na torre:** OpenJDK 21.0.12.1 (2026-08-18), build `21.0.12.1+1-1-24.04.4-Ubuntu`. O portátil tem a mesma versão.
- **Temperatura:** `sensors | grep Tctl`, cerca de 43 °C em repouso.

## 7. Divisão dos cores nas experiências

| Função | Cores | Comando |
| --- | --- | --- |
| Nó 1 | 0 e 1 | `taskset -c 0-1` |
| Nó 2 | 2 e 3 | `taskset -c 2-3` |
| Nó 3 | 4 e 5 | `taskset -c 4-5` |
| Sistema operativo | 6 e 7 | `system.slice` |

Os cores 0-3 e 4-7 estão em dois grupos (CCX), cada um com a sua cache L3. Os nós 1 e 2 partilham uma cache; o nó 3 partilha a outra com o sistema. Como as tarefas quase não usam memória, o efeito deve ser pequeno, mas entra nas ameaças à validade.

Flags da JVM nos nós: `-Xms512m -Xmx512m -XX:+UseSerialGC`.

## 8. Cabo direto (feito a 14 de outubro)

Foi feito todo por SSH, sem monitor na torre: prepara-se a rede nova com a torre ainda no router, aplica-se, e só depois se troca o cabo.

1. Na torre, impedir que a configuração de rede seja reescrita:
   ```bash
   echo 'network: {config: disabled}' | sudo tee /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg
   ```
2. Na torre, substituir o conteúdo de `/etc/netplan/50-cloud-init.yaml` por:
   ```yaml
   network:
     version: 2
     ethernets:
       enp8s0:
         dhcp4: false
         addresses: [10.42.0.2/24]
         routes:
           - to: default
             via: 10.42.0.1
         nameservers:
           addresses: [10.42.0.1]
   ```
   validar sem aplicar:
   ```bash
   sudo chmod 600 /etc/netplan/50-cloud-init.yaml
   sudo netplan generate
   ```
3. No portátil, criar a ligação partilhada (o nome do adaptador vê-se com `nmcli device`):
   ```bash
   sudo nmcli connection add type ethernet ifname enx00e06c79be37 con-name cluster ipv4.method shared ipv4.addresses 10.42.0.1/24
   ```
4. Na torre, aplicar com `sudo netplan apply` (a sessão SSH congela; fechar com Enter, `~`, `.`).
5. Passar o cabo da torre do router para o adaptador do portátil e, no portátil:
   ```bash
   sudo nmcli connection up cluster
   sed -i 's/HostName 192.168.1.90/HostName 10.42.0.2/' ~/.ssh/config
   echo '10.42.0.2 fixo' | sudo tee -a /etc/hosts
   ```
   A linha no `/etc/hosts` é precisa porque, sem o router, o nome `fixo` deixa de ser conhecido pelo `ping` e pelo `iperf3` (o `ssh` usa o `~/.ssh/config`).
6. Na primeira ligação, o SSH pergunta se confia no novo endereço: a chave é a mesma da torre, responder `yes`.

> Até trocar o cabo, não reiniciar a torre: arrancaria já com `10.42.0.2`, que o router não conhece.

### Medições da rede

Feitas a partir do portátil:

```bash
sudo ping -c 200 -i 0.002 fixo | tee data/testes/$(date +%Y-%m-%d_%H%M%S)_ping_cabo.txt
ssh fixo 'iperf3 -s -1' &
sleep 1
iperf3 -c fixo | tee data/testes/$(date +%Y-%m-%d_%H%M%S)_iperf_cabo.txt
```

| Medida | Wi-Fi (antes) | Cabo direto |
| --- | --- | --- |
| Latência, pings seguidos | ~2,8 ms, saltos até 12,8 ms | **0,117 ms** (mín. 0,100, máx. 0,187, desvio 0,012), 0% perdidos |
| Latência, ping normal (1 por segundo) | ~5,5 ms | 0,341 ms |
| Largura de banda (iperf3) | ~103 Mbit/s, instável | **941 Mbit/s**, estável |

A diferença entre os pings seguidos e o ping normal vem da poupança de energia entre pacotes. O valor que conta para as experiências, onde o tráfego é contínuo, é o dos pings seguidos.

## 9. Por fazer

- [ ] Na torre, desligar o aviso de upgrade para a 26.04. **Não fazer o upgrade.**
  ```bash
  sudo sed -i 's/^Prompt=.*/Prompt=never/' /etc/update-manager/release-upgrades
  ```
- [ ] Confirmar que as tarefas agendadas da secção 5 ficaram desligadas (`systemctl list-timers --all`).

## 10. Uso no dia a dia

1. Ligar o SSD externo e o cabo de rede (ao adaptador do portátil) **antes** de ligar a torre.
2. Carregar no botão da torre e esperar cerca de 1 minuto. No portátil, a ligação `cluster` ativa-se sozinha quando o adaptador está ligado (se não, `sudo nmcli connection up cluster`).
3. No portátil, `ssh fixo`. O `fixo login:` que aparece no monitor da torre pode ser ignorado.
4. Antes de medir, correr a validação da secção 6.
5. Para desligar: `ssh -t fixo sudo poweroff`, ou um toque curto no botão.

### Cuidados

- Depois de uma atualização que traga um kernel novo, reinstalar `linux-tools-$(uname -r)`. Sem isso, o `cpupower` falha e o serviço deixa de fixar o governador.
- Não correr `do-release-upgrade`: manter a 24.04, igual ao portátil.
- Usar sempre o IP do cabo do portátil (`10.42.0.1`) como endereço do coordenador, nunca o do Wi-Fi (`192.168.1.84`).
- A torre não precisa de internet. Quando precisar (instalar pacotes), tem-na através da partilha do portátil.
