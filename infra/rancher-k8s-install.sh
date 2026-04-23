#!/usr/bin/env bash
###############################################################################
# Rancher + K8s 一键安装脚本
#
# 架构：
#   - Node1 (112.33.254.8)   : Rancher Server (Docker) + etcd/controlplane/worker
#   - Node2 (112.33.254.145) : etcd/controlplane/worker
#   - Node3 (112.33.254.64)  : etcd/controlplane/worker
#
# 端口映射（避开 80/443/8080/8443）：
#   - Rancher HTTP  : 8880  (容器内 80)
#   - Rancher HTTPS : 9443  (容器内 443)
#
# 使用方法：
#   1. 在一台能 SSH 到三台节点的机器上（本地或跳板机）保存此脚本
#   2. chmod +x rancher-k8s-install.sh
#   3. ./rancher-k8s-install.sh
#
# 依赖：本机需要 sshpass / curl / jq（脚本会尝试自动安装）
#
# 完成后浏览器访问:  https://112.33.254.8:9443
#   用户名 admin  /  密码见下方 ADMIN_PASSWORD
###############################################################################

set -euo pipefail

###############################################################################
# 可修改配置
###############################################################################
NODE1_IP="112.33.254.8";    NODE1_PASS='Win2009@'
NODE2_IP="112.33.254.145";  NODE2_PASS='Win2009@'
NODE3_IP="112.33.254.64";   NODE3_PASS='Win2009@'

RANCHER_HTTP_PORT=8880
RANCHER_HTTPS_PORT=9443
RANCHER_VERSION="v2.8.5"              # v2.8.x 仍原生支持 RKE1 custom 集群
CLUSTER_NAME="longfeng-prod"
ADMIN_PASSWORD='Rancher@Longfeng2026' # 首次登录后会替换默认 bootstrap 密码

RANCHER_URL="https://${NODE1_IP}:${RANCHER_HTTPS_PORT}"

###############################################################################
# 工具函数
###############################################################################
log()  { echo -e "\n\033[1;32m==> $*\033[0m"; }
warn() { echo -e "\033[1;33m[!] $*\033[0m"; }
die()  { echo -e "\033[1;31m[x] $*\033[0m"; exit 1; }

ensure_local_tools() {
  for cmd in sshpass curl jq; do
    if ! command -v "$cmd" &>/dev/null; then
      warn "本机缺少 $cmd，尝试安装..."
      if   command -v apt-get &>/dev/null; then sudo apt-get update -y && sudo apt-get install -y "$cmd"
      elif command -v yum     &>/dev/null; then sudo yum install -y "$cmd"
      elif command -v brew    &>/dev/null; then brew install "$cmd"
      else die "请手动安装 $cmd 后重试"
      fi
    fi
  done
}

ssh_exec() {
  local ip="$1" pass="$2" cmd="$3"
  sshpass -p "$pass" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o ConnectTimeout=15 -o ServerAliveInterval=30 root@"$ip" "$cmd"
}

ssh_run_stdin() {
  local ip="$1" pass="$2"
  sshpass -p "$pass" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o ConnectTimeout=15 -o ServerAliveInterval=30 root@"$ip" "bash -s"
}

###############################################################################
# Step 1: 节点系统预处理
###############################################################################
prep_node() {
  local ip="$1" pass="$2" hostname="$3"
  log "预处理节点 ${hostname} (${ip})"
  ssh_run_stdin "$ip" "$pass" <<EOF
set -e
# 关防火墙
systemctl stop firewalld 2>/dev/null || true
systemctl disable firewalld 2>/dev/null || true
systemctl stop ufw 2>/dev/null || true
systemctl disable ufw 2>/dev/null || true

# SELinux
if command -v setenforce >/dev/null 2>&1; then
  setenforce 0 2>/dev/null || true
  sed -i 's/^SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
fi

# 关 swap
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

# 内核模块
cat > /etc/modules-load.d/k8s.conf <<MOD
overlay
br_netfilter
MOD
modprobe overlay
modprobe br_netfilter

# sysctl
cat > /etc/sysctl.d/k8s.conf <<SYS
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
fs.inotify.max_user_watches         = 524288
fs.inotify.max_user_instances       = 512
SYS
sysctl --system >/dev/null

# 主机名
hostnamectl set-hostname ${hostname}

# /etc/hosts
sed -i '/ node[123]$/d' /etc/hosts
cat >> /etc/hosts <<HOSTS
${NODE1_IP} node1
${NODE2_IP} node2
${NODE3_IP} node3
HOSTS

# 时间同步
if command -v yum     >/dev/null 2>&1; then yum install -y chrony >/dev/null 2>&1 || true; fi
if command -v apt-get >/dev/null 2>&1; then apt-get update -y >/dev/null 2>&1 && apt-get install -y chrony >/dev/null 2>&1 || true; fi
systemctl enable --now chronyd 2>/dev/null || systemctl enable --now chrony 2>/dev/null || true

echo "[OK] ${hostname} ready"
EOF
}

###############################################################################
# Step 2: 安装 Docker
###############################################################################
install_docker() {
  local ip="$1" pass="$2"
  log "安装 Docker @ ${ip}"
  ssh_run_stdin "$ip" "$pass" <<'EOF'
set -e
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "Docker 已安装，跳过"
else
  # IDC 节点无法访问 get.docker.com，走 apt 官方源
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y docker.io
fi

mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<DAEMON
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.mirrors.sjtug.sjtu.edu.cn",
    "https://docker.nju.edu.cn"
  ],
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {"max-size": "100m", "max-file": "3"},
  "storage-driver": "overlay2"
}
DAEMON

systemctl daemon-reload
systemctl enable --now docker
systemctl restart docker
docker version
EOF
}

###############################################################################
# Step 3: 启动 Rancher Server
###############################################################################
start_rancher() {
  log "启动 Rancher Server 容器 @ Node1 (HTTP=${RANCHER_HTTP_PORT}, HTTPS=${RANCHER_HTTPS_PORT})"
  ssh_run_stdin "$NODE1_IP" "$NODE1_PASS" <<EOF
set -e
mkdir -p /opt/rancher
if docker ps -a --format '{{.Names}}' | grep -qx 'rancher'; then
  echo "Rancher 容器已存在，先删除重建以确保端口正确"
  docker rm -f rancher
fi

MIRROR_IMG="m.daocloud.io/docker.io/rancher/rancher:${RANCHER_VERSION}"
echo "--> 从国内镜像拉取 Rancher 镜像..."
docker pull "\${MIRROR_IMG}" \
  || docker pull "rancher/rancher:${RANCHER_VERSION}"
docker tag "\${MIRROR_IMG}" "rancher/rancher:${RANCHER_VERSION}" 2>/dev/null || true

docker run -d --restart=unless-stopped \\
  --name rancher \\
  -p ${RANCHER_HTTP_PORT}:80 \\
  -p ${RANCHER_HTTPS_PORT}:443 \\
  --privileged \\
  -v /opt/rancher:/var/lib/rancher \\
  -e CATTLE_BOOTSTRAP_PASSWORD=admin \\
  -e CATTLE_SERVER_URL=${RANCHER_URL} \\
  rancher/rancher:${RANCHER_VERSION}

docker ps --filter name=rancher
EOF
}

###############################################################################
# Step 4: 等待 Rancher 就绪
###############################################################################
wait_rancher() {
  log "等待 Rancher 启动 (最多 10 分钟)"
  local retries=60
  while (( retries-- > 0 )); do
    if curl -sk --max-time 5 "$RANCHER_URL/ping" | grep -q pong; then
      echo "Rancher 已就绪"
      return 0
    fi
    printf '.'
    sleep 10
  done
  die "Rancher 启动超时，请在 Node1 上 'docker logs rancher' 排查"
}

###############################################################################
# Step 5: 配置 Rancher (设置密码 / server-url / EULA)
###############################################################################
configure_rancher() {
  log "初始化 Rancher (修改密码 / 设置 server-url)"

  local resp token
  resp=$(curl -sk "$RANCHER_URL/v3-public/localProviders/local?action=login" \
    -H 'content-type: application/json' \
    -d '{"username":"admin","password":"admin"}')
  token=$(echo "$resp" | jq -r .token)

  if [[ -z "$token" || "$token" == "null" ]]; then
    warn "bootstrap 密码 'admin' 登录失败，尝试从日志提取"
    local bs
    bs=$(ssh_exec "$NODE1_IP" "$NODE1_PASS" "docker logs rancher 2>&1 | grep 'Bootstrap Password:' | tail -1 | awk '{print \$NF}'")
    [[ -z "$bs" ]] && die "无法获取 Rancher bootstrap 密码"
    resp=$(curl -sk "$RANCHER_URL/v3-public/localProviders/local?action=login" \
      -H 'content-type: application/json' \
      -d "{\"username\":\"admin\",\"password\":\"${bs}\"}")
    token=$(echo "$resp" | jq -r .token)
    local old_pwd="$bs"
  else
    local old_pwd="admin"
  fi

  # 改密码
  curl -sk "$RANCHER_URL/v3/users?action=changepassword" \
    -H "Authorization: Bearer $token" \
    -H 'content-type: application/json' \
    -d "{\"currentPassword\":\"${old_pwd}\",\"newPassword\":\"${ADMIN_PASSWORD}\"}" >/dev/null || true

  # 用新密码重新登录
  resp=$(curl -sk "$RANCHER_URL/v3-public/localProviders/local?action=login" \
    -H 'content-type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${ADMIN_PASSWORD}\"}")
  API_TOKEN=$(echo "$resp" | jq -r .token)
  [[ -z "$API_TOKEN" || "$API_TOKEN" == "null" ]] && die "Rancher 登录失败"

  # 设置 server-url
  curl -sk -X PUT "$RANCHER_URL/v3/settings/server-url" \
    -H "Authorization: Bearer $API_TOKEN" \
    -H 'content-type: application/json' \
    -d "{\"name\":\"server-url\",\"value\":\"${RANCHER_URL}\"}" >/dev/null

  # EULA
  curl -sk -X PUT "$RANCHER_URL/v3/settings/eula-agreed" \
    -H "Authorization: Bearer $API_TOKEN" \
    -H 'content-type: application/json' \
    -d "{\"name\":\"eula-agreed\",\"value\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}" >/dev/null || true

  # 关 telemetry
  curl -sk -X PUT "$RANCHER_URL/v3/settings/telemetry-opt" \
    -H "Authorization: Bearer $API_TOKEN" \
    -H 'content-type: application/json' \
    -d '{"name":"telemetry-opt","value":"out"}' >/dev/null || true

  log "Rancher 配置完成，Admin Token 已获取"
}

###############################################################################
# Step 6: 通过 API 创建 RKE1 Custom 集群
###############################################################################
create_cluster() {
  log "创建自定义集群 ${CLUSTER_NAME}"

  # 如已存在同名集群则复用
  local exist
  exist=$(curl -sk "$RANCHER_URL/v3/clusters?name=${CLUSTER_NAME}" \
    -H "Authorization: Bearer $API_TOKEN" | jq -r '.data[0].id // empty')

  if [[ -n "$exist" ]]; then
    warn "检测到同名集群 ${CLUSTER_NAME} (id=${exist})，将复用"
    CLUSTER_ID="$exist"
  else
    local resp
    resp=$(curl -sk "$RANCHER_URL/v3/cluster" \
      -H "Authorization: Bearer $API_TOKEN" \
      -H 'content-type: application/json' \
      -d "{
        \"type\": \"cluster\",
        \"name\": \"${CLUSTER_NAME}\",
        \"description\": \"Longfeng prod cluster installed via script\",
        \"rancherKubernetesEngineConfig\": {
          \"type\": \"rancherKubernetesEngineConfig\",
          \"network\":  { \"plugin\": \"canal\" },
          \"services\": {
            \"etcd\": {
              \"snapshot\": true,
              \"creation\": \"6h0s\",
              \"retention\": \"72h\"
            }
          },
          \"ingress\": { \"provider\": \"nginx\" }
        }
      }")
    CLUSTER_ID=$(echo "$resp" | jq -r .id)
    [[ -z "$CLUSTER_ID" || "$CLUSTER_ID" == "null" ]] && \
      die "创建集群失败: $(echo "$resp" | jq -r '.message // .')"
    echo "集群创建成功: id=${CLUSTER_ID}"
    sleep 5
  fi

  # 取 nodeCommand（最多等 60s）
  local retries=12
  while (( retries-- > 0 )); do
    local q
    q=$(curl -sk "$RANCHER_URL/v3/clusterregistrationtokens?clusterId=${CLUSTER_ID}" \
      -H "Authorization: Bearer $API_TOKEN")
    NODE_CMD=$(echo "$q" | jq -r '.data[0].nodeCommand // empty')
    [[ -n "$NODE_CMD" ]] && break

    # 若不存在则主动创建一个注册 token
    curl -sk "$RANCHER_URL/v3/clusterregistrationtokens" \
      -H "Authorization: Bearer $API_TOKEN" \
      -H 'content-type: application/json' \
      -d "{\"type\":\"clusterRegistrationToken\",\"clusterId\":\"${CLUSTER_ID}\"}" >/dev/null 2>&1 || true
    sleep 5
  done

  [[ -z "${NODE_CMD:-}" ]] && die "无法取到节点注册命令 (nodeCommand)"
  echo "节点注册命令已获取"
}

###############################################################################
# Step 7: 注册三个节点（全部勾选 etcd + controlplane + worker）
###############################################################################
register_node() {
  local ip="$1" pass="$2" hn="$3"
  log "注册节点 ${hn} (${ip})"
  local full_cmd="${NODE_CMD} --address ${ip} --internal-address ${ip} --etcd --controlplane --worker"
  # 首先移除可能已有的 agent 容器
  ssh_exec "$ip" "$pass" "docker rm -f rancher-agent 2>/dev/null || true; docker rm -f \$(docker ps -aq --filter name=share-mnt) 2>/dev/null || true"
  ssh_exec "$ip" "$pass" "$full_cmd"
}

###############################################################################
# Step 8: 等待集群 Active
###############################################################################
wait_cluster_active() {
  log "等待集群 provision 完成（预计 10~15 分钟）"
  local retries=90 state transition
  while (( retries-- > 0 )); do
    local info
    info=$(curl -sk "$RANCHER_URL/v3/clusters/${CLUSTER_ID}" \
      -H "Authorization: Bearer $API_TOKEN")
    state=$(echo "$info" | jq -r .state)
    transition=$(echo "$info" | jq -r .transitioningMessage)
    printf "  state=%s  msg=%s\n" "$state" "$transition"
    [[ "$state" == "active" ]] && { log "集群已 Active ✅"; return 0; }
    sleep 20
  done
  warn "集群未在预期时间内 Active，请在 Rancher UI 查看每个节点的错误信息"
}

###############################################################################
# 主流程
###############################################################################
main() {
  ensure_local_tools

  log "== Step 1 / 7 : 预处理三台节点 =="
  prep_node "$NODE1_IP" "$NODE1_PASS" "node1"
  prep_node "$NODE2_IP" "$NODE2_PASS" "node2"
  prep_node "$NODE3_IP" "$NODE3_PASS" "node3"

  log "== Step 2 / 7 : 安装 Docker =="
  install_docker "$NODE1_IP" "$NODE1_PASS"
  install_docker "$NODE2_IP" "$NODE2_PASS"
  install_docker "$NODE3_IP" "$NODE3_PASS"

  log "== Step 3 / 7 : 启动 Rancher Server =="
  start_rancher
  wait_rancher

  log "== Step 4 / 7 : 初始化 Rancher =="
  configure_rancher

  log "== Step 5 / 7 : 创建 K8s 集群 =="
  create_cluster

  log "== Step 6 / 7 : 注册节点 =="
  register_node "$NODE1_IP" "$NODE1_PASS" "node1"
  register_node "$NODE2_IP" "$NODE2_PASS" "node2"
  register_node "$NODE3_IP" "$NODE3_PASS" "node3"

  log "== Step 7 / 7 : 等待集群就绪 =="
  wait_cluster_active

  cat <<INFO

==============================================================
  🎉  Rancher + Kubernetes 安装完成
--------------------------------------------------------------
  Rancher UI   : ${RANCHER_URL}
  用户名       : admin
  密码         : ${ADMIN_PASSWORD}
  集群名称     : ${CLUSTER_NAME}
  集群 ID      : ${CLUSTER_ID}
  节点角色     : 3 台节点均为 etcd + controlplane + worker
--------------------------------------------------------------
  下一步:
    1) 浏览器打开 ${RANCHER_URL} (证书自签，忽略告警继续)
    2) 左上角 → Cluster Management → ${CLUSTER_NAME} 查看
    3) 下载 kubeconfig 即可本地 kubectl 操作
    4) 安全提醒: 请尽快在三台节点上 passwd 修改 root 密码
==============================================================
INFO
}

main "$@"
