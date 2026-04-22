# AI 执行 Runbook：Git 到部署闭环
## IDC 3 节点 kubeadm + 禁用端口 80/443/8080/8443

> **本文件定位**：这是《企业级云原生平台落地方案_IDC3节点_kubeadm_阿里云Spot混合.md》的**执行副本**。AI 可以从上到下逐条命令执行，每一阶段都有通过/失败二元判定的验证步骤。
>
> **目标**：从 3 台干净的 Linux 机器 + root 口令开始，最终达到"开发者 `git push` 到 Gitea，CI 自动构建镜像并推 Harbor，ArgoCD 自动同步到集群并运行"——**完整 GitOps 闭环**。
>
> **非目标**（以下放到第二轮，不在这份 Runbook 内）：数据面 (PG/Redis/MQ/ES/Nacos)、阿里云 Spot 突发集群、WireGuard VPN、可观测全套、Velero 备份。先跑通闭环，再补这些。

---

## 0. 前置输入（AI 开跑前必须具备的信息）

```bash
# ====== 由用户提供的变量（AI 执行前替换）======
export NODE1_IP="__NODE1_IP__"       # 控制面 VIP 承载节点，默认 Rancher
export NODE2_IP="__NODE2_IP__"       # Harbor + Gitea
export NODE3_IP="__NODE3_IP__"       # ArgoCD + Runner + 观测
export ROOT_PASSWORD="__ROOT_PWD__"  # 三台机器相同 root 密码
export VIP="__VIP_IP__"              # 控制面 VIP，与 NODE1-3 同网段且未被占用（如 192.168.10.10）
export LB_IP="__LB_IP__"             # ingress-nginx 对外 VIP（如 192.168.10.100）

# ====== 由本方案固定的常量（不要改）======
export K8S_VER="1.28.10"
export POD_CIDR="10.244.0.0/16"
export SVC_CIDR="10.96.0.0/12"
export INGRESS_HTTP_PORT="18080"     # 避开 80/8080
export INGRESS_HTTPS_PORT="18443"    # 避开 443/8443

# ====== 基于上面自动推导（用 nip.io 免域名托管 DNS）======
export BASE_HOST="${LB_IP}.nip.io"              # 例：192.168.10.100.nip.io
export RANCHER_URL="https://rancher.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
export HARBOR_URL="https://harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
export HARBOR_REG="harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}"     # docker login 用
export GITEA_URL="https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
export ARGOCD_URL="https://argocd.${BASE_HOST}:${INGRESS_HTTPS_PORT}"

# ====== 状态持久化（关键：多次执行/新 shell 都能恢复）======
# 每阶段抽取的动态凭证（Harbor robot、Gitea token、ArgoCD 初始密码等）都写入下面这个文件。
# AI 执行前先 `source $STATE_FILE` 恢复上下文。
export STATE_FILE="/tmp/runbook-state.env"
touch $STATE_FILE
# 把 §0 的静态变量也固化一次
cat > $STATE_FILE <<EOF
export NODE1_IP="$NODE1_IP"
export NODE2_IP="$NODE2_IP"
export NODE3_IP="$NODE3_IP"
export ROOT_PASSWORD="$ROOT_PASSWORD"
export VIP="$VIP"
export LB_IP="$LB_IP"
export K8S_VER="$K8S_VER"
export POD_CIDR="$POD_CIDR"
export SVC_CIDR="$SVC_CIDR"
export INGRESS_HTTP_PORT="$INGRESS_HTTP_PORT"
export INGRESS_HTTPS_PORT="$INGRESS_HTTPS_PORT"
export BASE_HOST="$BASE_HOST"
export HARBOR_REG="$HARBOR_REG"
export RANCHER_URL="$RANCHER_URL"
export HARBOR_URL="$HARBOR_URL"
export GITEA_URL="$GITEA_URL"
export ARGOCD_URL="$ARGOCD_URL"
export KUBECONFIG="$PWD/admin.conf"
EOF

# ====== AI 执行每个新阶段开头必跑这一行 ======
# source $STATE_FILE
```

> **给 AI 的约定**：任何后续阶段的命令开头都先 `source /tmp/runbook-state.env`。每当本 Runbook 里出现形如 `echo "export KEY=VAL" >> $STATE_FILE` 的追加命令，就是在向状态文件写入新凭证。

> **nip.io 机制**：`xxx.192.168.10.100.nip.io` 自动解析到 `192.168.10.100`，零 DNS 配置。需要机器能出公网或能解析 *.nip.io（纯内网可用 dnsmasq wildcard 替代，见 §15.4）。

### 0.1 连接性自检

```bash
# 验证从执行机可以 SSH 到三台节点
for ip in $NODE1_IP $NODE2_IP $NODE3_IP; do
  echo "=== $ip ==="
  sshpass -p "$ROOT_PASSWORD" ssh -o StrictHostKeyChecking=no \
    root@$ip "hostname; uname -r; cat /etc/os-release | grep ^PRETTY_NAME; free -h | head -2; lsblk | grep disk"
done
```

**通过标准**：3 台全部响应，内核 ≥ 5.15，OS 为 Ubuntu 22.04（若不是，后面 apt 命令需替换为 dnf/yum）。

### 0.2 检查禁用端口未被占用

```bash
for ip in $NODE1_IP $NODE2_IP $NODE3_IP; do
  sshpass -p "$ROOT_PASSWORD" ssh root@$ip \
    "ss -tln | awk 'NR>1 {print \$4}' | awk -F: '{print \$NF}' | sort -u | grep -E '^(80|443|8080|8443|$INGRESS_HTTP_PORT|$INGRESS_HTTPS_PORT|6443|2379|2380)$' || echo 'OK: no conflict on $ip'"
done
```

**通过标准**：输出全部为 `OK: no conflict`。如果 80/443 有占用（常见是系统默认 Nginx），要先停；如果 18080/18443 有占用，换端口（如 28080/28443）并同步修改 §0 常量。

---

## 1. 阶段 P0：三节点操作系统准备

### 1.1 批量初始化脚本（在每个节点跑一次）

把下面脚本保存为 `/tmp/node-init.sh`，scp 到每台节点执行：

```bash
cat > /tmp/node-init.sh <<'SCRIPT'
#!/bin/bash
set -euo pipefail

# 参数：节点序号（1/2/3）
NODE_IDX=$1
HOSTNAME="node-0${NODE_IDX}"

echo "==> [1/10] 设置 hostname"
hostnamectl set-hostname $HOSTNAME

echo "==> [2/10] 写 /etc/hosts"
cat > /etc/hosts <<EOF
127.0.0.1 localhost
${NODE1_IP} node-01
${NODE2_IP} node-02
${NODE3_IP} node-03
${VIP} k8s-api.idc.local
EOF

echo "==> [3/10] 关闭 swap"
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

echo "==> [4/10] 关闭 ufw/firewalld（内网演示场景；生产请改为精细白名单）"
systemctl disable --now ufw 2>/dev/null || true
systemctl disable --now firewalld 2>/dev/null || true

echo "==> [5/10] 内核模块"
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
ip_vs
ip_vs_rr
ip_vs_wrr
ip_vs_sh
nf_conntrack
EOF
modprobe overlay br_netfilter ip_vs ip_vs_rr ip_vs_wrr ip_vs_sh nf_conntrack

echo "==> [6/10] sysctl"
cat > /etc/sysctl.d/99-k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
vm.swappiness                       = 0
vm.overcommit_memory                = 1
fs.inotify.max_user_instances       = 8192
fs.inotify.max_user_watches         = 524288
EOF
sysctl --system

echo "==> [7/10] 时间同步"
apt-get update -q
apt-get install -y chrony curl ca-certificates gnupg apt-transport-https sshpass jq
systemctl enable --now chrony

echo "==> [8/10] 数据盘挂载到 /var/lib/longhorn (假设是 /dev/sdb)"
DISK=/dev/sdb
if [ -b $DISK ] && ! mount | grep -q /var/lib/longhorn; then
  if ! blkid $DISK >/dev/null; then
    mkfs.ext4 -F $DISK
  fi
  mkdir -p /var/lib/longhorn
  UUID=$(blkid -s UUID -o value $DISK)
  grep -q "$UUID" /etc/fstab || echo "UUID=$UUID /var/lib/longhorn ext4 defaults,noatime 0 2" >> /etc/fstab
  mount -a
fi

echo "==> [9/10] 安装 containerd"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
apt-get update -q
apt-get install -y containerd.io=1.7.*
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sed -i 's|sandbox_image = "registry.k8s.io/pause:3.8"|sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
systemctl enable --now containerd
systemctl restart containerd

echo "==> [10/10] 安装 kubeadm/kubelet/kubectl $K8S_VER"
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.28/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.28/deb/ /' > /etc/apt/sources.list.d/kubernetes.list
apt-get update -q
apt-get install -y kubelet=${K8S_VER}-1.1 kubeadm=${K8S_VER}-1.1 kubectl=${K8S_VER}-1.1
apt-mark hold kubelet kubeadm kubectl
systemctl enable kubelet

echo "==> 完成。hostname=$(hostname)"
SCRIPT
chmod +x /tmp/node-init.sh

# 分发并执行
for i in 1 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  echo "====== init node-0$i ($NODE_IP) ======"
  sshpass -p "$ROOT_PASSWORD" scp -o StrictHostKeyChecking=no /tmp/node-init.sh root@$NODE_IP:/tmp/
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP \
    "NODE1_IP=$NODE1_IP NODE2_IP=$NODE2_IP NODE3_IP=$NODE3_IP VIP=$VIP K8S_VER=$K8S_VER \
     bash /tmp/node-init.sh $i"
done
```

### 1.2 验证 P0

```bash
for ip in $NODE1_IP $NODE2_IP $NODE3_IP; do
  sshpass -p "$ROOT_PASSWORD" ssh root@$ip '
    echo "== hostname: $(hostname)"
    echo "== swap: $(swapon --show | wc -l) (期望 0)"
    echo "== containerd: $(systemctl is-active containerd)"
    echo "== kubelet: $(systemctl is-enabled kubelet)"
    echo "== longhorn mount: $(df -h /var/lib/longhorn | tail -1 | awk "{print \$2}")"
    kubeadm version | head -1
  '
done
```

**通过标准**：hostname 分别为 node-01/02/03；swap=0；containerd=active；kubelet=enabled；/var/lib/longhorn 约 98G；kubeadm 版本 v1.28.10。

---

## 2. 阶段 P1：部署 keepalived VIP（避免控制面单点）

> **为什么提前**：kubeadm init 时 `controlPlaneEndpoint` 必须是所有节点可达的地址。如果用 node-01 IP 直连，node-01 挂了整套 kubelet 都会失联。

```bash
cat > /tmp/keepalived-setup.sh <<'SCRIPT'
#!/bin/bash
set -e
NODE_IDX=$1
VIP=$2
NODE1_IP=$3 NODE2_IP=$4 NODE3_IP=$5

apt-get install -y keepalived

# node-01: priority 100 (MASTER), node-02: 90, node-03: 80
declare -A P=([1]=100 [2]=90 [3]=80)
declare -A S=([1]=MASTER [2]=BACKUP [3]=BACKUP)
PRIORITY=${P[$NODE_IDX]}
STATE=${S[$NODE_IDX]}

# 探测默认网卡
IFACE=$(ip -o -4 route show to default | awk '{print $5}' | head -1)

cat > /etc/keepalived/keepalived.conf <<EOF
global_defs {
  router_id k8s_$(hostname)
  script_user root
  enable_script_security
}
vrrp_script chk_apiserver {
  script "/bin/sh -c 'curl -sk https://127.0.0.1:6443/healthz -o /dev/null -w %{http_code} | grep -q 200 || exit 1'"
  interval 3
  weight -2
  fall 3
  rise 2
}
vrrp_instance VI_1 {
  state $STATE
  interface $IFACE
  virtual_router_id 51
  priority $PRIORITY
  advert_int 1
  authentication {
    auth_type PASS
    auth_pass k8sVIP2026
  }
  virtual_ipaddress {
    $VIP
  }
  # init 阶段 apiserver 还没起来，先注释探测；kubeadm init 完成后取消注释
  # track_script { chk_apiserver }
}
EOF
systemctl enable keepalived
systemctl restart keepalived
SCRIPT

for i in 1 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  sshpass -p "$ROOT_PASSWORD" scp /tmp/keepalived-setup.sh root@$NODE_IP:/tmp/
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP \
    "bash /tmp/keepalived-setup.sh $i $VIP $NODE1_IP $NODE2_IP $NODE3_IP"
done
```

### 2.1 验证 P1

```bash
ping -c 3 $VIP   # 应该通（此时由 node-01 持有）
sshpass -p "$ROOT_PASSWORD" ssh root@$NODE1_IP "ip addr show | grep $VIP"  # 应该显示 VIP
```

**通过标准**：VIP 可 ping，node-01 持有该 IP。

---

## 3. 阶段 P2：kubeadm init 第一个控制面

```bash
# 在 node-01 上
cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: $NODE1_IP
  bindPort: 6443
nodeRegistration:
  name: node-01
  criSocket: unix:///run/containerd/containerd.sock
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v$K8S_VER
clusterName: idc-calendar
controlPlaneEndpoint: "$VIP:6443"
networking:
  serviceSubnet: $SVC_CIDR
  podSubnet: $POD_CIDR
apiServer:
  certSANs:
  - k8s-api.idc.local
  - $VIP
  - $NODE1_IP
  - $NODE2_IP
  - $NODE3_IP
  - node-01
  - node-02
  - node-03
  - 127.0.0.1
imageRepository: registry.aliyuncs.com/google_containers
etcd:
  local:
    extraArgs:
      listen-metrics-urls: http://0.0.0.0:2381
---
apiVersion: kubeproxy.config.k8s.io/v1alpha1
kind: KubeProxyConfiguration
mode: ipvs
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
maxPods: 110
EOF

sshpass -p "$ROOT_PASSWORD" scp /tmp/kubeadm-config.yaml root@$NODE1_IP:/root/
sshpass -p "$ROOT_PASSWORD" ssh root@$NODE1_IP '
  set -e
  kubeadm config images pull --config /root/kubeadm-config.yaml
  kubeadm init --config /root/kubeadm-config.yaml --upload-certs 2>&1 | tee /root/kubeadm-init.log
  mkdir -p $HOME/.kube
  cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
  kubectl taint nodes --all node-role.kubernetes.io/control-plane:NoSchedule- || true
'

# 把 admin.conf 拉到执行机
sshpass -p "$ROOT_PASSWORD" scp root@$NODE1_IP:/etc/kubernetes/admin.conf ./admin.conf
sed -i "s|server: https://.*|server: https://$VIP:6443|" ./admin.conf
export KUBECONFIG=$PWD/admin.conf

# 抓取 join 命令（含 certKey）
CP_JOIN=$(sshpass -p "$ROOT_PASSWORD" ssh root@$NODE1_IP \
  "grep -A2 'control-plane' /root/kubeadm-init.log | tail -3 | tr '\n' ' ' | sed 's/\\\\//g'")
echo "Control-plane join cmd: $CP_JOIN"

# 在 node-02 / node-03 执行 join
for i in 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP "
    $CP_JOIN --apiserver-advertise-address $NODE_IP
    mkdir -p \$HOME/.kube
    cp -f /etc/kubernetes/admin.conf \$HOME/.kube/config
  "
done

# 所有节点去掉 NoSchedule taint
kubectl taint nodes --all node-role.kubernetes.io/control-plane:NoSchedule- || true

# 打标签
kubectl label node node-01 role=platform-core idc-rack=r1 --overwrite
kubectl label node node-02 role=registry-vcs  idc-rack=r2 --overwrite
kubectl label node node-03 role=gitops-obs    idc-rack=r3 --overwrite

# 启用 keepalived 的 apiserver 探测脚本
for i in 1 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP "
    sed -i 's|# track_script|track_script|; s|#   chk_apiserver|  chk_apiserver|; s|# }$|}|' /etc/keepalived/keepalived.conf
    systemctl restart keepalived
  "
done
```

### 3.1 验证 P2

```bash
kubectl get nodes -o wide
# 期望：3 节点都出现（NotReady 正常，因为 CNI 还没装），角色 control-plane
kubectl get pod -n kube-system
# etcd-node-01/02/03, kube-apiserver-node-01/02/03 都 Running
# （CoreDNS 还是 Pending，等 CNI）
```

**通过标准**：3 节点都 Registered；etcd 和 apiserver 都 Running × 3。

---

## 4. 阶段 P3：Calico CNI

```bash
kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.2/manifests/tigera-operator.yaml

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
      cidr: $POD_CIDR
      encapsulation: VXLAN
      natOutgoing: Enabled
      nodeSelector: all()
  registry: quay.io/
---
apiVersion: operator.tigera.io/v1
kind: APIServer
metadata:
  name: default
spec: {}
EOF

# 等待 tigerastatus 全部 Available
for i in 1 2 3 4 5 6 7 8 9 10; do
  STATUS=$(kubectl get tigerastatus -o jsonpath='{.items[*].status.conditions[?(@.type=="Available")].status}')
  [ "$STATUS" = "True True True" ] && { echo "Calico ready"; break; }
  echo "Waiting Calico... ($i/10)"; sleep 30
done

kubectl get nodes   # 全部 Ready
```

**通过标准**：3 节点 Ready，`kubectl get tigerastatus` 全 Available=True。

---

## 5. 阶段 P4：Longhorn 存储

```bash
# 前置：每节点 open-iscsi（node-init.sh 里已安装，这里确认）
for i in 1 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP \
    "apt-get install -y open-iscsi nfs-common && systemctl enable --now iscsid"
done

# 安装 Helm
if ! command -v helm; then
  curl -fsSL https://get.helm.sh/helm-v3.14.4-linux-amd64.tar.gz | tar xz
  mv linux-amd64/helm /usr/local/bin/
fi

helm repo add longhorn https://charts.longhorn.io
helm repo update

cat > /tmp/longhorn-values.yaml <<EOF
persistence:
  defaultClass: true
  defaultClassReplicaCount: 3
  reclaimPolicy: Delete
defaultSettings:
  defaultReplicaCount: 3
  defaultDataPath: /var/lib/longhorn
  storageOverProvisioningPercentage: 100
  storageMinimalAvailablePercentage: 15
service:
  ui:
    type: ClusterIP
ingress:
  enabled: false
EOF

kubectl create namespace longhorn-system
helm install longhorn longhorn/longhorn -n longhorn-system \
  -f /tmp/longhorn-values.yaml --version 1.6.1

# 等待
kubectl -n longhorn-system wait --for=condition=ready pod -l app=longhorn-manager --timeout=600s

# 额外创建一个单副本 SC（给有自主复制的中间件用，不在闭环必需，可后置）
cat <<EOF | kubectl apply -f -
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
  fsType: ext4
EOF
```

### 5.1 验证 P4

```bash
kubectl get sc
# 期望 longhorn (default) 和 longhorn-single 都在
kubectl -n longhorn-system get pod | grep -v Running | grep -v Completed | wc -l
# 期望仅头部 1 行
```

**通过标准**：Longhorn 所有 Pod Running；默认 StorageClass 是 longhorn。

---

## 6. 阶段 P5：MetalLB + ingress-nginx（**禁用端口适配**）

### 6.1 MetalLB

```bash
kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.14.3/config/manifests/metallb-native.yaml
kubectl -n metallb-system wait --for=condition=ready pod -l app=metallb --timeout=300s

# IP 池
cat <<EOF | kubectl apply -f -
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: idc-pool
  namespace: metallb-system
spec:
  addresses:
  - $LB_IP/32
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: idc-l2
  namespace: metallb-system
spec:
  ipAddressPools: [idc-pool]
EOF
```

### 6.2 ingress-nginx —— 关键：**监听 18080 / 18443**

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

cat > /tmp/ingress-values.yaml <<EOF
controller:
  replicaCount: 2
  kind: Deployment
  service:
    type: LoadBalancer
    loadBalancerIP: $LB_IP
    ports:
      http: $INGRESS_HTTP_PORT
      https: $INGRESS_HTTPS_PORT
    targetPorts:
      http: http
      https: https
  containerPort:
    http: 80      # Pod 内部监听仍是 80/443（不影响主机端口）
    https: 443
  config:
    proxy-body-size: "100m"
    use-forwarded-headers: "true"
    ssl-redirect: "true"
    hsts: "true"
  metrics:
    enabled: true
  affinity:
    podAntiAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels: { app.kubernetes.io/name: ingress-nginx }
        topologyKey: kubernetes.io/hostname
  resources:
    requests: { cpu: 100m, memory: 128Mi }
    limits:   { cpu: 500m, memory: 512Mi }
EOF

helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  -f /tmp/ingress-values.yaml --version 4.10.0

kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=300s
```

### 6.3 验证 P5

```bash
kubectl -n ingress-nginx get svc ingress-nginx-controller
# 期望 EXTERNAL-IP = $LB_IP，PORT(S) 包含 18080:XXXXX/TCP, 18443:XXXXX/TCP

# 从执行机测试
curl -I http://$LB_IP:$INGRESS_HTTP_PORT
# 期望 HTTP/1.1 404 Not Found （这是正常的——还没配置 host，但 ingress 已在响应）
```

**通过标准**：EXTERNAL-IP 正确，`curl http://$LB_IP:18080` 返回 404（证明 ingress 已接管）。

---

## 7. 阶段 P6：cert-manager + 自签 CA + 分发到 containerd

### 7.1 cert-manager

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml
kubectl -n cert-manager wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager --timeout=300s

# 自签 CA
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata: { name: selfsigned-bootstrap }
spec: { selfSigned: {} }
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: idc-root-ca
  namespace: cert-manager
spec:
  isCA: true
  commonName: idc-calendar-ca
  secretName: idc-root-ca-secret
  duration: 87600h       # 10 年
  privateKey: { algorithm: ECDSA, size: 256 }
  issuerRef: { name: selfsigned-bootstrap, kind: ClusterIssuer }
---
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata: { name: idc-ca-issuer }
spec:
  ca: { secretName: idc-root-ca-secret }
EOF

# 等 CA 证书就绪
until kubectl -n cert-manager get secret idc-root-ca-secret >/dev/null 2>&1; do sleep 2; done
```

### 7.2 **关键：CA 证书分发到每个节点的 containerd**

```bash
source $STATE_FILE

# 导出 CA
kubectl -n cert-manager get secret idc-root-ca-secret -o jsonpath='{.data.tls\.crt}' \
  | base64 -d > /tmp/idc-ca.crt

# Harbor 的镜像地址（带端口）。containerd 1.7 的 certs.d 原生支持 "HOST:PORT" 目录名，
# 所以 CA 和 hosts.toml 放在同一个目录即可（不再使用下划线绕路）。
REG_HOST="harbor.${BASE_HOST}"
REG_PORT="$INGRESS_HTTPS_PORT"
REG_DIR="/etc/containerd/certs.d/${REG_HOST}:${REG_PORT}"

cat > /tmp/containerd-hosts.toml <<EOF
server = "https://${REG_HOST}:${REG_PORT}"

[host."https://${REG_HOST}:${REG_PORT}"]
  capabilities = ["pull", "resolve", "push"]
  ca = "ca.crt"       # 相对路径：与 hosts.toml 同目录
EOF

for i in 1 2 3; do
  NODE_IP_VAR="NODE${i}_IP"
  NODE_IP="${!NODE_IP_VAR}"
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP "mkdir -p '$REG_DIR'"
  sshpass -p "$ROOT_PASSWORD" scp /tmp/idc-ca.crt root@$NODE_IP:"$REG_DIR/ca.crt"
  sshpass -p "$ROOT_PASSWORD" scp /tmp/containerd-hosts.toml root@$NODE_IP:"$REG_DIR/hosts.toml"
  # 也放到系统 CA store，方便 curl/git
  sshpass -p "$ROOT_PASSWORD" scp /tmp/idc-ca.crt root@$NODE_IP:/usr/local/share/ca-certificates/idc-ca.crt
  sshpass -p "$ROOT_PASSWORD" ssh root@$NODE_IP '
    update-ca-certificates
    # 让 containerd 启用 config_path（幂等）
    grep -q "config_path = \"/etc/containerd/certs.d\"" /etc/containerd/config.toml \
      || sed -i "/\[plugins.*cri.*registry\]/a\    config_path = \"/etc/containerd/certs.d\"" /etc/containerd/config.toml
    systemctl restart containerd
  '
done
```

### 7.3 验证 P6

```bash
kubectl -n cert-manager get certificate
# idc-root-ca  READY=True

# 节点侧
sshpass -p "$ROOT_PASSWORD" ssh root@$NODE1_IP \
  "ls /etc/containerd/certs.d/; openssl verify -CAfile /usr/local/share/ca-certificates/idc-ca.crt /usr/local/share/ca-certificates/idc-ca.crt"
```

**通过标准**：`Certificate` READY=True；节点上 CA 存在。

---

## 8. 阶段 P7：Harbor（**ingress 带端口 + CA 签发的证书**）

```bash
kubectl create namespace harbor

# 为 Harbor 申请证书（CN=harbor.<LB_IP>.nip.io）
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: harbor-tls
  namespace: harbor
spec:
  secretName: harbor-tls
  issuerRef: { name: idc-ca-issuer, kind: ClusterIssuer }
  dnsNames:
  - harbor.${BASE_HOST}
  duration: 8760h
EOF

helm repo add harbor https://helm.goharbor.io
helm repo update

cat > /tmp/harbor-values.yaml <<EOF
expose:
  type: ingress
  tls:
    enabled: true
    certSource: secret
    secret: { secretName: harbor-tls }
  ingress:
    hosts: { core: "harbor.${BASE_HOST}" }
    className: nginx
    annotations:
      nginx.ingress.kubernetes.io/proxy-body-size: "0"
      nginx.ingress.kubernetes.io/ssl-redirect: "true"

externalURL: "https://harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
harborAdminPassword: "Harbor12345"

persistence:
  enabled: true
  resourcePolicy: keep
  persistentVolumeClaim:
    registry:   { storageClass: longhorn, size: 20Gi }
    chartmuseum:{ storageClass: longhorn, size: 2Gi }
    jobservice: { jobLog: { storageClass: longhorn, size: 1Gi } }
    database:   { storageClass: longhorn, size: 3Gi }
    redis:      { storageClass: longhorn, size: 1Gi }
    trivy:      { storageClass: longhorn, size: 5Gi }

portal:  { replicas: 1 }
core:    { replicas: 1, resources: { requests: { cpu: 100m, memory: 256Mi }, limits: { cpu: 1000m, memory: 1Gi } } }
jobservice: { replicas: 1 }
registry:   { replicas: 1, resources: { requests: { cpu: 100m, memory: 256Mi }, limits: { cpu: 1000m, memory: 1Gi } } }
chartmuseum: { enabled: true }
trivy:       { enabled: true, replicas: 1 }
notary:      { enabled: false }

nodeSelector: { kubernetes.io/hostname: node-02 }
EOF

helm install harbor harbor/harbor -n harbor -f /tmp/harbor-values.yaml --version 1.14.2
kubectl -n harbor rollout status deploy/harbor-core --timeout=600s
kubectl -n harbor rollout status deploy/harbor-portal --timeout=600s
```

### 8.1 验证 P7

```bash
# 执行机上 curl（带 --resolve 模拟 DNS，因为执行机可能没解析 nip.io）
curl -kI --resolve harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}:${LB_IP} \
  "https://harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}/"
# 期望：HTTP/2 200 或 302（重定向到 portal）

# Docker 登录（节点上因为已加 CA 信任，直接能登）
sshpass -p "$ROOT_PASSWORD" ssh root@$NODE2_IP "
  apt-get install -y docker.io
  docker login harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT} -u admin -p 'Harbor12345'
"
# 期望：Login Succeeded
```

### 8.2 创建项目 + Robot 账号

```bash
# 端口变量替代方便写脚本
HARBOR_API="https://harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}/api/v2.0"
CURL_OPTS="-k -u admin:Harbor12345 -H 'Content-Type: application/json'"

# 创建 calendar 项目
eval curl $CURL_OPTS -X POST $HARBOR_API/projects \
  -d '"{\"project_name\":\"calendar\",\"metadata\":{\"public\":\"false\"}}"'

# 为 CI 创建 robot 账号
eval curl $CURL_OPTS -X POST $HARBOR_API/robots \
  -d '"{\"name\":\"ci\",\"duration\":-1,\"level\":\"system\",\"permissions\":[{\"kind\":\"project\",\"namespace\":\"calendar\",\"access\":[{\"resource\":\"repository\",\"action\":\"push\"},{\"resource\":\"repository\",\"action\":\"pull\"}]}]}"' \
  | tee /tmp/robot.json
ROBOT_NAME=$(jq -r .name /tmp/robot.json)
ROBOT_SECRET=$(jq -r .secret /tmp/robot.json)
echo "Robot: $ROBOT_NAME / $ROBOT_SECRET"

# 持久化到状态文件（后续阶段 source 即可拿到）
cat >> $STATE_FILE <<EOF
export ROBOT_NAME="$ROBOT_NAME"
export ROBOT_SECRET="$ROBOT_SECRET"
EOF
```

**通过标准**：curl 访问 Harbor UI 返回 200/302；docker login 成功；创建 calendar 项目 + robot 账号成功。

---

## 9. 阶段 P8：Gitea + Actions Runner

### 9.1 Gitea

```bash
kubectl create namespace gitea

# 证书
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Certificate
metadata: { name: gitea-tls, namespace: gitea }
spec:
  secretName: gitea-tls
  issuerRef: { name: idc-ca-issuer, kind: ClusterIssuer }
  dnsNames: [ "git.${BASE_HOST}" ]
  duration: 8760h
EOF

helm repo add gitea-charts https://dl.gitea.com/charts/
helm repo update

cat > /tmp/gitea-values.yaml <<EOF
replicaCount: 1
persistence:
  enabled: true
  size: 10Gi
  storageClass: longhorn
postgresql-ha: { enabled: false }
postgresql:
  enabled: true
  primary:
    persistence: { storageClass: longhorn, size: 5Gi }
redis-cluster: { enabled: false }
redis: { enabled: true }
gitea:
  admin:
    username: gitea_admin
    password: "Gitea12345"
    email: admin@idc.local
  config:
    server:
      DOMAIN: "git.${BASE_HOST}"
      ROOT_URL: "https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
      SSH_DOMAIN: "git.${BASE_HOST}"
      HTTP_PORT: 3000          # 容器内部，不暴露主机
    service:
      DISABLE_REGISTRATION: true
    webhook:
      ALLOWED_HOST_LIST: "*"
    actions:
      ENABLED: true
ingress:
  enabled: true
  className: nginx
  hosts:
  - host: "git.${BASE_HOST}"
    paths: [{ path: /, pathType: Prefix }]
  tls:
  - secretName: gitea-tls
    hosts: [ "git.${BASE_HOST}" ]
resources:
  requests: { cpu: 100m, memory: 256Mi }
  limits:   { cpu: 1000m, memory: 768Mi }
nodeSelector: { kubernetes.io/hostname: node-02 }
EOF

helm install gitea gitea-charts/gitea -n gitea -f /tmp/gitea-values.yaml --version 10.1.0
kubectl -n gitea rollout status sts/gitea --timeout=600s
```

### 9.2 获取 Runner 注册 token 并部署 Runner

```bash
# 直接通过 Gitea DB 获取全局 runner 注册 token（避免手工 UI 操作）
RUNNER_TOKEN=$(kubectl -n gitea exec -it sts/gitea -c gitea -- \
  gitea actions generate-runner-token -c /data/gitea/conf/app.ini | tr -d '\r\n')
echo "Runner token: $RUNNER_TOKEN"

kubectl create namespace gitea-runner

cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata: { name: runner-token, namespace: gitea-runner }
stringData: { token: "$RUNNER_TOKEN" }
---
apiVersion: v1
kind: ConfigMap
metadata: { name: runner-config, namespace: gitea-runner }
data:
  config.yaml: |
    log:
      level: info
    runner:
      capacity: 2
      envs:
        HARBOR_REG: "harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
        ROBOT_USER: "${ROBOT_NAME}"
        ROBOT_SECRET: "${ROBOT_SECRET}"
    cache:
      enabled: false
    container:
      network: "host"
      privileged: false
      options: ""
      workdir_parent: ""
      valid_volumes: []
      docker_host: "-"
---
apiVersion: apps/v1
kind: StatefulSet
metadata: { name: act-runner, namespace: gitea-runner }
spec:
  serviceName: act-runner
  replicas: 1
  selector: { matchLabels: { app: act-runner } }
  template:
    metadata: { labels: { app: act-runner } }
    spec:
      nodeSelector: { kubernetes.io/hostname: node-03 }
      volumes:
      - name: docker-certs
        secret:
          secretName: docker-certs
          optional: true
      - name: ca
        configMap:
          name: ca-bundle
      containers:
      - name: runner
        image: gitea/act_runner:0.2.10
        env:
        - name: CONFIG_FILE
          value: /config/config.yaml
        - name: GITEA_INSTANCE_URL
          value: "https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
        - name: GITEA_RUNNER_REGISTRATION_TOKEN
          valueFrom: { secretKeyRef: { name: runner-token, key: token } }
        - name: GITEA_RUNNER_NAME
          value: "idc-runner-01"
        - name: GITEA_RUNNER_LABELS
          value: "idc-ctl"
        - name: SSL_CERT_DIR
          value: "/etc/ssl/certs:/ca"
        volumeMounts:
        - { name: docker-certs, mountPath: /certs }
        - { name: ca, mountPath: /ca }
        resources:
          requests: { cpu: 100m, memory: 256Mi }
          limits:   { cpu: 1000m, memory: 1Gi }
EOF

# 把 CA bundle 给 runner
kubectl -n gitea-runner create cm ca-bundle --from-file=ca.crt=/tmp/idc-ca.crt

kubectl -n gitea-runner rollout status sts/act-runner --timeout=300s
```

### 9.3 验证 P8

```bash
kubectl -n gitea get pod
# gitea-0 Running, gitea-postgresql-0 Running, gitea-redis-master-0 Running

kubectl -n gitea-runner logs sts/act-runner | tail -20
# 期望看到 "Runner registered" 或 "Runner connected"

# Gitea UI 可达性
curl -kI --resolve git.${BASE_HOST}:${INGRESS_HTTPS_PORT}:${LB_IP} \
  "https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/"
# 期望 200
```

**通过标准**：Gitea Pod 就绪；runner 注册成功；UI 可达。

---

## 10. 阶段 P9：ArgoCD

```bash
kubectl create namespace argocd

cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Certificate
metadata: { name: argocd-tls, namespace: argocd }
spec:
  secretName: argocd-server-tls
  issuerRef: { name: idc-ca-issuer, kind: ClusterIssuer }
  dnsNames: [ "argocd.${BASE_HOST}" ]
  duration: 8760h
EOF

helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

cat > /tmp/argocd-values.yaml <<EOF
global:
  domain: "argocd.${BASE_HOST}"
configs:
  params:
    server.insecure: true
  cm:
    url: "https://argocd.${BASE_HOST}:${INGRESS_HTTPS_PORT}"
    timeout.reconciliation: 60s
    application.instanceLabelKey: argocd.argoproj.io/instance
  rbac:
    policy.default: role:readonly
server:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 256Mi }
    limits:   { cpu: 1000m, memory: 512Mi }
  ingress:
    enabled: true
    controller: generic
    ingressClassName: nginx
    hostname: "argocd.${BASE_HOST}"
    tls: true
    annotations:
      nginx.ingress.kubernetes.io/backend-protocol: HTTP
  nodeSelector: { kubernetes.io/hostname: node-03 }
repoServer:
  replicas: 1
  resources: { requests: { cpu: 100m, memory: 256Mi }, limits: { cpu: 500m, memory: 512Mi } }
  nodeSelector: { kubernetes.io/hostname: node-03 }
  # 挂 CA 让 repo-server 能 clone 自签证书的 Gitea
  volumes:
  - name: ca-bundle
    configMap: { name: argocd-ca }
  volumeMounts:
  - { name: ca-bundle, mountPath: /etc/ssl/certs/idc-ca.crt, subPath: ca.crt }
applicationSet:
  replicas: 1
controller:
  replicas: 1
  resources: { requests: { cpu: 100m, memory: 256Mi }, limits: { cpu: 1000m, memory: 1Gi } }
  nodeSelector: { kubernetes.io/hostname: node-03 }
redis: { enabled: true }
dex: { enabled: false }
notifications: { enabled: false }
EOF

# 先创建 CA configmap
kubectl -n argocd create cm argocd-ca --from-file=ca.crt=/tmp/idc-ca.crt

# ingress-tls 用 argocd 内部命名，自动映射到 argocd-server-tls
kubectl -n argocd patch secret argocd-server-tls -p '{"metadata":{"labels":{"app.kubernetes.io/managed-by":"argocd"}}}' || true

helm install argocd argo/argo-cd -n argocd -f /tmp/argocd-values.yaml --version 6.7.10
kubectl -n argocd rollout status deploy/argocd-server --timeout=600s

# 改 ingress 指向自签证书（默认 helm 生成占位 secret）
kubectl -n argocd patch ingress argocd-server --type=json \
  -p "[{\"op\":\"replace\",\"path\":\"/spec/tls/0/secretName\",\"value\":\"argocd-server-tls\"}]"

# 获取初始 admin 密码
ARGOCD_PWD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)
echo "ArgoCD admin password: $ARGOCD_PWD"
echo "export ARGOCD_PWD=\"$ARGOCD_PWD\"" >> $STATE_FILE
```

### 10.1 验证 P9

```bash
kubectl -n argocd get pod
# argocd-server / repo-server / application-controller / applicationset / redis 全 Running

curl -kI --resolve argocd.${BASE_HOST}:${INGRESS_HTTPS_PORT}:${LB_IP} \
  "https://argocd.${BASE_HOST}:${INGRESS_HTTPS_PORT}/"
# 期望 200
```

**通过标准**：Pod 全 Running；UI 可达。

---

## 11. 阶段 P10：准备示例应用 + 两个 Git 仓库

### 11.1 在 Gitea 里创建 calendar（应用代码）和 gitops（部署描述）仓库

```bash
GT_API="https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/api/v1"
GT_AUTH="-k -u gitea_admin:Gitea12345 -H 'Content-Type: application/json'"

# 先用 admin 生成一个 PAT 给 CI 用
eval curl $GT_AUTH -X POST "$GT_API/users/gitea_admin/tokens" \
  -d '"{\"name\":\"ci\",\"scopes\":[\"write:repository\",\"write:user\"]}"' \
  > /tmp/gt-token.json
GITEA_TOKEN=$(jq -r .sha1 /tmp/gt-token.json)
echo "Gitea CI token: $GITEA_TOKEN"

# 持久化
echo "export GITEA_TOKEN=\"$GITEA_TOKEN\"" >> $STATE_FILE

# 创建组织 calendar-org
eval curl $GT_AUTH -X POST "$GT_API/orgs" \
  -d '"{\"username\":\"calendar-org\",\"full_name\":\"Calendar Org\"}"'

# 创建 calendar (应用代码) 和 gitops (部署描述)
for repo in calendar gitops; do
  eval curl $GT_AUTH -X POST "$GT_API/orgs/calendar-org/repos" \
    -d '"{\"name\":\"'$repo'\",\"auto_init\":true,\"default_branch\":\"main\"}"'
done
```

### 11.2 最小示例应用 calendar-hello（Go，5 秒启动）

```bash
WORKDIR=/tmp/calendar-work && rm -rf $WORKDIR && mkdir -p $WORKDIR
cd $WORKDIR
git clone "https://gitea_admin:${GITEA_TOKEN}@git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/calendar.git" app
cd app

cat > main.go <<'EOF'
package main

import (
  "fmt"
  "net/http"
  "os"
)
func main() {
  v := os.Getenv("APP_VERSION")
  if v == "" { v = "dev" }
  http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
    fmt.Fprintf(w, "hello from calendar %s\n", v)
  })
  http.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(200); w.Write([]byte("ok"))
  })
  http.ListenAndServe(":8000", nil)
}
EOF

cat > go.mod <<'EOF'
module calendar-hello
go 1.22
EOF

cat > Dockerfile <<'EOF'
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -o /out/calendar-hello .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/calendar-hello /calendar-hello
EXPOSE 8000
ENTRYPOINT ["/calendar-hello"]
EOF

# Gitea Actions workflow
mkdir -p .gitea/workflows
cat > .gitea/workflows/ci.yaml <<EOF
name: ci
on:
  push:
    branches: [main]
jobs:
  build-deploy:
    runs-on: idc-ctl
    steps:
    - uses: actions/checkout@v4

    - name: Set version
      id: ver
      run: echo "VERSION=\$(date +%Y%m%d-%H%M%S)-\${GITHUB_SHA::8}" >> \$GITHUB_ENV

    - name: Kaniko build & push
      uses: https://gitea.com/alexxa/kaniko-action@v1.1
      with:
        context: .
        image: "${HARBOR_REG}/calendar/calendar-hello:\${{ env.VERSION }}"
        extra_args: |
          --destination=${HARBOR_REG}/calendar/calendar-hello:latest
          --cache=true
          --cache-repo=${HARBOR_REG}/calendar/cache
          --skip-tls-verify-pull=false
          --skip-tls-verify-registry=false
        registry_username: "${ROBOT_NAME}"
        registry_password: "${ROBOT_SECRET}"

    - name: Bump gitops repo
      run: |
        git config --global user.email "ci@idc.local"
        git config --global user.name "ci-bot"
        git clone "https://gitea_admin:${GITEA_TOKEN}@git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/gitops.git" /tmp/gitops
        cd /tmp/gitops
        yq e -i ".image.tag = \"\${{ env.VERSION }}\"" charts/calendar-hello/values.yaml
        git add -A
        git commit -m "chore(calendar-hello): bump to \${{ env.VERSION }}" || exit 0
        git push
EOF

git add -A
git commit -m "init calendar-hello"
git push origin main
```

### 11.3 gitops 仓库初始内容（Helm Chart）

```bash
cd $WORKDIR
git clone "https://gitea_admin:${GITEA_TOKEN}@git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/gitops.git"
cd gitops

mkdir -p charts/calendar-hello/templates
cat > charts/calendar-hello/Chart.yaml <<EOF
apiVersion: v2
name: calendar-hello
version: 0.1.0
EOF

cat > charts/calendar-hello/values.yaml <<EOF
replicaCount: 2
image:
  repository: ${HARBOR_REG}/calendar/calendar-hello
  tag: latest
  pullPolicy: Always
imagePullSecrets:
- name: harbor-robot
service:
  type: ClusterIP
  port: 8000
ingress:
  enabled: true
  className: nginx
  host: "hello.${BASE_HOST}"
resources:
  requests: { cpu: 50m, memory: 64Mi }
  limits:   { cpu: 200m, memory: 128Mi }
EOF

cat > charts/calendar-hello/templates/deploy.yaml <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: calendar-hello
spec:
  replicas: {{ .Values.replicaCount }}
  selector: { matchLabels: { app: calendar-hello } }
  template:
    metadata:
      labels: { app: calendar-hello }
      annotations:
        config-hash: "{{ .Values.image.tag }}"
    spec:
      imagePullSecrets:
      {{- toYaml .Values.imagePullSecrets | nindent 6 }}
      containers:
      - name: app
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        env:
        - name: APP_VERSION
          value: "{{ .Values.image.tag }}"
        ports:
        - containerPort: 8000
        readinessProbe:
          httpGet: { path: /healthz, port: 8000 }
          initialDelaySeconds: 2
          periodSeconds: 5
        livenessProbe:
          httpGet: { path: /healthz, port: 8000 }
          initialDelaySeconds: 10
          periodSeconds: 10
        resources:
          {{- toYaml .Values.resources | nindent 10 }}
---
apiVersion: v1
kind: Service
metadata: { name: calendar-hello }
spec:
  selector: { app: calendar-hello }
  ports:
  - { port: {{ .Values.service.port }}, targetPort: 8000 }
{{- if .Values.ingress.enabled }}
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: calendar-hello
spec:
  ingressClassName: {{ .Values.ingress.className }}
  rules:
  - host: {{ .Values.ingress.host }}
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: calendar-hello
            port: { number: {{ .Values.service.port }} }
{{- end }}
EOF

git add -A
git commit -m "add calendar-hello chart"
git push origin main
```

---

## 12. 阶段 P11：ArgoCD 注册 Repo + Cluster + 创建 Application

### 12.1 安装 argocd CLI

```bash
curl -sSL -o /usr/local/bin/argocd \
  https://github.com/argoproj/argo-cd/releases/download/v2.10.7/argocd-linux-amd64
chmod +x /usr/local/bin/argocd

argocd login argocd.${BASE_HOST}:${INGRESS_HTTPS_PORT} \
  --insecure --grpc-web \
  --username admin --password "$ARGOCD_PWD"
```

### 12.2 注册 Gitea 仓库（Git Repo）

```bash
argocd repo add "https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/gitops.git" \
  --username gitea_admin \
  --password "$GITEA_TOKEN" \
  --insecure-skip-server-verification
```

### 12.3 在 calendar-dev namespace 里放 Harbor pull secret

```bash
kubectl create namespace calendar-dev
kubectl -n calendar-dev create secret docker-registry harbor-robot \
  --docker-server="https://${HARBOR_REG}" \
  --docker-username="${ROBOT_NAME}" \
  --docker-password="${ROBOT_SECRET}"
```

### 12.4 创建 Application

```bash
cat <<EOF | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: calendar-hello
  namespace: argocd
  finalizers: [resources-finalizer.argocd.argoproj.io]
spec:
  project: default
  source:
    repoURL: "https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/gitops.git"
    targetRevision: main
    path: charts/calendar-hello
    helm: { releaseName: calendar-hello }
  destination:
    server: https://kubernetes.default.svc
    namespace: calendar-dev
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
    - CreateNamespace=false
    retry:
      limit: 5
      backoff: { duration: 10s, factor: 2, maxDuration: 3m }
EOF

# 触发 sync
argocd app sync calendar-hello --grpc-web || true
argocd app wait calendar-hello --grpc-web --timeout 300
```

### 12.5 验证 P11

```bash
kubectl -n calendar-dev get pod
# calendar-hello-XXX Running (2 replica)

curl --resolve hello.${BASE_HOST}:${INGRESS_HTTP_PORT}:${LB_IP} \
  "http://hello.${BASE_HOST}:${INGRESS_HTTP_PORT}/"
# 期望：hello from calendar latest  （或具体 tag）
```

**通过标准**：Pod 都 Running；curl 返回 hello。

---

## 13. 阶段 P12：端到端闭环 Smoke Test

> 这一步是验收核心。执行后用户 `git push` 应该触发全链路。

```bash
cd $WORKDIR/app

# 修改代码（改个字符串即可）
sed -i 's|hello from calendar %s|CALENDAR v2 says hi (%s)|' main.go
git commit -am "feat: change greeting"
git push origin main

echo "已推送。现在观察链路："
echo "1. Gitea Actions (https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/calendar/actions) 应出现绿灯 job"
echo "2. Harbor 会出现新 tag：https://harbor.${BASE_HOST}:${INGRESS_HTTPS_PORT}/harbor/projects/"
echo "3. gitops 仓库会看到 ci-bot 的 commit：https://git.${BASE_HOST}:${INGRESS_HTTPS_PORT}/calendar-org/gitops/commits/branch/main"
echo "4. ArgoCD 自动 sync 并滚动 Pod"
```

### 13.1 观察闭环

```bash
# 轮询直到 curl 返回新字符串
for i in $(seq 1 30); do
  OUT=$(curl -s --resolve hello.${BASE_HOST}:${INGRESS_HTTP_PORT}:${LB_IP} \
    "http://hello.${BASE_HOST}:${INGRESS_HTTP_PORT}/")
  echo "[$i] $OUT"
  if echo "$OUT" | grep -q "CALENDAR v2"; then
    echo "==> 闭环验证通过！"
    break
  fi
  sleep 20
done
```

**通过标准**：`/` 响应从 `hello from calendar` 变为 `CALENDAR v2 says hi`。完整链路：`git push → Gitea Actions → Kaniko → Harbor → gitops commit → ArgoCD → K8s rollout`。

---

## 14. 阶段 P13：Rancher（可选，做统一运维门户）

> 非闭环必需。如果想在一个 UI 里管集群、看日志、执行 kubectl，装 Rancher：

```bash
kubectl create namespace cattle-system

cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Certificate
metadata: { name: rancher-tls, namespace: cattle-system }
spec:
  secretName: rancher-tls
  issuerRef: { name: idc-ca-issuer, kind: ClusterIssuer }
  dnsNames: [ "rancher.${BASE_HOST}" ]
  duration: 8760h
EOF

helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
helm repo update

cat > /tmp/rancher-values.yaml <<EOF
hostname: "rancher.${BASE_HOST}"
replicas: 1
bootstrapPassword: "Rancher12345"
ingress:
  tls: { source: secret, secretName: rancher-tls }
  extraAnnotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "0"
resources:
  requests: { cpu: 500m, memory: 1Gi }
  limits:   { cpu: 2000m, memory: 2Gi }
nodeSelector: { kubernetes.io/hostname: node-01 }
auditLog: { level: 1, maxAge: 7 }
EOF

helm install rancher rancher-stable/rancher -n cattle-system \
  -f /tmp/rancher-values.yaml --version 2.8.3
kubectl -n cattle-system rollout status deploy/rancher --timeout=600s
```

访问：`https://rancher.<LB_IP>.nip.io:18443`，口令 `Rancher12345`。

---

## 15. 阶段 P14：附录 — DNS / 证书 / 端口 对照表

### 15.1 所有对外 URL

| 服务 | URL |
|---|---|
| Rancher | `https://rancher.${LB_IP}.nip.io:18443` |
| Harbor  | `https://harbor.${LB_IP}.nip.io:18443` |
| Gitea   | `https://git.${LB_IP}.nip.io:18443` |
| ArgoCD  | `https://argocd.${LB_IP}.nip.io:18443` |
| Demo 应用 | `http://hello.${LB_IP}.nip.io:18080` |

### 15.2 开发者本地访问

开发者机器不出公网时，给 hosts 加一条：

```bash
# /etc/hosts (Mac/Linux) 或 C:\Windows\System32\drivers\etc\hosts (Win)
<LB_IP>   rancher.<LB_IP>.nip.io harbor.<LB_IP>.nip.io git.<LB_IP>.nip.io argocd.<LB_IP>.nip.io hello.<LB_IP>.nip.io
```

### 15.3 导入 CA 到开发者机器

```bash
# 从执行机拉 CA（或任一节点 /usr/local/share/ca-certificates/idc-ca.crt）
scp root@<NODE1_IP>:/usr/local/share/ca-certificates/idc-ca.crt .

# macOS
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain idc-ca.crt

# Linux (Ubuntu/Debian)
sudo cp idc-ca.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates

# Windows (PowerShell 管理员)
# Import-Certificate -FilePath .\idc-ca.crt -CertStoreLocation Cert:\LocalMachine\Root
```

### 15.4 如果机房无法访问 nip.io（完全内网）

部署 dnsmasq 在 node-01，为所有节点提供 wildcard 解析：

```bash
sshpass -p "$ROOT_PASSWORD" ssh root@$NODE1_IP '
  apt-get install -y dnsmasq
  cat > /etc/dnsmasq.d/idc.conf <<EOF
port=5353
address=/.'${BASE_HOST}'/'${LB_IP}'
EOF
  systemctl restart dnsmasq
'

# 所有节点 /etc/systemd/resolved.conf 加
# DNS=NODE1_IP:5353
```

---

## 16. 阶段 P15：回滚与恢复

### 16.1 某一阶段失败的回滚

| 阶段 | 回滚命令 |
|---|---|
| P0 OS 初始化失败 | 重装 OS，重跑 node-init.sh |
| P2 kubeadm init 失败 | `kubeadm reset -f && rm -rf /etc/cni/net.d /var/lib/kubelet && systemctl restart containerd kubelet` 然后重跑 P2 |
| P3 Calico 失败 | `kubectl delete -f tigera-operator.yaml` 后重装 |
| P4-P13 Helm 失败 | `helm uninstall <rel> -n <ns>` + `kubectl delete ns <ns>` 后重装 |
| P12 ArgoCD 同步失败 | `argocd app delete calendar-hello`, 确认 gitops 仓库路径无误，重跑 12.4 |

### 16.2 完整重置（核按钮）

```bash
for ip in $NODE1_IP $NODE2_IP $NODE3_IP; do
  sshpass -p "$ROOT_PASSWORD" ssh root@$ip '
    kubeadm reset -f
    rm -rf /etc/cni/net.d /var/lib/kubelet /var/lib/longhorn/* /etc/kubernetes
    iptables -F; iptables -t nat -F; iptables -t mangle -F; iptables -X
    systemctl restart containerd
  '
done
```

---

## 17. 最终验收清单（P0 → P13）

| # | 阶段 | 验证命令 | 通过标准 |
|---|---|---|---|
| 1 | P0 | 三节点 hostname/swap/containerd/kubelet 状态 | 全绿 |
| 2 | P1 | `ping $VIP` | 通 |
| 3 | P2 | `kubectl get nodes` | 3 个 Ready（装 CNI 后） |
| 4 | P3 | `kubectl get tigerastatus` | 全 Available=True |
| 5 | P4 | `kubectl get sc` | longhorn (default) 存在 |
| 6 | P5 | `kubectl -n ingress-nginx get svc` | EXTERNAL-IP=$LB_IP，端口 18080/18443 |
| 7 | P6 | `kubectl -n cert-manager get certificate` | idc-root-ca READY=True |
| 8 | P7 | `docker login $HARBOR_REG -u admin -p Harbor12345` | Login Succeeded |
| 9 | P8 | `kubectl -n gitea-runner logs sts/act-runner` | 出现 `Runner registered` |
| 10 | P9 | `argocd version --grpc-web` | 客户端连接成功 |
| 11 | P10 | `git push origin main`（first commit） | 两个仓库就绪 |
| 12 | P11 | `argocd app get calendar-hello` | Status=Synced, Health=Healthy |
| 13 | P12 | `curl http://hello.$LB_IP.nip.io:18080` | 返回 hello |
| 14 | P13 | 改代码 push 后再次 curl | 内容变为 v2 |

**14/14 通过** = 闭环打通。此时用户在本机 clone `git.$LB_IP.nip.io:18443/calendar-org/calendar.git`，改代码 push，环境自动部署。

---

## 18. 与主方案的差异说明

本 Runbook 相对主方案（企业级云原生平台落地方案_IDC3节点_kubeadm_阿里云Spot混合.md）的调整：

1. **端口**：ingress-nginx 从 80/443 改为 **18080/18443**（避开禁用端口）。
2. **DNS**：从占位 `cal.example.com` 改为 **`<LB_IP>.nip.io`**（零 DNS 配置，即开即用）。
3. **CA 信任链**：显式把自签 CA 分发到 containerd `/etc/containerd/certs.d/<host>_<port>/`，解决 Kaniko 推/节点拉镜像的信任问题。
4. **VIP 提前**：在 kubeadm init 之前部署 keepalived，避免控制面单点。
5. **示例应用**：内置一个 Go hello world，用于验证闭环（主方案只描述了 Calendar 微服务但没给最小跑通用例）。
6. **完整 Git/ArgoCD 凭证链**：显式通过 API 创建 Gitea PAT + Harbor Robot + ArgoCD Repo 注册，全程无需 UI 操作。

数据面 (PG/Redis/MQ/ES/Nacos) 和阿里云 Spot 突发集群未在本 Runbook 覆盖 —— 在 P13 闭环验证通过后，按主方案 §16-§24 继续补齐即可。

---

**文档版本**：v1.0.0 · 2026-04-20
**适用 K8s 版本**：v1.28.10
**执行预计耗时**（网络良好情况下）：P0-P2 ≈ 30 min，P3-P5 ≈ 20 min，P6-P9 ≈ 30 min，P10-P13 ≈ 20 min，**合计约 100 min**。
