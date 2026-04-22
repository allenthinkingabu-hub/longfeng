# 企业级云原生平台落地方案
## IDC 3 节点 + 阿里云 Spot 混合拓扑 — kubeadm 原生 Kubernetes

> **文档定位**：本方案是《企业级云原生平台方案_Rancher_K8s_Helm_Harbor.md》与《AI落地实施计划_通用日历系统.md》在**受限资源场景**下的工程落地版本。覆盖 **dev + 小型 stg 一体化**，CI 构建 / 压测 / 灾备放在**阿里云 Spot ECS**，用完即销毁。
>
> **硬件约束**：
> - **IDC**：3 台物理机，每台 **8 核 CPU / 16 GB 内存 / 80 GB 系统盘 / 100 GB 数据盘**
> - **网络**：机房内网 192.168.10.0/24，出口公网或 NAT
> - **云端**：阿里云 VPC（杭州可用区 H）+ Spot ECS 按需启动
>
> **技术选型**：
> - Kubernetes：**kubeadm 原生 v1.28.10**（**不用** K3s、**不用** RKE2）
> - 容器运行时：containerd 1.7
> - CNI：Calico 3.27（VXLAN 模式）
> - 存储：Longhorn 1.6（三副本分布式块存储）
> - 入口：MetalLB（L2）+ ingress-nginx
> - GitOps：Rancher 2.8 + Harbor 2.10 + Gitea 1.21 + ArgoCD 2.10
> - 数据：CloudNativePG 1.22 + PG 16、Redis Sentinel、Nacos 2.3、RocketMQ 5.1、ES 8.12
>
> **非目标**：生产级多活、PB 级数据、严格等保合规。

---

## 目录

- [0. 关键决策与取舍](#0-关键决策与取舍)
- [1. 整体拓扑](#1-整体拓扑)
- [2. 容量规划](#2-容量规划)
- [3. 网络规划](#3-网络规划)
- [4. 操作系统与基础软件准备](#4-操作系统与基础软件准备)
- [5. kubeadm 集群初始化（IDC）](#5-kubeadm-集群初始化idc)
- [6. Calico CNI](#6-calico-cni)
- [7. Longhorn 存储](#7-longhorn-存储)
- [8. MetalLB + ingress-nginx](#8-metallb--ingress-nginx)
- [9. cert-manager](#9-cert-manager)
- [10. Rancher Server 部署](#10-rancher-server-部署)
- [11. Harbor 部署](#11-harbor-部署)
- [12. Gitea 部署](#12-gitea-部署)
- [13. ArgoCD 部署](#13-argocd-部署)
- [14. GitLab Runner（Kaniko executor on Spot）](#14-gitlab-runnerkaniko-executor-on-spot)
- [15. 可观测栈：Prometheus/Grafana/Loki/Tempo](#15-可观测栈prometheusgrafanalokitempo)
- [16. CloudNativePG（PostgreSQL HA）](#16-cloudnativepgpostgresql-ha)
- [17. Redis Sentinel](#17-redis-sentinel)
- [18. Nacos 集群](#18-nacos-集群)
- [19. RocketMQ 集群](#19-rocketmq-集群)
- [20. Elasticsearch（ECK）](#20-elasticsearcheck)
- [21. 环境隔离：dev vs stg](#21-环境隔离dev-vs-stg)
- [22. 阿里云突发集群（Terraform + kubeadm）](#22-阿里云突发集群terraform--kubeadm)
- [23. Rancher 纳管云端集群](#23-rancher-纳管云端集群)
- [24. WireGuard 跨网组网](#24-wireguard-跨网组网)
- [25. CI/CD 流水线](#25-cicd-流水线)
- [26. ArgoCD ApplicationSet 跨集群分发](#26-argocd-applicationset-跨集群分发)
- [27. 备份与灾备](#27-备份与灾备)
- [28. 故障演练 Runbook](#28-故障演练-runbook)
- [29. 验收清单](#29-验收清单)
- [附录 A：节点标签与调度策略](#附录-a节点标签与调度策略)
- [附录 B：资源 Requests/Limits 总表](#附录-b资源-requestslimits-总表)
- [附录 C：常见故障排查](#附录-c常见故障排查)

---

## 0. 关键决策与取舍

### 0.1 为什么 3 节点全部 control-plane + worker（stacked 混合模式）

| 方案 | 节点分配 | 控制面 HA | 可用业务容量 | 评价 |
|---|---|---|---|---|
| A：1 master + 2 worker | 1 × master, 2 × worker | ❌ master 宕机即瘫 | ~28 GB | ✗ 放弃 |
| B：3 master 专用 | 3 × control-plane（NoSchedule） | ✅ | 0 GB | ✗ 浪费 |
| **C：3 stacked 混合** | **3 × control-plane + schedulable** | **✅ etcd/apiserver 三副本** | **~40 GB** | **✓ 选用** |

具体做法：

```bash
# 每个节点 kubeadm init/join 完成后，去掉控制面的 NoSchedule taint
kubectl taint nodes --all node-role.kubernetes.io/control-plane:NoSchedule-
```

### 0.2 固定在 IDC vs 可以上云的组件边界

| 分类 | 组件 | 位置 | 原因 |
|---|---|---|---|
| 控制面 | etcd / apiserver / scheduler / CM | **IDC** | 延迟敏感 |
| 管理面 | Rancher / Harbor / Gitea / ArgoCD / Vault | **IDC** | 稳态，对公网访问需要但数据不应外流 |
| 数据面 | PG / Redis / MQ / Nacos / ES | **IDC** | 延迟敏感；业务 Pod 同网 |
| 业务 Pod | 日历 4 个微服务 (dev + stg) | **IDC** | 与数据面同网 |
| CI 构建 | Kaniko build Pod / SAST | **云 Spot** | CPU/IO 峰值大，会拖垮 IDC |
| 压测 | JMeter / k6 | **云 Spot** | 不能与被压对象同节点 |
| 灾备 | PG Standby / Harbor 镜像 / ES CCR | **云 Spot / OSS** | 异地容灾 |

### 0.3 关键版本锁定

```yaml
kubernetes: "1.28.10"
containerd: "1.7.13"
calico: "3.27.2"
longhorn: "1.6.1"
metallb: "0.14.3"
ingress-nginx: "4.10.0"    # chart
cert-manager: "1.14.4"
rancher: "2.8.3"
harbor: "1.14.2"           # chart (=> harbor 2.10.1)
gitea: "10.1.0"            # chart
argo-cd: "6.7.10"          # chart
gitlab-runner: "0.63.0"    # chart
kube-prometheus-stack: "57.2.0"
loki: "5.47.2"
tempo: "1.9.0"
cloudnative-pg: "0.20.2"
postgresql: "16.2"
redis-operator: "1.2.4"
nacos: "2.3.1"
rocketmq-operator: "1.0.3"
eck-operator: "2.12.1"
velero: "1.13.2"
wireguard: "host-package"
```

---

## 1. 整体拓扑

```
                     ┌────────────────────────────────────────────┐
                     │          阿里云 VPC (cn-hangzhou-h)         │
                     │         172.16.0.0/16                       │
                     │                                              │
   Dev / Ops         │    ┌───────────────────────────────────┐    │
     │  HTTPS        │    │  Burst Cluster (kubeadm 或 ACK)   │    │
     │               │    │                                    │    │
     ▼               │    │  cloud-m1  (固定 t6, 2C4G)        │    │
 ┌─────────┐         │    │  cloud-w1..N  (Spot 4C8G 按需)    │    │
 │  公网   │         │    │                                    │    │
 │ DNS+LB  │         │    │  • GitLab Runner (Kaniko)          │    │
 └────┬────┘         │    │  • JMeter 压测 Pod                 │    │
      │              │    │  • PG Standby (异步流复制)         │    │
      │              │    │  • Harbor 镜像复制 slave           │    │
      │              │    │  • Velero 备份消费者               │    │
      ▼              │    └───────────────────────────────────┘    │
 ┌─────────┐         └─────────────┬──────────────────────────────┘
 │ IDC 网关 │ ◄─── WireGuard VPN ───┘
 │ 路由/NAT │
 │ + DNS   │
 └────┬────┘
      │ 192.168.10.0/24
      ▼
┌───────────────── IDC K8s Cluster (kubeadm v1.28.10) ─────────────────┐
│                                                                        │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐            │
│   │  node-01     │    │  node-02     │    │  node-03     │            │
│   │ 192.168.10.11│    │ 192.168.10.12│    │ 192.168.10.13│            │
│   │ 8C/16G/100G  │    │ 8C/16G/100G  │    │ 8C/16G/100G  │            │
│   ├──────────────┤    ├──────────────┤    ├──────────────┤            │
│   │ etcd         │    │ etcd         │    │ etcd         │ 控制面 HA │
│   │ kube-apiserv │    │ kube-apiserv │    │ kube-apiserv │            │
│   │ scheduler    │    │ scheduler    │    │ scheduler    │            │
│   │ controller-m │    │ controller-m │    │ controller-m │            │
│   ├──────────────┤    ├──────────────┤    ├──────────────┤            │
│   │ Rancher (1)  │    │ Harbor       │    │ ArgoCD       │ 平台面    │
│   │ MetalLB L2   │    │ Gitea        │    │ RunnerMgr    │            │
│   │ ingress-nginx│    │ Prom/Graf    │    │ Loki/Tempo   │            │
│   ├──────────────┤    ├──────────────┤    ├──────────────┤            │
│   │ PG-Primary   │    │ PG-Replica   │    │ PG-Replica   │ 数据面    │
│   │ Redis Master │    │ Redis Slave  │    │ Redis Sentinel│           │
│   │ Nacos-1      │    │ Nacos-2      │    │ Nacos-3      │            │
│   │ MQ NS+B1     │    │ MQ B2        │    │ MQ B3        │            │
│   │ ES-1         │    │ ES-2         │    │ ES-3         │            │
│   ├──────────────┤    ├──────────────┤    ├──────────────┤            │
│   │ dev/stg Pods │    │ dev/stg Pods │    │ dev/stg Pods │ 业务面    │
│   └──────────────┘    └──────────────┘    └──────────────┘            │
│                                                                        │
│   共享存储：Longhorn 三副本（每节点 100G 数据盘，总容量 300G / 3 = 100G 有效）│
│   对外 VIP：192.168.10.100 (MetalLB) → ingress-nginx                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 容量规划

### 2.1 单节点 16 GB 内存精确预算（以 node-01 为例，最重）

| 组件 | Memory Request | Memory Limit | 说明 |
|---|---|---|---|
| **系统层** | | | |
| OS + kernel + systemd | - | 1.0 GB | 预留 |
| containerd + kubelet | - | 0.3 GB | |
| kube-apiserver | 512 Mi | 1.5 GB | |
| etcd | 256 Mi | 800 Mi | snapshots + WAL |
| kube-controller-manager | 128 Mi | 512 Mi | |
| kube-scheduler | 128 Mi | 256 Mi | |
| kube-proxy | 64 Mi | 256 Mi | |
| calico-node | 128 Mi | 512 Mi | |
| longhorn-manager/instance | 256 Mi | 1.0 GB | |
| ingress-nginx | 128 Mi | 512 Mi | |
| **小计：控制/系统层** | | **~6.7 GB** | |
| **平台层 (Rancher 在本节点)** | | | |
| Rancher Server | 1.0 GB | 2.0 GB | 1 replica 够用 |
| MetalLB speaker | 64 Mi | 256 Mi | |
| cert-manager | 64 Mi | 256 Mi | |
| **小计：平台层** | | **~2.5 GB** | |
| **数据层（PG Primary + Redis Master + Nacos-1 + MQ NS+B1 + ES-1）** | | | |
| PG Primary | 1.0 GB | 2.0 GB | shared_buffers=1G |
| Redis Master | 256 Mi | 512 Mi | maxmemory=384M |
| Nacos-1 | 768 Mi | 1.5 GB | heap=1G |
| RocketMQ NS + B1 | 1.2 GB | 2.5 GB | broker heap=1.5G |
| ES-1 | 1.2 GB | 2.0 GB | heap=1G |
| **小计：数据层** | | **~8.5 GB** | |
| **业务层** | | | |
| dev/stg 业务 Pod | 256 Mi × 4 | 512 Mi × 4 | 平均分布 |
| **小计：业务层** | | **~2.0 GB** | |
| **总计** | | **~19.7 GB (Limit) / ~11.2 GB (Request)** | 依赖 Limit overcommit |

> **关键**：Request 总和 ~11.2 GB，留出 ~4.8 GB 系统缓冲；Limit overcommit 到 ~19.7 GB 是可接受的（K8s 设计即如此）。**前提是 PG/ES/MQ 这些 heap 型服务各自用 JVM/配置 hard limit 控好**，不会集体吃满。

### 2.2 3 节点总容量

| 维度 | 单节点 | 集群总计 | 可分配给业务 |
|---|---|---|---|
| CPU | 8 核 | 24 核 | ~15 核 |
| Memory | 16 GB | 48 GB | ~20 GB |
| 数据盘 | 100 GB | 300 GB（Longhorn 三副本）→ **100 GB 有效** | ~70 GB（PG/MQ/ES 占大头） |

### 2.3 存储分配（Longhorn 有效 100 GB）

| PVC | 大小 | 说明 |
|---|---|---|
| harbor-registry | 20 GB | 容器镜像 |
| harbor-chartmuseum | 2 GB | Helm Chart |
| pg-primary + 2 replica | 15 GB × 3 = 45 GB | **注意 Longhorn 已三副本，每个 PVC 大小即其占用** |
| es-data × 3 | 8 GB × 3 = 24 GB | |
| rocketmq-store × 3 | 5 GB × 3 = 15 GB | |
| loki-chunks | 10 GB | |
| prometheus | 15 GB | 保留 7 天 |
| 其他（gitea/argocd/nacos） | ~10 GB | |
| **合计** | **~141 GB** | ❗ 超了 |

**调整策略**：
- PG/ES 的 replica 走 Longhorn 单副本即可（本身已逻辑复制），Longhorn replicaCount 设为 1，节省 2/3
- Prometheus 保留 7 天 + remote_write 到云端长期存储
- harbor-registry 配 GC cronjob

调整后：
| PVC | 大小 | Longhorn 副本数 | 实际占用 |
|---|---|---|---|
| PG × 3 | 15 GB × 3 | 1（逻辑流复制已在应用层） | 45 GB |
| ES × 3 | 8 GB × 3 | 1 | 24 GB |
| RocketMQ × 3 | 5 GB × 3 | 1 | 15 GB |
| Harbor registry | 20 GB | 3 | 60 GB |
| Prometheus | 15 GB | 1 | 15 GB |
| Loki | 10 GB | 1 | 10 GB |
| Gitea + ArgoCD + Nacos | 10 GB | 1 | 10 GB |
| **合计** | | | **~179 GB** / 300 GB |

---

## 3. 网络规划

### 3.1 地址段

| 网络 | CIDR | 说明 |
|---|---|---|
| IDC 物理网 | 192.168.10.0/24 | node-01/02/03 为 .11/.12/.13，网关 .1 |
| MetalLB 池 | 192.168.10.100-192.168.10.120 | 分配给 LoadBalancer Service |
| Pod CIDR | 10.244.0.0/16 | Calico |
| Service CIDR | 10.96.0.0/12 | K8s 默认 |
| WireGuard | 10.200.0.0/24 | VPN 内网 |
| 阿里云 VPC | 172.16.0.0/16 | |
| 阿里云 Pod | 10.245.0.0/16 | 云端 K8s |
| 阿里云 Service | 10.97.0.0/12 | |

### 3.2 DNS

内部域名统一使用 `*.idc.calendar.internal`，外部使用 `*.cal.example.com`。

| FQDN | 解析 | 用途 |
|---|---|---|
| rancher.cal.example.com | 192.168.10.100 (内) / 公网 IP | Rancher UI |
| harbor.cal.example.com | 192.168.10.100 | 镜像仓库 |
| git.cal.example.com | 192.168.10.100 | Gitea |
| argocd.cal.example.com | 192.168.10.100 | ArgoCD |
| grafana.cal.example.com | 192.168.10.100 | Grafana |
| prom.cal.example.com | 192.168.10.100 | Prometheus |
| *.dev.cal.example.com | 192.168.10.100 | 业务 dev |
| *.stg.cal.example.com | 192.168.10.100 | 业务 stg |

### 3.3 防火墙（iptables / firewalld）

```bash
# 所有节点互通（控制面需要）
firewall-cmd --permanent --add-source=192.168.10.0/24 --zone=trusted
firewall-cmd --permanent --add-source=10.244.0.0/16 --zone=trusted   # Pod
firewall-cmd --permanent --add-source=10.96.0.0/12 --zone=trusted    # Service
firewall-cmd --permanent --add-source=10.200.0.0/24 --zone=trusted   # VPN
# WireGuard UDP
firewall-cmd --permanent --add-port=51820/udp
firewall-cmd --reload
```

---

## 4. 操作系统与基础软件准备

**假设 OS：Ubuntu 22.04 LTS**（如果是 Rocky/CentOS 9 替换 apt 为 dnf）

### 4.1 系统配置（每台节点）

```bash
# 1. 主机名
sudo hostnamectl set-hostname node-01   # 对应节点改
cat <<EOF | sudo tee -a /etc/hosts
192.168.10.11 node-01
192.168.10.12 node-02
192.168.10.13 node-03
EOF

# 2. 关闭 swap（K8s 硬性要求）
sudo swapoff -a
sudo sed -i '/ swap / s/^\(.*\)$/#\1/' /etc/fstab

# 3. 时间同步
sudo apt update && sudo apt install -y chrony
sudo systemctl enable --now chrony

# 4. 内核模块
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF
sudo modprobe overlay
sudo modprobe br_netfilter

# 5. sysctl
cat <<EOF | sudo tee /etc/sysctl.d/99-k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
vm.swappiness                       = 0
vm.overcommit_memory                = 1
kernel.panic                        = 10
kernel.panic_on_oops                = 1
fs.inotify.max_user_instances       = 8192
fs.inotify.max_user_watches         = 524288
EOF
sudo sysctl --system
```

### 4.2 数据盘挂载（100G）

假设数据盘设备为 `/dev/sdb`：

```bash
# 格式化为 ext4（Longhorn 推荐）
sudo mkfs.ext4 /dev/sdb
sudo mkdir -p /var/lib/longhorn
UUID=$(sudo blkid -s UUID -o value /dev/sdb)
echo "UUID=${UUID} /var/lib/longhorn ext4 defaults,noatime 0 2" | sudo tee -a /etc/fstab
sudo mount -a

# 验证
df -h /var/lib/longhorn   # 应显示 ~100G
```

### 4.3 安装 containerd 1.7

```bash
sudo apt install -y apt-transport-https ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y containerd.io=1.7.*

# 生成默认配置并切到 systemd cgroup driver
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
# 国内节点可加镜像加速
sudo sed -i 's|sandbox_image = "registry.k8s.io/pause:3.8"|sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
sudo systemctl restart containerd
sudo systemctl enable containerd
```

### 4.4 安装 kubeadm / kubelet / kubectl v1.28.10

```bash
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.28/deb/Release.key | \
  sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.28/deb/ /' | \
  sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt update
sudo apt install -y kubelet=1.28.10-1.1 kubeadm=1.28.10-1.1 kubectl=1.28.10-1.1
sudo apt-mark hold kubelet kubeadm kubectl
```

### 4.5 验证

```bash
# 每台节点执行
systemctl status containerd kubelet --no-pager | head -20
kubeadm version
ctr version
swapon --show   # 应为空
```

---

## 5. kubeadm 集群初始化（IDC）

### 5.1 生成集群配置（在 node-01 上）

```bash
# 生成 join token 用的 certKey
CERT_KEY=$(kubeadm certs certificate-key)
echo "Cert key: $CERT_KEY"   # 记下，后面 join 用

cat <<EOF > /root/kubeadm-config.yaml
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: 192.168.10.11
  bindPort: 6443
certificateKey: "${CERT_KEY}"
nodeRegistration:
  name: node-01
  criSocket: unix:///run/containerd/containerd.sock
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v1.28.10
clusterName: idc-calendar
# 使用外部 VIP 当作 controlPlaneEndpoint（如果没有 VIP，也可以用 DNS 记录）
controlPlaneEndpoint: "k8s-api.idc.calendar.internal:6443"
networking:
  serviceSubnet: 10.96.0.0/12
  podSubnet: 10.244.0.0/16
  dnsDomain: cluster.local
apiServer:
  certSANs:
    - "k8s-api.idc.calendar.internal"
    - "node-01"
    - "node-02"
    - "node-03"
    - "192.168.10.11"
    - "192.168.10.12"
    - "192.168.10.13"
    - "127.0.0.1"
  extraArgs:
    audit-log-maxage: "30"
    audit-log-maxbackup: "10"
    audit-log-maxsize: "100"
    audit-log-path: "/var/log/kubernetes/audit.log"
controllerManager:
  extraArgs:
    bind-address: 0.0.0.0
scheduler:
  extraArgs:
    bind-address: 0.0.0.0
etcd:
  local:
    extraArgs:
      listen-metrics-urls: http://0.0.0.0:2381
imageRepository: registry.aliyuncs.com/google_containers   # 国内加速，可删
---
apiVersion: kubeproxy.config.k8s.io/v1alpha1
kind: KubeProxyConfiguration
mode: ipvs
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
maxPods: 110
systemReserved:
  cpu: "500m"
  memory: "512Mi"
kubeReserved:
  cpu: "500m"
  memory: "512Mi"
evictionHard:
  memory.available: "500Mi"
  nodefs.available: "10%"
EOF
```

> `controlPlaneEndpoint` 需要一个稳定入口。如果没有外部 LB/keepalived，先用 `/etc/hosts` 把 `k8s-api.idc.calendar.internal` 在所有节点都解析到 192.168.10.11（init 节点），将来要做真 HA 需接入 keepalived + HAProxy。

### 5.2 Init 第一个控制面（node-01）

```bash
sudo kubeadm init --config /root/kubeadm-config.yaml --upload-certs | tee /root/kubeadm-init.log

# 输出里会有两条 join 命令：
# (a) 其他 control-plane 加入（含 --certificate-key）
# (b) worker 加入
# 保存它们

mkdir -p $HOME/.kube
sudo cp /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config

# 去掉控制面 NoSchedule taint（核心决策：3 节点混合）
kubectl taint nodes node-01 node-role.kubernetes.io/control-plane:NoSchedule-

# 验证
kubectl get nodes   # node-01 NotReady（因为 CNI 还没装）
kubectl get pod -n kube-system
```

### 5.3 Join node-02 / node-03 作为 control-plane

在 node-02、node-03 上执行 init log 里的命令（示例，真实值按日志）：

```bash
# node-02
sudo kubeadm join k8s-api.idc.calendar.internal:6443 \
  --token <token-from-init> \
  --discovery-token-ca-cert-hash sha256:<hash> \
  --control-plane \
  --certificate-key <cert-key-from-init> \
  --apiserver-advertise-address 192.168.10.12

# 同理 node-03，替换 advertise-address 为 192.168.10.13
```

join 完成后，在任一节点：

```bash
# 去掉所有控制面 NoSchedule taint
kubectl taint nodes --all node-role.kubernetes.io/control-plane:NoSchedule- || true

# 标记节点角色（方便调度策略）
kubectl label node node-01 workload=platform,idc-rack=r1
kubectl label node node-02 workload=platform,idc-rack=r2
kubectl label node node-03 workload=platform,idc-rack=r3

kubectl get nodes -o wide
# 3 个 Ready（装完 CNI 后）状态为 control-plane,worker
```

### 5.4 /etc/hosts HA 兜底（所有节点）

在真正的 keepalived VIP 部署前，先用 `/etc/hosts` 让 `k8s-api.idc.calendar.internal` 轮询可用：

```bash
# 三节点各自 /etc/hosts 第一条指向自己，避免单点；或统一指向任一 apiserver
echo "192.168.10.11 k8s-api.idc.calendar.internal" | sudo tee -a /etc/hosts
```

### 5.5 keepalived VIP（可选但推荐）

为 apiserver 提供 192.168.10.10 VIP（避免 hosts 手动维护）：

```bash
sudo apt install -y keepalived haproxy

cat <<EOF | sudo tee /etc/keepalived/keepalived.conf
vrrp_script chk_apiserver {
  script "curl -sk https://127.0.0.1:6443/healthz -o /dev/null"
  interval 3
  weight -2
  fall 3
  rise 2
}
vrrp_instance VI_1 {
  state MASTER              # node-02/03 改为 BACKUP
  interface eth0            # 替换为实际网卡名
  virtual_router_id 51
  priority 100              # node-02=90, node-03=80
  advert_int 1
  authentication {
    auth_type PASS
    auth_pass k8sapi-2026
  }
  virtual_ipaddress {
    192.168.10.10
  }
  track_script {
    chk_apiserver
  }
}
EOF
sudo systemctl enable --now keepalived

# 然后把所有节点 /etc/hosts 里 k8s-api.idc.calendar.internal 改为 192.168.10.10
```

---

## 6. Calico CNI

```bash
# 下载 Tigera operator 和 custom-resources
curl -O https://raw.githubusercontent.com/projectcalico/calico/v3.27.2/manifests/tigera-operator.yaml
kubectl apply -f tigera-operator.yaml

cat <<EOF | kubectl apply -f -
apiVersion: operator.tigera.io/v1
kind: Installation
metadata:
  name: default
spec:
  calicoNetwork:
    bgp: Disabled
    ipPools:
    - blockSize: 26
      cidr: 10.244.0.0/16
      encapsulation: VXLAN
      natOutgoing: Enabled
      nodeSelector: all()
  registry: quay.io/
  nodeMetricsPort: 9091
  typhaMetricsPort: 9093
---
apiVersion: operator.tigera.io/v1
kind: APIServer
metadata:
  name: default
spec: {}
EOF

# 等待 Ready
kubectl get tigerastatus   # 全部 Available=True
kubectl get pod -n calico-system
kubectl get node   # 3 个 Ready
```

---

## 7. Longhorn 存储

### 7.1 前置：安装 open-iscsi

```bash
# 所有节点
sudo apt install -y open-iscsi nfs-common
sudo systemctl enable --now iscsid
```

### 7.2 安装 Longhorn

```bash
helm repo add longhorn https://charts.longhorn.io
helm repo update

cat <<EOF > longhorn-values.yaml
persistence:
  defaultClass: true
  defaultClassReplicaCount: 3
  reclaimPolicy: Delete
defaultSettings:
  defaultReplicaCount: 3
  defaultDataPath: /var/lib/longhorn
  replicaSoftAntiAffinity: false
  replicaZoneSoftAntiAffinity: false
  storageOverProvisioningPercentage: 100
  storageMinimalAvailablePercentage: 15
  backupTarget: s3://longhorn-backup@cn-hangzhou/
  backupTargetCredentialSecret: aliyun-oss-secret
longhornManager:
  tolerations:
  - operator: Exists
longhornDriver:
  tolerations:
  - operator: Exists
service:
  ui:
    type: ClusterIP
ingress:
  enabled: false   # 稍后通过 ingress-nginx 统一接入
EOF

kubectl create namespace longhorn-system
helm install longhorn longhorn/longhorn -n longhorn-system -f longhorn-values.yaml --version 1.6.1

# 验证
kubectl -n longhorn-system get pod
kubectl get storageclass
# longhorn 应是 default
```

### 7.3 创建低副本 StorageClass（给 PG/ES/MQ 用）

因为 PG/ES/MQ 自己做复制，Longhorn 再三副本就 9 份了，太浪费：

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: longhorn-single
provisioner: driver.longhorn.io
allowVolumeExpansion: true
reclaimPolicy: Delete
parameters:
  numberOfReplicas: "1"
  staleReplicaTimeout: "30"
  fromBackup: ""
  fsType: "ext4"
```

```bash
kubectl apply -f longhorn-single-sc.yaml
```

---

## 8. MetalLB + ingress-nginx

### 8.1 MetalLB

```bash
kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.14.3/config/manifests/metallb-native.yaml

# 等待 Ready
kubectl wait --namespace metallb-system --for=condition=ready pod --selector=app=metallb --timeout=300s

# 配置 IP 池（L2 模式）
cat <<EOF | kubectl apply -f -
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: idc-pool
  namespace: metallb-system
spec:
  addresses:
  - 192.168.10.100-192.168.10.120
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: idc-l2
  namespace: metallb-system
spec:
  ipAddressPools:
  - idc-pool
EOF
```

### 8.2 ingress-nginx

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

cat <<EOF > ingress-nginx-values.yaml
controller:
  replicaCount: 2
  service:
    type: LoadBalancer
    loadBalancerIP: 192.168.10.100
    annotations:
      metallb.universe.tf/allow-shared-ip: "idc-shared"
  metrics:
    enabled: true
    serviceMonitor:
      enabled: true
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi
  affinity:
    podAntiAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels:
            app.kubernetes.io/name: ingress-nginx
        topologyKey: kubernetes.io/hostname
  config:
    proxy-body-size: "50m"
    proxy-read-timeout: "300"
    proxy-send-timeout: "300"
    enable-real-ip: "true"
    use-forwarded-headers: "true"
EOF

helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  -f ingress-nginx-values.yaml \
  --version 4.10.0

# 验证
kubectl -n ingress-nginx get svc
# EXTERNAL-IP 应是 192.168.10.100
```

---

## 9. cert-manager

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml

# 等待 Ready
kubectl -n cert-manager wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager --timeout=300s

# 方案 1：内部环境用自签 CA
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: selfsigned-ca
spec:
  selfSigned: {}
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: calendar-ca
  namespace: cert-manager
spec:
  isCA: true
  commonName: calendar-internal-ca
  secretName: calendar-ca-secret
  privateKey:
    algorithm: ECDSA
    size: 256
  issuerRef:
    name: selfsigned-ca
    kind: ClusterIssuer
---
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: calendar-ca-issuer
spec:
  ca:
    secretName: calendar-ca-secret
EOF

# 方案 2：如果有公网域名，用 Let's Encrypt DNS01（阿里云 DNS）
# 参考官方 webhook：https://github.com/pmartin/cert-manager-alidns-webhook
```

---

## 10. Rancher Server 部署

### 10.1 安装 Rancher（单副本模式，部署到 node-01）

```bash
helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
helm repo update

kubectl create namespace cattle-system

cat <<EOF > rancher-values.yaml
hostname: rancher.cal.example.com
replicas: 1
bootstrapPassword: ChangeMe-Rancher-2026
ingress:
  tls:
    source: secret
    secretName: rancher-tls
  extraAnnotations:
    cert-manager.io/cluster-issuer: calendar-ca-issuer
resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    cpu: 2000m
    memory: 2Gi
nodeSelector:
  kubernetes.io/hostname: node-01
tolerations:
- operator: Exists
# 关闭 telemetry、audit log 量级调低
auditLog:
  level: 1
  maxAge: 7
EOF

helm install rancher rancher-stable/rancher \
  -n cattle-system \
  -f rancher-values.yaml \
  --version 2.8.3

# 验证
kubectl -n cattle-system rollout status deploy/rancher
```

### 10.2 访问与初始化

- 浏览器打开 `https://rancher.cal.example.com`（把 DNS 指到 192.168.10.100）
- 使用 `ChangeMe-Rancher-2026` 首次登录，立即改密
- 在 "Cluster Management" 里能看到 `local` 集群（即本 IDC 集群）

---

## 11. Harbor 部署

### 11.1 Helm values

```bash
kubectl create namespace harbor
helm repo add harbor https://helm.goharbor.io
helm repo update

cat <<EOF > harbor-values.yaml
expose:
  type: ingress
  tls:
    enabled: true
    certSource: secret
    secret:
      secretName: harbor-tls
  ingress:
    hosts:
      core: harbor.cal.example.com
    annotations:
      cert-manager.io/cluster-issuer: calendar-ca-issuer
      nginx.ingress.kubernetes.io/proxy-body-size: "0"

externalURL: https://harbor.cal.example.com
harborAdminPassword: Harbor12345-Change

persistence:
  enabled: true
  resourcePolicy: keep
  persistentVolumeClaim:
    registry:
      storageClass: longhorn
      size: 20Gi
    chartmuseum:
      storageClass: longhorn
      size: 2Gi
    jobservice:
      jobLog:
        storageClass: longhorn
        size: 1Gi
    database:
      storageClass: longhorn
      size: 3Gi
    redis:
      storageClass: longhorn
      size: 1Gi
    trivy:
      storageClass: longhorn
      size: 5Gi

portal:
  replicas: 1
core:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 1Gi }
jobservice:
  replicas: 1
registry:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 1Gi }

chartmuseum:
  enabled: true
trivy:
  enabled: true
  replicas: 1
notary:
  enabled: false  # 改用 Cosign

nodeSelector:
  kubernetes.io/hostname: node-02

metrics:
  enabled: true
  serviceMonitor:
    enabled: true
EOF

helm install harbor harbor/harbor -n harbor -f harbor-values.yaml --version 1.14.2

# 验证
kubectl -n harbor rollout status deploy/harbor-core
kubectl -n harbor rollout status deploy/harbor-portal
```

### 11.2 初始化项目与凭证

```bash
# CLI 登录
docker login harbor.cal.example.com -u admin -p "Harbor12345-Change"

# 创建项目：calendar、library-public
curl -u "admin:Harbor12345-Change" -X POST \
  https://harbor.cal.example.com/api/v2.0/projects \
  -H "Content-Type: application/json" \
  -d '{"project_name":"calendar","metadata":{"public":"false"}}'
```

### 11.3 Helm 仓库使用

```bash
helm repo add calendar https://harbor.cal.example.com/chartrepo/calendar \
  --username admin --password 'Harbor12345-Change'
```

### 11.4 定期 GC（避免镜像盘撑爆）

在 Harbor UI → Garbage Collection → Schedule: `0 0 3 * * 0`（每周日凌晨 3 点）

---

## 12. Gitea 部署

```bash
helm repo add gitea-charts https://dl.gitea.com/charts/
kubectl create namespace gitea

cat <<EOF > gitea-values.yaml
replicaCount: 1
persistence:
  enabled: true
  size: 10Gi
  storageClass: longhorn
postgresql-ha:
  enabled: false
postgresql:
  enabled: true
  primary:
    persistence:
      storageClass: longhorn
      size: 5Gi
redis-cluster:
  enabled: false
redis:
  enabled: true
gitea:
  admin:
    username: gitea_admin
    password: Gitea-Change-2026
    email: admin@cal.example.com
  config:
    server:
      DOMAIN: git.cal.example.com
      ROOT_URL: https://git.cal.example.com
      SSH_DOMAIN: git.cal.example.com
    service:
      DISABLE_REGISTRATION: true
    webhook:
      ALLOWED_HOST_LIST: "*.cal.example.com,argocd-server.argocd.svc"
ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: calendar-ca-issuer
  hosts:
  - host: git.cal.example.com
    paths:
    - path: /
      pathType: Prefix
  tls:
  - secretName: gitea-tls
    hosts:
    - git.cal.example.com
resources:
  requests: { cpu: 100m, memory: 256Mi }
  limits:   { cpu: 1000m, memory: 768Mi }
nodeSelector:
  kubernetes.io/hostname: node-02
EOF

helm install gitea gitea-charts/gitea -n gitea -f gitea-values.yaml --version 10.1.0
```

---

## 13. ArgoCD 部署

```bash
helm repo add argo https://argoproj.github.io/argo-helm
kubectl create namespace argocd

cat <<EOF > argocd-values.yaml
global:
  domain: argocd.cal.example.com
configs:
  params:
    server.insecure: true   # 由 ingress-nginx 处理 TLS
  cm:
    application.instanceLabelKey: argocd.argoproj.io/instance
    timeout.reconciliation: 180s
    kustomize.buildOptions: --enable-helm
    url: https://argocd.cal.example.com
    resource.customizations.ignoreDifferences.apps_Deployment: |
      jsonPointers:
      - /spec/replicas
server:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 512Mi }
  ingress:
    enabled: true
    controller: generic
    ingressClassName: nginx
    hostname: argocd.cal.example.com
    annotations:
      cert-manager.io/cluster-issuer: calendar-ca-issuer
      nginx.ingress.kubernetes.io/ssl-passthrough: "false"
      nginx.ingress.kubernetes.io/backend-protocol: "HTTP"
    tls: true
  nodeSelector:
    kubernetes.io/hostname: node-03
repoServer:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 500m,  memory: 512Mi }
  nodeSelector:
    kubernetes.io/hostname: node-03
applicationSet:
  replicas: 1
controller:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 1Gi }
  nodeSelector:
    kubernetes.io/hostname: node-03
redis:
  enabled: true
dex:
  enabled: false
notifications:
  enabled: false
EOF

helm install argocd argo/argo-cd -n argocd -f argocd-values.yaml --version 6.7.10

# 获取初始 admin 密码
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### 13.1 安装 Argo Rollouts（灰度/金丝雀）

```bash
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts \
  -f https://github.com/argoproj/argo-rollouts/releases/download/v1.7.1/install.yaml
```

---

## 14. GitLab Runner（Kaniko executor on Spot）

> **关键决策**：Runner Manager 跑在 IDC node-03，**所有 build job 通过 Kubernetes Autoscaler 在阿里云 Spot 上动态启 Pod**。

### 14.1 先做 Gitea 侧 Runner 注册（或直接用 GitLab Runner 对接 Gitea Actions）

Gitea 1.21 支持原生 Actions（兼容 GitHub Actions 语法）。我们用 **act_runner**：

```bash
# 在 Gitea UI → Site Administration → Actions → Runners 复制 REGISTRATION_TOKEN

kubectl create namespace gitea-runner
kubectl create secret generic act-runner-token \
  -n gitea-runner \
  --from-literal=token=<REGISTRATION_TOKEN>

cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: act-runner
  namespace: gitea-runner
spec:
  replicas: 1
  selector:
    matchLabels: { app: act-runner }
  template:
    metadata:
      labels: { app: act-runner }
    spec:
      nodeSelector:
        kubernetes.io/hostname: node-03
      containers:
      - name: runner
        image: gitea/act_runner:0.2.10
        env:
        - name: GITEA_INSTANCE_URL
          value: https://git.cal.example.com
        - name: GITEA_RUNNER_REGISTRATION_TOKEN
          valueFrom:
            secretKeyRef: { name: act-runner-token, key: token }
        - name: GITEA_RUNNER_LABELS
          value: "idc-ctl:host,cloud-spot:docker://gitea/runner-images:ubuntu-22.04"
        - name: CONFIG_FILE
          value: /etc/runner/config.yaml
        volumeMounts:
        - name: config
          mountPath: /etc/runner
        - name: data
          mountPath: /data
        resources:
          requests: { cpu: 100m, memory: 256Mi }
          limits:   { cpu: 500m, memory: 512Mi }
      volumes:
      - name: config
        configMap: { name: act-runner-config }
      - name: data
        emptyDir: {}
EOF
```

### 14.2 Kaniko build Pod 模板（在云端 Spot 执行）

工作流 yaml 里指定 `runs-on: cloud-spot`，让任务在云端节点池跑。云端节点有污点 `cloud-only=true:NoSchedule`，build Pod 容忍即可。

Kaniko build 任务 workflow 示例见 §25。

---

## 15. 可观测栈：Prometheus/Grafana/Loki/Tempo

### 15.1 kube-prometheus-stack

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
kubectl create namespace observability

cat <<EOF > kps-values.yaml
prometheus:
  prometheusSpec:
    retention: 7d
    retentionSize: "12GB"
    resources:
      requests: { cpu: 300m, memory: 1Gi }
      limits:   { cpu: 2000m, memory: 3Gi }
    storageSpec:
      volumeClaimTemplate:
        spec:
          storageClassName: longhorn-single
          accessModes: ["ReadWriteOnce"]
          resources:
            requests:
              storage: 15Gi
    remoteWrite:
    - url: https://prom-cloud.oss-cn-hangzhou.internal/api/v1/write
      # 长期存储推到云端（可选）
    nodeSelector:
      kubernetes.io/hostname: node-03
alertmanager:
  alertmanagerSpec:
    resources:
      requests: { cpu: 50m, memory: 64Mi }
      limits:   { cpu: 200m, memory: 256Mi }
grafana:
  adminPassword: Grafana-Change-2026
  persistence:
    enabled: true
    size: 5Gi
    storageClassName: longhorn-single
  ingress:
    enabled: true
    ingressClassName: nginx
    annotations:
      cert-manager.io/cluster-issuer: calendar-ca-issuer
    hosts: ["grafana.cal.example.com"]
    tls:
    - secretName: grafana-tls
      hosts: ["grafana.cal.example.com"]
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 500m, memory: 512Mi }
  additionalDataSources:
  - name: Loki
    type: loki
    url: http://loki-gateway.observability.svc:80
  - name: Tempo
    type: tempo
    url: http://tempo.observability.svc:3100
nodeExporter:
  enabled: true
kubeStateMetrics:
  enabled: true
EOF

helm install kps prometheus-community/kube-prometheus-stack \
  -n observability -f kps-values.yaml --version 57.2.0
```

### 15.2 Loki（SingleBinary 模式，节省资源）

```bash
helm repo add grafana https://grafana.github.io/helm-charts

cat <<EOF > loki-values.yaml
deploymentMode: SingleBinary
loki:
  auth_enabled: false
  commonConfig:
    replication_factor: 1
  storage:
    type: filesystem
  schemaConfig:
    configs:
    - from: "2024-01-01"
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h
singleBinary:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 1Gi }
  persistence:
    enabled: true
    size: 10Gi
    storageClass: longhorn-single
  nodeSelector:
    kubernetes.io/hostname: node-03
gateway:
  enabled: true
  replicas: 1
monitoring:
  selfMonitoring: { enabled: false }
  lokiCanary: { enabled: false }
test:
  enabled: false
EOF

helm install loki grafana/loki -n observability -f loki-values.yaml --version 5.47.2
```

### 15.3 Promtail

```bash
cat <<EOF > promtail-values.yaml
config:
  clients:
  - url: http://loki-gateway.observability.svc:80/loki/api/v1/push
resources:
  requests: { cpu: 50m, memory: 64Mi }
  limits:   { cpu: 200m, memory: 256Mi }
EOF

helm install promtail grafana/promtail -n observability -f promtail-values.yaml
```

### 15.4 Tempo（轻量 SingleBinary）

```bash
cat <<EOF > tempo-values.yaml
tempo:
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 500m, memory: 512Mi }
persistence:
  enabled: true
  size: 5Gi
  storageClassName: longhorn-single
EOF

helm install tempo grafana/tempo -n observability -f tempo-values.yaml --version 1.9.0
```

---

## 16. CloudNativePG（PostgreSQL HA）

### 16.1 安装 Operator

```bash
kubectl apply --server-side -f \
  https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.22/releases/cnpg-1.22.0.yaml

kubectl -n cnpg-system rollout status deploy/cnpg-controller-manager
```

### 16.2 创建 PG 集群（1 主 2 备）

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: pg-calendar
  namespace: calendar-data
spec:
  instances: 3
  imageName: ghcr.io/cloudnative-pg/postgresql:16.2
  postgresql:
    parameters:
      max_connections: "200"
      shared_buffers: "1GB"
      effective_cache_size: "3GB"
      work_mem: "16MB"
      maintenance_work_mem: "128MB"
      wal_buffers: "16MB"
      min_wal_size: "512MB"
      max_wal_size: "2GB"
      checkpoint_completion_target: "0.9"
      log_min_duration_statement: "200"
      log_statement: "ddl"
      timezone: "UTC"
      wal_level: "logical"
      max_replication_slots: "10"
      max_wal_senders: "10"
  bootstrap:
    initdb:
      database: calendar
      owner: calendar
      secret:
        name: pg-calendar-app-cred
      postInitSQL:
      - "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
      - "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
  storage:
    storageClass: longhorn-single
    size: 15Gi
  walStorage:
    storageClass: longhorn-single
    size: 5Gi
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: 2000m, memory: 2Gi }
  affinity:
    enablePodAntiAffinity: true
    topologyKey: kubernetes.io/hostname
    podAntiAffinityType: required
  monitoring:
    enablePodMonitor: true
  backup:
    retentionPolicy: "30d"
    barmanObjectStore:
      destinationPath: s3://pg-backup/calendar
      endpointURL: https://oss-cn-hangzhou.aliyuncs.com
      s3Credentials:
        accessKeyId:
          name: pg-backup-cred
          key: ACCESS_KEY_ID
        secretAccessKey:
          name: pg-backup-cred
          key: ACCESS_KEY_SECRET
      wal:
        compression: gzip
      data:
        compression: gzip
        immediateCheckpoint: true
```

### 16.3 应用密钥

```bash
kubectl create namespace calendar-data

kubectl create secret generic pg-calendar-app-cred \
  -n calendar-data \
  --from-literal=username=calendar \
  --from-literal=password=Calendar-App-Pass-2026

kubectl create secret generic pg-backup-cred \
  -n calendar-data \
  --from-literal=ACCESS_KEY_ID=xxx \
  --from-literal=ACCESS_KEY_SECRET=xxx

kubectl apply -f pg-calendar.yaml
kubectl -n calendar-data get cluster pg-calendar -w
```

### 16.4 定期备份 ScheduledBackup

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: ScheduledBackup
metadata:
  name: pg-calendar-daily
  namespace: calendar-data
spec:
  schedule: "0 0 2 * * *"
  backupOwnerReference: self
  cluster:
    name: pg-calendar
```

### 16.5 给业务的 Service 名

- 主：`pg-calendar-rw.calendar-data.svc:5432`
- 只读：`pg-calendar-ro.calendar-data.svc:5432`
- 任一副本：`pg-calendar-r.calendar-data.svc:5432`

---

## 17. Redis Sentinel

使用 Spotahome Redis Operator（社区最活跃）：

```bash
kubectl create namespace redis-system
helm repo add redis-operator https://spotahome.github.io/redis-operator
helm install redis-operator redis-operator/redis-operator -n redis-system --version 3.2.13
```

创建 Redis 实例：

```yaml
apiVersion: databases.spotahome.com/v1
kind: RedisFailover
metadata:
  name: redis-calendar
  namespace: calendar-data
spec:
  sentinel:
    replicas: 3
    resources:
      requests: { cpu: 50m, memory: 64Mi }
      limits:   { cpu: 200m, memory: 128Mi }
  redis:
    replicas: 3
    image: redis:7.2.4-alpine
    resources:
      requests: { cpu: 100m, memory: 256Mi }
      limits:   { cpu: 500m, memory: 512Mi }
    customConfig:
    - "maxmemory 384mb"
    - "maxmemory-policy allkeys-lru"
    - "appendonly yes"
    - "save 900 1"
    storage:
      persistentVolumeClaim:
        metadata:
          name: redis-data
        spec:
          storageClassName: longhorn-single
          accessModes: [ReadWriteOnce]
          resources:
            requests:
              storage: 5Gi
    affinity:
      podAntiAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
        - labelSelector:
            matchLabels:
              app.kubernetes.io/component: redis
              redisfailovers.databases.spotahome.com/name: redis-calendar
          topologyKey: kubernetes.io/hostname
```

访问端点：`rfs-redis-calendar.calendar-data.svc:26379`（Sentinel）/ `rfr-redis-calendar.calendar-data.svc:6379`（当前 master 转发）

---

## 18. Nacos 集群

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nacos-cm
  namespace: calendar-data
data:
  MYSQL_SERVICE_HOST: "pg-calendar-rw.calendar-data.svc"
  # Nacos 2.3 支持 PG（社区分支 nacos-postgresql），或仍使用 derby/内置
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nacos
  namespace: calendar-data
spec:
  serviceName: nacos-headless
  replicas: 3
  selector:
    matchLabels: { app: nacos }
  template:
    metadata:
      labels: { app: nacos }
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchLabels: { app: nacos }
            topologyKey: kubernetes.io/hostname
      containers:
      - name: nacos
        image: nacos/nacos-server:v2.3.1
        ports:
        - containerPort: 8848
          name: http
        - containerPort: 9848
          name: grpc
        env:
        - name: NACOS_SERVERS
          value: "nacos-0.nacos-headless.calendar-data.svc:8848 nacos-1.nacos-headless.calendar-data.svc:8848 nacos-2.nacos-headless.calendar-data.svc:8848"
        - name: MODE
          value: cluster
        - name: JVM_XMS
          value: 768m
        - name: JVM_XMX
          value: 1g
        - name: JVM_XMN
          value: 256m
        resources:
          requests: { cpu: 200m, memory: 768Mi }
          limits:   { cpu: 1000m, memory: 1.5Gi }
        readinessProbe:
          httpGet: { path: /nacos/v1/console/health/readiness, port: 8848 }
          initialDelaySeconds: 30
          periodSeconds: 10
        volumeMounts:
        - name: data
          mountPath: /home/nacos/data
        - name: logs
          mountPath: /home/nacos/logs
  volumeClaimTemplates:
  - metadata: { name: data }
    spec:
      storageClassName: longhorn-single
      accessModes: [ReadWriteOnce]
      resources: { requests: { storage: 3Gi } }
  - metadata: { name: logs }
    spec:
      storageClassName: longhorn-single
      accessModes: [ReadWriteOnce]
      resources: { requests: { storage: 2Gi } }
---
apiVersion: v1
kind: Service
metadata:
  name: nacos-headless
  namespace: calendar-data
spec:
  clusterIP: None
  selector: { app: nacos }
  ports:
  - { name: http, port: 8848 }
  - { name: grpc, port: 9848 }
---
apiVersion: v1
kind: Service
metadata:
  name: nacos
  namespace: calendar-data
spec:
  selector: { app: nacos }
  ports:
  - { name: http, port: 8848 }
  - { name: grpc, port: 9848 }
```

业务接入：`spring.cloud.nacos.discovery.server-addr=nacos.calendar-data.svc:8848`

---

## 19. RocketMQ 集群

使用 RocketMQ Operator：

```bash
kubectl apply -f https://github.com/apache/rocketmq-operator/releases/download/v1.0.3/crds.yaml
kubectl create namespace rocketmq-system
kubectl apply -f https://github.com/apache/rocketmq-operator/releases/download/v1.0.3/operator.yaml
```

创建集群：

```yaml
apiVersion: rocketmq.apache.org/v1alpha1
kind: NameService
metadata:
  name: name-service
  namespace: calendar-data
spec:
  size: 1                       # 1 个 NameServer 节省资源
  nameServerImage: apache/rocketmq:5.1.4
  imagePullPolicy: IfNotPresent
  hostNetwork: false
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 500m, memory: 512Mi }
  storageMode: EmptyDir
---
apiVersion: rocketmq.apache.org/v1alpha1
kind: Broker
metadata:
  name: broker
  namespace: calendar-data
spec:
  size: 3                       # 3 主 0 从（依赖 raft 或单纯主复制）
  nameServers: "name-service-0.name-service-service.calendar-data.svc:9876"
  replicationMode: ASYNC
  replicaPerGroup: 1
  brokerImage: apache/rocketmq:5.1.4
  allocateNodeAntiAffinity: true
  resources:
    requests: { cpu: 300m, memory: 1Gi }
    limits:   { cpu: 1500m, memory: 1.5Gi }
  storageMode: StorageClass
  storageClassName: longhorn-single
  volumeClaimTemplates:
  - metadata: { name: broker-storage }
    spec:
      accessModes: [ReadWriteOnce]
      storageClassName: longhorn-single
      resources: { requests: { storage: 5Gi } }
  env:
  - name: BROKER_MEM
    value: "-Xms1g -Xmx1g -Xmn512m"
```

业务接入：`rocketmq.name-server-addr=name-service-service.calendar-data.svc:9876`

---

## 20. Elasticsearch（ECK）

```bash
# 安装 ECK Operator
kubectl create -f https://download.elastic.co/downloads/eck/2.12.1/crds.yaml
kubectl apply -f https://download.elastic.co/downloads/eck/2.12.1/operator.yaml

# ES 集群
cat <<EOF | kubectl apply -f -
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: es-calendar
  namespace: calendar-data
spec:
  version: 8.12.2
  nodeSets:
  - name: default
    count: 3
    config:
      node.roles: ["master", "data", "ingest"]
      xpack.security.enabled: true
      xpack.security.transport.ssl.enabled: true
    podTemplate:
      spec:
        affinity:
          podAntiAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  elasticsearch.k8s.elastic.co/cluster-name: es-calendar
              topologyKey: kubernetes.io/hostname
        containers:
        - name: elasticsearch
          env:
          - name: ES_JAVA_OPTS
            value: "-Xms1g -Xmx1g"
          resources:
            requests: { cpu: 300m, memory: 1.5Gi }
            limits:   { cpu: 1500m, memory: 2Gi }
    volumeClaimTemplates:
    - metadata: { name: elasticsearch-data }
      spec:
        accessModes: [ReadWriteOnce]
        storageClassName: longhorn-single
        resources:
          requests: { storage: 8Gi }
EOF
```

获取密码：

```bash
kubectl -n calendar-data get secret es-calendar-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d
```

---

## 21. 环境隔离：dev vs stg

同一集群用 namespace 区分，配合 ResourceQuota + NetworkPolicy：

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: calendar-dev
  labels:
    env: dev
    pod-security.kubernetes.io/enforce: baseline
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: quota-dev
  namespace: calendar-dev
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 12Gi
    pods: "30"
---
apiVersion: v1
kind: LimitRange
metadata:
  name: default-limits-dev
  namespace: calendar-dev
spec:
  limits:
  - default:
      cpu: 500m
      memory: 512Mi
    defaultRequest:
      cpu: 100m
      memory: 128Mi
    type: Container
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-cross-env
  namespace: calendar-dev
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
  ingress:
  - from:
    - namespaceSelector:
        matchLabels: { env: dev }
    - namespaceSelector:
        matchLabels: { kubernetes.io/metadata.name: ingress-nginx }
    - namespaceSelector:
        matchLabels: { kubernetes.io/metadata.name: observability }
  egress:
  - to:
    - namespaceSelector:
        matchLabels: { env: dev }
    - namespaceSelector:
        matchLabels: { kubernetes.io/metadata.name: calendar-data }
    - namespaceSelector:
        matchLabels: { kubernetes.io/metadata.name: kube-system }
```

同理创建 `calendar-stg` namespace（quota 加倍）。

---

## 22. 阿里云突发集群（Terraform + kubeadm）

### 22.1 Terraform 配置（`cloud-cluster/main.tf`）

```hcl
terraform {
  required_providers {
    alicloud = {
      source  = "aliyun/alicloud"
      version = "~> 1.220.0"
    }
  }
  backend "oss" {
    bucket = "tfstate-calendar"
    prefix = "cloud-cluster"
    region = "cn-hangzhou"
  }
}

provider "alicloud" {
  region = "cn-hangzhou"
}

resource "alicloud_vpc" "main" {
  vpc_name   = "calendar-burst"
  cidr_block = "172.16.0.0/16"
}

resource "alicloud_vswitch" "h" {
  vpc_id     = alicloud_vpc.main.id
  cidr_block = "172.16.1.0/24"
  zone_id    = "cn-hangzhou-h"
  vswitch_name = "calendar-burst-h"
}

resource "alicloud_security_group" "k8s" {
  name   = "calendar-burst-sg"
  vpc_id = alicloud_vpc.main.id
}

resource "alicloud_security_group_rule" "k8s_internal" {
  type              = "ingress"
  ip_protocol       = "all"
  policy            = "accept"
  port_range        = "-1/-1"
  security_group_id = alicloud_security_group.k8s.id
  cidr_ip           = "172.16.0.0/16"
}

resource "alicloud_security_group_rule" "wg" {
  type              = "ingress"
  ip_protocol       = "udp"
  policy            = "accept"
  port_range        = "51820/51820"
  security_group_id = alicloud_security_group.k8s.id
  cidr_ip           = "0.0.0.0/0"
}

# 固定的 master 节点 (按量付费，不用 Spot)
resource "alicloud_instance" "cloud_master" {
  instance_name     = "cloud-m1"
  instance_type     = "ecs.g7.large"         # 2C4G
  image_id          = data.alicloud_images.ubuntu.images[0].id
  vswitch_id        = alicloud_vswitch.h.id
  security_groups   = [alicloud_security_group.k8s.id]
  system_disk_category = "cloud_essd"
  system_disk_size     = 60
  internet_max_bandwidth_out = 5
  user_data = base64encode(file("${path.module}/init-master.sh"))
  tags = { role = "k8s-master", cluster = "calendar-burst" }
}

# Spot worker (数量动态，通过 scaling-group 或 ASK 自动伸缩)
resource "alicloud_ess_scaling_group" "worker" {
  scaling_group_name = "calendar-burst-worker"
  min_size           = 0
  max_size           = 6
  default_cooldown   = 60
  vswitch_ids        = [alicloud_vswitch.h.id]
  removal_policies   = ["NewestInstance"]
  multi_az_policy    = "PRIORITY"
}

resource "alicloud_ess_scaling_configuration" "worker_cfg" {
  scaling_group_id  = alicloud_ess_scaling_group.worker.id
  image_id          = data.alicloud_images.ubuntu.images[0].id
  instance_type     = "ecs.c7.xlarge"        # 4C8G
  security_group_id = alicloud_security_group.k8s.id
  spot_strategy     = "SpotWithPriceLimit"
  spot_price_limit {
    instance_type = "ecs.c7.xlarge"
    price_limit   = 0.3
  }
  system_disk_category = "cloud_essd"
  system_disk_size     = 80
  enable            = true
  active            = true
  user_data         = base64encode(file("${path.module}/init-worker.sh"))
  tags              = { role = "k8s-worker", cluster = "calendar-burst" }
}

data "alicloud_images" "ubuntu" {
  owners     = "system"
  name_regex = "^ubuntu_22_04_x64"
}

output "master_public_ip" { value = alicloud_instance.cloud_master.public_ip }
output "master_private_ip" { value = alicloud_instance.cloud_master.private_ip }
```

### 22.2 init-master.sh（cloud-init 脚本）

```bash
#!/bin/bash
set -e

# 参照 §4 的步骤
# 1. 关 swap
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

# 2. 内核模块/sysctl（略，同 §4.1）

# 3. 安装 containerd + kubeadm 1.28.10（同 §4.3 §4.4）

# 4. kubeadm init
cat <<EOF > /root/kubeadm-cloud.yaml
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: __PRIVATE_IP__
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v1.28.10
clusterName: calendar-burst
networking:
  podSubnet: 10.245.0.0/16
  serviceSubnet: 10.97.0.0/12
imageRepository: registry.aliyuncs.com/google_containers
EOF

PRIVATE_IP=$(hostname -I | awk '{print $1}')
sed -i "s/__PRIVATE_IP__/$PRIVATE_IP/" /root/kubeadm-cloud.yaml
kubeadm init --config /root/kubeadm-cloud.yaml | tee /root/init.log

# 上传 join token 到 OSS，供 worker 拉取
aws s3 cp /root/init.log s3://tfstate-calendar/cloud-cluster/init.log \
  --endpoint https://oss-cn-hangzhou.aliyuncs.com
```

### 22.3 init-worker.sh

```bash
#!/bin/bash
set -e
# 同样安装 containerd+kubeadm（略）

# 从 OSS 拉 join 命令
aws s3 cp s3://tfstate-calendar/cloud-cluster/init.log /root/init.log \
  --endpoint https://oss-cn-hangzhou.aliyuncs.com
JOIN_CMD=$(grep 'kubeadm join' /root/init.log | grep -v control-plane | head -1 -A2 | tr '\n' ' ' | sed 's/\\ //g')
$JOIN_CMD

# 给 worker 打云端标签和污点
kubectl label node $(hostname) cloud=aliyun-spot cost=cheap
kubectl taint node $(hostname) cloud-only=true:NoSchedule
```

### 22.4 按需启停

```bash
# 拉起 3 个 worker
aliyun ess SetScalingGroupActive --ScalingGroupId <id>
aliyun ess ModifyScalingGroup --ScalingGroupId <id> --MinSize 3

# 缩回 0（夜间/空闲）
aliyun ess ModifyScalingGroup --ScalingGroupId <id> --MinSize 0
```

---

## 23. Rancher 纳管云端集群

1. Rancher UI → Cluster Management → Import Existing → Generic
2. 集群名 `calendar-burst`，取 `kubectl apply` 命令
3. 在云端 cloud-m1 上执行该命令
4. 等待 Rancher 显示 Active
5. 在 Cluster → Tools → Fleet 可统一管理两套集群

---

## 24. WireGuard 跨网组网

### 24.1 在 IDC 网关（独立主机或 node-01）上装 WG

```bash
sudo apt install -y wireguard
wg genkey | tee /etc/wireguard/privatekey | wg pubkey > /etc/wireguard/publickey
IDC_PRIV=$(cat /etc/wireguard/privatekey)

cat <<EOF | sudo tee /etc/wireguard/wg0.conf
[Interface]
Address = 10.200.0.1/24
ListenPort = 51820
PrivateKey = $IDC_PRIV
PostUp   = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

[Peer]
PublicKey = <CLOUD_PUBLIC_KEY>
AllowedIPs = 10.200.0.2/32, 172.16.0.0/16, 10.245.0.0/16, 10.97.0.0/12
Endpoint = <CLOUD_PUBLIC_IP>:51820
PersistentKeepalive = 25
EOF

sudo systemctl enable --now wg-quick@wg0
```

### 24.2 在云端 cloud-m1 上

```bash
# 对称配置
cat <<EOF | sudo tee /etc/wireguard/wg0.conf
[Interface]
Address = 10.200.0.2/24
ListenPort = 51820
PrivateKey = $CLOUD_PRIV
PostUp   = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

[Peer]
PublicKey = <IDC_PUBLIC_KEY>
AllowedIPs = 10.200.0.1/32, 192.168.10.0/24, 10.244.0.0/16, 10.96.0.0/12
Endpoint = <IDC_PUBLIC_IP>:51820
PersistentKeepalive = 25
EOF
```

### 24.3 跨集群 DNS

在 IDC CoreDNS 加 forward 条目：

```bash
kubectl -n kube-system edit cm coredns
```

```
.:53 {
    ...
    forward cloud.local 10.97.0.10   # 云端 CoreDNS ClusterIP
    ...
}
```

云端 CoreDNS 反向 forward `idc.local → 10.96.0.10`。

---

## 25. CI/CD 流水线

### 25.1 Gitea Actions Workflow（`.gitea/workflows/ci.yaml`）

```yaml
name: ci-calendar
on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  test:
    runs-on: idc-ctl
    container:
      image: eclipse-temurin:21-jdk
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: calendar_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
    steps:
    - uses: actions/checkout@v4
    - name: Cache Gradle
      uses: actions/cache@v3
      with:
        path: ~/.gradle/caches
        key: gradle-${{ hashFiles('**/*.gradle') }}
    - name: Test
      env:
        SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/calendar_test
        SPRING_DATASOURCE_USERNAME: test
        SPRING_DATASOURCE_PASSWORD: test
      run: ./gradlew clean test -Pprofile=ci

  build-image:
    runs-on: cloud-spot             # 在阿里云 Spot 节点跑
    needs: test
    strategy:
      matrix:
        service: [calendar-core, reminder, user-setting, notification]
    steps:
    - uses: actions/checkout@v4
    - name: Set up version
      run: echo "VERSION=$(date +%Y%m%d)-${GITHUB_SHA::8}" >> $GITHUB_ENV
    - name: Kaniko build & push
      uses: docker://gcr.io/kaniko-project/executor:v1.22.0
      with:
        args: |
          --context=./services/${{ matrix.service }}
          --dockerfile=./services/${{ matrix.service }}/Dockerfile
          --destination=harbor.cal.example.com/calendar/${{ matrix.service }}:${{ env.VERSION }}
          --destination=harbor.cal.example.com/calendar/${{ matrix.service }}:latest
          --cache=true
          --cache-repo=harbor.cal.example.com/calendar/cache
          --snapshot-mode=redo

  scan:
    runs-on: cloud-spot
    needs: build-image
    strategy:
      matrix:
        service: [calendar-core, reminder, user-setting, notification]
    steps:
    - name: Trivy scan
      run: |
        trivy image --severity HIGH,CRITICAL --exit-code 1 \
          harbor.cal.example.com/calendar/${{ matrix.service }}:${{ env.VERSION }}

  sign:
    runs-on: cloud-spot
    needs: scan
    steps:
    - name: Cosign keyless sign
      env:
        COSIGN_EXPERIMENTAL: "1"
      run: |
        cosign sign --yes \
          harbor.cal.example.com/calendar/calendar-core:${{ env.VERSION }}

  deploy-dev:
    runs-on: idc-ctl
    needs: sign
    if: github.ref == 'refs/heads/develop'
    steps:
    - name: Bump Helm values in gitops repo
      run: |
        git clone https://git.cal.example.com/calendar/gitops.git
        cd gitops
        yq e -i ".calendarCore.image.tag = \"${{ env.VERSION }}\"" dev/values.yaml
        git commit -am "chore(dev): bump to ${{ env.VERSION }}"
        git push

  deploy-stg:
    runs-on: idc-ctl
    needs: sign
    if: github.ref == 'refs/heads/main'
    steps:
    - name: Bump Helm values in gitops repo (stg)
      run: |
        git clone https://git.cal.example.com/calendar/gitops.git
        cd gitops
        yq e -i ".calendarCore.image.tag = \"${{ env.VERSION }}\"" stg/values.yaml
        git commit -am "chore(stg): bump to ${{ env.VERSION }}"
        git push
```

### 25.2 Umbrella Chart 目录结构（gitops 仓库）

```
gitops/
├── charts/
│   └── calendar-umbrella/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
├── dev/
│   ├── values.yaml
│   └── Chart.yaml
├── stg/
│   ├── values.yaml
│   └── Chart.yaml
└── applicationset.yaml
```

`charts/calendar-umbrella/Chart.yaml`：

```yaml
apiVersion: v2
name: calendar-umbrella
version: 0.1.0
dependencies:
- name: calendar-core
  version: "0.1.x"
  repository: "https://harbor.cal.example.com/chartrepo/calendar"
- name: reminder
  version: "0.1.x"
  repository: "https://harbor.cal.example.com/chartrepo/calendar"
- name: user-setting
  version: "0.1.x"
  repository: "https://harbor.cal.example.com/chartrepo/calendar"
- name: notification
  version: "0.1.x"
  repository: "https://harbor.cal.example.com/chartrepo/calendar"
```

---

## 26. ArgoCD ApplicationSet 跨集群分发

### 26.1 注册集群到 ArgoCD

```bash
# 导入 kubeconfig 后
argocd cluster add calendar-burst-ctx --name calendar-burst --server https://argocd.cal.example.com

argocd cluster list
# NAME              SERVER                              STATUS
# in-cluster        https://kubernetes.default.svc      Successful
# calendar-burst    https://<cloud-m1-private>:6443     Successful
```

### 26.2 ApplicationSet（Matrix generator）

```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: calendar-all-envs
  namespace: argocd
spec:
  goTemplate: true
  generators:
  - matrix:
      generators:
      - list:
          elements:
          - env: dev
            cluster: in-cluster
            namespace: calendar-dev
            replicas: "1"
          - env: stg
            cluster: in-cluster
            namespace: calendar-stg
            replicas: "2"
          - env: loadtest
            cluster: calendar-burst
            namespace: calendar-loadtest
            replicas: "3"
      - git:
          repoURL: https://git.cal.example.com/calendar/gitops.git
          revision: HEAD
          directories:
          - path: charts/calendar-umbrella
  template:
    metadata:
      name: 'calendar-{{.env}}'
    spec:
      project: default
      source:
        repoURL: https://git.cal.example.com/calendar/gitops.git
        targetRevision: HEAD
        path: '{{.env}}'
        helm:
          valueFiles: ["values.yaml"]
          parameters:
          - name: global.replicaCount
            value: '{{.replicas}}'
      destination:
        name: '{{.cluster}}'
        namespace: '{{.namespace}}'
      syncPolicy:
        automated:
          prune: true
          selfHeal: true
        syncOptions:
        - CreateNamespace=true
        - ServerSideApply=true
```

### 26.3 灰度发布（Argo Rollouts）

`charts/calendar-core/templates/rollout.yaml`：

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: calendar-core
spec:
  replicas: {{ .Values.replicaCount }}
  strategy:
    canary:
      canaryService: calendar-core-canary
      stableService: calendar-core-stable
      trafficRouting:
        nginx:
          stableIngress: calendar-core-ingress
      steps:
      - setWeight: 10
      - pause: { duration: 2m }
      - analysis:
          templates:
          - templateName: success-rate
      - setWeight: 50
      - pause: { duration: 5m }
      - setWeight: 100
  selector:
    matchLabels: { app: calendar-core }
  template: { ... }
---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  metrics:
  - name: success-rate
    interval: 30s
    successCondition: result[0] >= 0.98
    failureLimit: 3
    provider:
      prometheus:
        address: http://kps-prometheus.observability.svc:9090
        query: |
          sum(rate(http_server_requests_seconds_count{
            service="calendar-core-canary", status!~"5.."
          }[2m])) /
          sum(rate(http_server_requests_seconds_count{
            service="calendar-core-canary"
          }[2m]))
```

---

## 27. 备份与灾备

### 27.1 Velero（集群资源 + Longhorn 卷备份到阿里云 OSS）

```bash
helm repo add vmware-tanzu https://vmware-tanzu.github.io/helm-charts

cat <<EOF > velero-values.yaml
configuration:
  backupStorageLocation:
  - name: default
    provider: aws
    bucket: calendar-velero-backup
    config:
      region: cn-hangzhou
      s3ForcePathStyle: "true"
      s3Url: https://oss-cn-hangzhou.aliyuncs.com
  volumeSnapshotLocation:
  - name: default
    provider: aws
    config:
      region: cn-hangzhou
credentials:
  useSecret: true
  secretContents:
    cloud: |
      [default]
      aws_access_key_id = xxx
      aws_secret_access_key = xxx
initContainers:
- name: velero-plugin-for-aws
  image: velero/velero-plugin-for-aws:v1.9.0
  volumeMounts:
  - name: plugins
    mountPath: /target
snapshotsEnabled: true
deployNodeAgent: true
EOF

helm install velero vmware-tanzu/velero -n velero --create-namespace -f velero-values.yaml
```

### 27.2 定时备份

```yaml
apiVersion: velero.io/v1
kind: Schedule
metadata:
  name: daily-full
  namespace: velero
spec:
  schedule: "0 1 * * *"
  template:
    ttl: "720h"                   # 30 天
    includedNamespaces:
    - calendar-data
    - calendar-dev
    - calendar-stg
    - harbor
    - gitea
    - argocd
    defaultVolumesToFsBackup: true
```

### 27.3 PG 异地流复制到云端

参考 §16.2 中 `bootstrap.recovery` 和 CNPG `externalClusters` 配置。

关键：在云端部署一个 `CloudNativePG Cluster` 作为 standby，其 `replica.source` 指向 IDC 的 PG primary（通过 WireGuard 内网）。

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: pg-calendar-dr
  namespace: calendar-data
spec:
  instances: 1
  replica:
    enabled: true
    source: idc-primary
  externalClusters:
  - name: idc-primary
    connectionParameters:
      host: pg-calendar-rw.calendar-data.svc.idc.local
      user: streaming_replica
      dbname: postgres
      sslmode: require
    password:
      name: pg-replica-cred
      key: password
  # storage, resources 同 §16.2
```

---

## 28. 故障演练 Runbook

### 28.1 单节点宕机

**症状**：`kubectl get nodes` 看到一个节点 `NotReady`。

**处理**：
1. `kubectl describe node <name>` 看事件
2. `ssh` 进去看 kubelet `journalctl -u kubelet -n 200`
3. 5 分钟未恢复 → 驱逐：
   ```bash
   kubectl drain <name> --ignore-daemonsets --delete-emptydir-data --force
   ```
4. 其余节点会重建 Pod（Longhorn 三副本保证存储）
5. 上线后 `kubectl uncordon <name>`

**业务影响**：
- etcd 从 3 降到 2（仍可写，quorum=2）
- 若 node-01 挂 → Rancher 暂不可用（单副本）；Harbor 在 node-02，ArgoCD 在 node-03 不受影响
- 业务 Pod 会被重新调度，Longhorn 卷在其他节点已有副本，约 30s 内恢复

### 28.2 etcd 损坏

**症状**：apiserver 报 `context deadline exceeded` 持续。

**处理**：
```bash
# 在任一健康 control-plane 上
sudo -i
export ETCDCTL_API=3
alias etcdctl='etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key'

etcdctl member list
etcdctl endpoint health --cluster

# 移除故障成员
etcdctl member remove <broken-member-id>

# 在故障节点清理
sudo rm -rf /var/lib/etcd/member

# 重新加回
kubeadm reset phase cleanup-node
# 在健康节点执行：
kubeadm token create --print-join-command
# 用带 --control-plane 的 join 命令把节点加回来
```

### 28.3 Longhorn 卷降级

**症状**：某 PVC 的 Longhorn replica 显示 `degraded`。

**处理**：
1. UI 或 `kubectl -n longhorn-system get volumes.longhorn.io` 查看
2. 自动重建：Longhorn 会在其他节点自动拉起新副本
3. 若自动重建失败（存储不足）：清理旧 snapshot / 临时卷

### 28.4 资源耗尽 OOM

**症状**：大量 Pod `OOMKilled`，`kubectl top node` 显示内存 > 90%。

**处理**：
1. 立即：
   ```bash
   # 临时把 dev 环境缩到 0
   kubectl -n calendar-dev scale deploy --all --replicas=0
   ```
2. 中期：把 Prometheus 推到云端 remote_write，本地只留 24h
3. 长期：按 §28.7 启动云端 Spot 承担 dev 环境

### 28.5 Harbor 镜像仓库满

**症状**：推送失败 `no space left on device`。

**处理**：
```bash
# 立即手动 GC
curl -u admin:xxx -X POST https://harbor.cal.example.com/api/v2.0/system/gc/schedule \
  -H "Content-Type: application/json" \
  -d '{"schedule":{"type":"Manual"},"parameters":{"delete_untagged":true}}'

# 中期：Retention 策略
curl -u admin:xxx -X POST https://harbor.cal.example.com/api/v2.0/retentions \
  -H "Content-Type: application/json" \
  -d '{
    "scope":{"level":"project","ref":1},
    "rules":[{"action":"retain","template":"latestPushedK","params":{"latestPushedK":10}}]
  }'
```

### 28.6 证书过期

kubeadm 默认证书 1 年有效：

```bash
# 查看
kubeadm certs check-expiration

# 续期（所有 control-plane 分别执行）
kubeadm certs renew all
# 重启静态 Pod
sudo mv /etc/kubernetes/manifests/kube-apiserver.yaml /tmp/
sudo mv /etc/kubernetes/manifests/kube-controller-manager.yaml /tmp/
sudo mv /etc/kubernetes/manifests/kube-scheduler.yaml /tmp/
sleep 10
sudo mv /tmp/kube-*.yaml /etc/kubernetes/manifests/
```

### 28.7 业务峰值 — 临时扩展到云端

```bash
# 1. 拉起云端 worker
aliyun ess ModifyScalingGroup --ScalingGroupId <id> --MinSize 3 --MaxSize 6

# 2. 在 ArgoCD 里把 stg 的 replica 改到云端集群
kubectl -n argocd edit applicationset calendar-all-envs
# 把 env=stg 的 cluster 改为 calendar-burst

# 3. ArgoCD 自动 sync

# 4. 压测完：
kubectl -n argocd edit applicationset calendar-all-envs   # 改回 in-cluster
aliyun ess ModifyScalingGroup --ScalingGroupId <id> --MinSize 0
```

### 28.8 完整集群灾难恢复

若 IDC 全瘫：
1. 在云端跑 `terraform apply -var workers=3`
2. `velero restore create --from-schedule daily-full`
3. 从 PG Standby 提升为 primary：
   ```bash
   kubectl -n calendar-data patch cluster pg-calendar-dr --type=merge \
     -p '{"spec":{"replica":{"enabled":false}}}'
   ```
4. DNS 切换到云端 ingress IP（RTO ≈ 1 小时，RPO ≈ 5 分钟）

---

## 29. 验收清单

按顺序执行，每条 Pass 才能进下一阶段：

| # | 项 | 验证命令 | 期望 |
|---|---|---|---|
| 1 | 3 节点 Ready | `kubectl get nodes` | 3 个 Ready, 角色 control-plane |
| 2 | etcd HA | `kubectl -n kube-system exec etcd-node-01 -- etcdctl --endpoints=https://127.0.0.1:2379 --cacert=/etc/kubernetes/pki/etcd/ca.crt --cert=/etc/kubernetes/pki/etcd/peer.crt --key=/etc/kubernetes/pki/etcd/peer.key member list` | 3 个 started |
| 3 | Calico 就绪 | `kubectl get tigerastatus` | 全部 Available |
| 4 | Longhorn 健康 | `kubectl -n longhorn-system get nodes.longhorn.io` | 3 节点 ready |
| 5 | ingress-nginx EXTERNAL-IP | `kubectl -n ingress-nginx get svc ingress-nginx-controller` | 192.168.10.100 |
| 6 | Rancher 可登录 | `curl -kI https://rancher.cal.example.com` | 200/302 |
| 7 | Harbor 推送 | `docker push harbor.cal.example.com/calendar/test:v1` | Pushed |
| 8 | Helm 推送 | `helm push calendar-core-0.1.0.tgz oci://harbor.cal.example.com/calendar` | OK |
| 9 | ArgoCD 同步 | `argocd app get calendar-dev` | Synced, Healthy |
| 10 | CloudNativePG 主备 | `kubectl -n calendar-data get cluster pg-calendar` | 3 replicas, 1 primary |
| 11 | PG 连通 | `kubectl run -it psql --rm --image=postgres:16-alpine -- psql -h pg-calendar-rw.calendar-data -U calendar` | 能连 |
| 12 | Redis Sentinel | `kubectl -n calendar-data exec -it rfr-redis-calendar-0 -- redis-cli -p 26379 sentinel masters` | 1 个 master |
| 13 | Nacos 集群 | `curl http://nacos.calendar-data.svc:8848/nacos/v1/ns/operator/cluster/states` | 3 节点 UP |
| 14 | RocketMQ NS | `kubectl -n calendar-data exec broker-0-master-0 -- sh mqadmin clusterList -n name-service-service:9876` | 3 broker |
| 15 | ES 绿色 | `kubectl -n calendar-data exec es-calendar-es-default-0 -- curl -sk -u elastic:$PWD https://localhost:9200/_cluster/health` | green |
| 16 | Grafana 面板 | 打开 `grafana.cal.example.com` 看 K8s/Node 面板 | 数据有流入 |
| 17 | Loki 有日志 | Grafana → Explore → Loki → `{namespace="calendar-data"}` | 有日志 |
| 18 | dev 业务 Pod Running | `kubectl -n calendar-dev get pod` | 4 个服务 Running |
| 19 | CI 跑通 | 提交代码到 Gitea develop 分支 | Actions 绿灯，镜像 Push 到 Harbor |
| 20 | 灰度发布 | `kubectl argo rollouts get rollout calendar-core -n calendar-stg` | 步骤可见，成功 promote |
| 21 | WireGuard 连通 | `ping 10.200.0.2` (IDC→云) | 通 |
| 22 | Velero 备份 | `velero backup create test-now --include-namespaces calendar-data` | Completed |
| 23 | Pod 驱逐 RTO | drain node-02 → 秒表 calendar-core 可访问 | < 60s |

---

## 附录 A：节点标签与调度策略

### A.1 标签规划

```bash
# 所有节点（脚本化）
for i in 1 2 3; do
  kubectl label node node-0${i} \
    workload=mixed \
    env-allowed=dev/stg \
    idc-rack=r${i} \
    storage=longhorn \
    --overwrite
done

# node-01: 平台核心（Rancher）
kubectl label node node-01 role=platform-core --overwrite

# node-02: 镜像仓库 + VCS
kubectl label node node-02 role=registry-vcs --overwrite

# node-03: GitOps + 观测
kubectl label node node-03 role=gitops-obs --overwrite
```

### A.2 关键 Pod 绑定示例

**Rancher**：`nodeSelector: { kubernetes.io/hostname: node-01 }`

**Harbor**：`nodeSelector: { kubernetes.io/hostname: node-02 }`

**ArgoCD**：`nodeSelector: { kubernetes.io/hostname: node-03 }`

**数据库/MQ/ES**：使用 PodAntiAffinity，强制 3 副本分散到 3 节点。

**业务 Pod**：不绑定节点，使用 PodAntiAffinity `preferredDuringScheduling` 让多副本尽量分散。

---

## 附录 B：资源 Requests/Limits 总表

| Namespace | Workload | Replicas | CPU Req | Mem Req | CPU Lim | Mem Lim |
|---|---|---|---|---|---|---|
| kube-system | kube-apiserver | 3 | 250m | 512Mi | 2000m | 1.5Gi |
| kube-system | etcd | 3 | 100m | 256Mi | 500m | 800Mi |
| kube-system | kube-controller-manager | 3 | 100m | 128Mi | 500m | 512Mi |
| kube-system | kube-scheduler | 3 | 100m | 128Mi | 500m | 256Mi |
| calico-system | calico-node | 3 | 100m | 128Mi | 500m | 512Mi |
| longhorn-system | longhorn-manager | 3 | 100m | 128Mi | 500m | 512Mi |
| ingress-nginx | controller | 2 | 100m | 128Mi | 500m | 512Mi |
| cattle-system | rancher | 1 | 500m | 1Gi | 2000m | 2Gi |
| harbor | all pods | - | ~400m | ~1.5Gi | ~3000m | ~4Gi |
| gitea | gitea + pg | 1+1 | 200m | 512Mi | 1500m | 1.2Gi |
| argocd | all | - | ~500m | ~1.2Gi | ~2500m | ~2.5Gi |
| observability | prometheus | 1 | 300m | 1Gi | 2000m | 3Gi |
| observability | grafana | 1 | 100m | 256Mi | 500m | 512Mi |
| observability | loki | 1 | 100m | 256Mi | 1000m | 1Gi |
| observability | tempo | 1 | 100m | 256Mi | 500m | 512Mi |
| calendar-data | pg-calendar (cnpg) | 3 | 500m | 1Gi | 2000m | 2Gi |
| calendar-data | redis | 3+3 sentinel | 150m | 320Mi | 700m | 640Mi |
| calendar-data | nacos | 3 | 200m | 768Mi | 1000m | 1.5Gi |
| calendar-data | rocketmq broker | 3 | 300m | 1Gi | 1500m | 1.5Gi |
| calendar-data | elasticsearch | 3 | 300m | 1.5Gi | 1500m | 2Gi |
| calendar-dev | 4 services | 4 | 100m | 256Mi | 500m | 512Mi |
| calendar-stg | 4 services | 8 | 100m | 256Mi | 500m | 512Mi |

**Request 合计 ≈ 14 核 CPU / 26 GB 内存**（24 核 / 48 GB 总容量），留 10 核 / 22 GB 余量。

---

## 附录 C：常见故障排查

| 现象 | 可能原因 | 排查 |
|---|---|---|
| Pod Pending，事件 `0/3 nodes: insufficient memory` | Request 总和超过 allocatable | `kubectl describe node <n> \| grep -A5 Allocated`；调小请求 |
| Pod CrashLoopBackOff | 应用启动失败 | `kubectl logs --previous`；检查 ConfigMap/Secret |
| PVC Pending | Longhorn 存储不足或调度失败 | `kubectl get events -A`；Longhorn UI 看容量 |
| Ingress 404 | 域名 DNS / host 配置 | `nslookup`；`kubectl describe ingress` 看 backend |
| etcd database space exceeded | etcd 没定期 compact | `etcdctl compact $(etcdctl endpoint status --write-out=json \| jq .[0].Status.header.revision)`；然后 `etcdctl defrag` |
| Rancher "forbidden" | 证书 / bootstrapPassword 未重置 | 重置 `kubectl -n cattle-system exec deploy/rancher -- reset-password` |
| Argo Rollouts 卡在 Analysis | Prometheus 查不到指标 | 直接在 Grafana 跑 AnalysisTemplate 里的 PromQL 验证 |
| CI Kaniko build OOM | memory limit 过低 | 把 build Pod limit 调到 2-4Gi；Spot 换大规格 |
| VPN 断连 | PersistentKeepalive 太长 / 云端 SG 端口 | `wg show`；检查阿里云安全组 UDP 51820 |

---

## 总结

这套方案在 **3 台 8C/16G IDC + 阿里云 Spot** 硬件下：

- **能跑什么**：完整 K8s v1.28.10 HA 控制面、Rancher/Harbor/Gitea/ArgoCD 平台全家桶、PG/Redis/Nacos/MQ/ES 数据面、dev + 小型 stg 业务环境、完整 CI/CD 流水线、可观测 + 备份 + 灾备。
- **能抗什么**：任一节点宕机不丢数据；etcd/业务均可继续；分钟级恢复。
- **不能抗什么**：2 节点同时宕 etcd 丢 quorum；IDC 全毁要走灾备流程 (RTO~1h)；大流量压测必须推到云端。
- **每月成本**：IDC 电费+带宽（自有）+ 阿里云 Spot 按使用（估算 ¥200-800/月，看 CI 频率和压测时长）。

后续升级路径：
1. 第 4 台 IDC 机器 → 拆分 Rancher/Harbor/数据库到独立节点
2. 加 GPU 节点 → 支持 AI 辅助（根据另一份计划中的 LLM 功能预留）
3. 接入真公网域名 + Let's Encrypt + 边缘 CDN → 变成生产级
4. 开启 Istio + Kyverno + Vault → 补齐企业级安全治理

---

**文档版本**：v1.0.0 · 2026-04-20
**适用 K8s 版本**：v1.28.10
**审阅建议**：部署前先通读 §0 与 §2，确认容量认知对齐；按 §4 → §29 顺序执行并逐条验证。
