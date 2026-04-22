# 企业级云原生平台落地方案 — Rancher + Kubernetes + Helm + Harbor

> **承接业务**：通用日历系统（Java / Spring Cloud Alibaba 微服务、PostgreSQL 16、Redis Cluster、RocketMQ、Nacos、Elasticsearch、XXL-Job）
> **目标**：一套**企业级、专业、可落地**的云原生底座 + GitOps 化的 CI/CD 流水线，支撑日历系统从开发到生产的完整生命周期，可承载百万 DAU。
> **设计基线**：HA、可扩展、可观测、可审计、可回滚、可灾备、最小权限。

---

## 0. 总体架构概览

### 0.1 平面分层

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          研发协作 / GitOps 入口                          │
│  GitLab CE  +  ArgoCD  +  Argo Rollouts  +  Argo Image Updater          │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────┴──────────────────────────────────────┐
│                      管理平面 (Management Cluster)                       │
│   Rancher Manager (HA, 3 nodes)   ·   Harbor (HA)   ·   ChartMuseum     │
│   Vault (HA)   ·   Nexus (Maven mirror)   ·   GitLab Runner Manager     │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ Rancher Agent
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌──────────────┐          ┌──────────────┐           ┌──────────────┐
│  DEV Cluster │          │ STG Cluster  │           │ PROD Cluster │
│  RKE2 (3+5)  │          │ RKE2 (3+8)   │           │ RKE2 (3+12)  │
│  + Calico    │          │ + Calico     │           │ + Cilium eBPF│
│  + Longhorn  │          │ + Longhorn   │           │ + Rook-Ceph  │
└──────────────┘          └──────────────┘           └──────────────┘
```

### 0.2 集群定位与角色

| 集群 | 用途 | 节点规模（建议） | SLA | 数据持久化 |
|------|------|------------------|-----|-----------|
| `mgmt-cluster` | Rancher / Harbor / GitLab / Vault / ArgoCD | 3 master + 3 worker（每节点 8C/16G/200G SSD） | 99.9% | Longhorn 3 副本 |
| `dev-cluster`  | 开发自测、特性分支预览 | 3 master + 5 worker（8C/16G/200G） | 99% | Longhorn 2 副本 |
| `stg-cluster`  | 预发 / 集成测试 / 性能压测 | 3 master + 8 worker（16C/32G/500G） | 99.5% | Longhorn 3 副本 |
| `prod-cluster` | 生产 | 3 master + 12 worker（32C/64G/1T NVMe） | 99.95% | Rook-Ceph 3 副本 + 异地备份 |

> 总硬件清单与采购指引见附录 A。

### 0.3 核心组件版本基线（生产可用）

| 类别 | 组件 | 版本 | 部署方式 |
|------|------|------|---------|
| 集群管理 | Rancher | 2.8.x | Helm（mgmt-cluster） |
| K8s 发行版 | RKE2 | 1.28.x | Rancher 下发 |
| CNI | Calico / Cilium | 3.27 / 1.15 | RKE2 内置 / Helm |
| Ingress | NGINX Ingress Controller | 1.10.x | Helm |
| 证书 | cert-manager | 1.14.x | Helm |
| 存储 | Longhorn / Rook-Ceph | 1.6 / 1.14 | Helm |
| 服务网格 | Istio（生产可选） | 1.21 | istioctl + Helm |
| 镜像仓库 | Harbor | 2.10.x | Helm |
| Helm 仓库 | Harbor OCI / ChartMuseum | 内置 | — |
| GitOps | ArgoCD + Argo Rollouts | 2.10 / 1.6 | Helm |
| CI | GitLab CE + Runner | 16.x | Helm |
| 密钥 | HashiCorp Vault | 1.16 | Helm + Raft HA |
| 策略 | Kyverno | 1.11 | Helm |
| 镜像扫描 | Trivy Operator + Harbor 内置 | 0.20 / Trivy | Helm |
| 镜像签名 | Cosign / Sigstore | 2.2 | CLI |
| 监控 | kube-prometheus-stack | 58.x | Helm |
| 日志 | Loki + Promtail（首选）/ ELK（备选） | 2.9 / 8.13 | Helm |
| 链路追踪 | Tempo + OpenTelemetry Collector | 2.4 | Helm |
| 备份 | Velero + CloudNativePG Barman | 1.13 / 1.22 | Helm |
| **业务依赖** | | | |
| 数据库 | CloudNativePG（PG 16） | Operator 1.22 | Helm |
| 缓存 | Redis Operator (OT-CONTAINER-KIT) | 0.16 | Helm |
| 消息 | RocketMQ Operator | 1.0.0 | Helm |
| 注册中心 | Nacos Helm Chart | 2.3.x | Helm |
| 全文检索 | ECK (Elastic Cloud on K8s) | 2.12 | Operator |
| 任务调度 | XXL-Job-Admin | 2.4.1 | Helm（自维护 Chart） |

---

## 1. 物理 / 虚拟基础设施规划

### 1.1 网络规划

| 网段 | 用途 | 备注 |
|------|------|------|
| `10.10.0.0/22`  | Mgmt 集群节点段 | 3 + 3 节点 |
| `10.20.0.0/22`  | DEV 节点段     | |
| `10.30.0.0/22`  | STG 节点段     | |
| `10.40.0.0/22`  | PROD 节点段    | |
| `10.244.0.0/16` | Pod CIDR（每集群独立） | 每集群配置时差异化 |
| `10.96.0.0/12`  | Service CIDR  | |
| `192.168.50.0/24` | LoadBalancer VIP 段（MetalLB） | 仅在裸金属/私有云环境需要 |

**外部入口**：F5/HAProxy → MetalLB（私有云）/ Cloud LB（公有云）→ NGINX Ingress。
**集群互访**：跨集群仅允许 `mgmt-cluster` 出向（Rancher Agent + ArgoCD）；`prod-cluster` 不允许出公网（出向走 egress NAT 网关，白名单：Harbor / Vault / NTP / DNS）。

### 1.2 节点角色与污点策略

每个 RKE2 集群按角色专用化：

```yaml
# 通用节点标签
node-role.kubernetes.io/control-plane: "true"   # master
node-role.kubernetes.io/worker: "true"          # worker
node.calendar.io/workload: stateless|stateful|infra|build
```

| 标签 | 污点 | 承载工作负载 |
|------|------|-------------|
| `workload=stateless` | — | 业务微服务（calendar-core / reminder / notification ...） |
| `workload=stateful`  | `dedicated=stateful:NoSchedule` | PostgreSQL / Redis / Kafka / RocketMQ / ES |
| `workload=infra`     | `dedicated=infra:NoSchedule` | Ingress / Logging / Monitoring / cert-manager |
| `workload=build`     | `dedicated=build:NoSchedule` | GitLab Runner / Kaniko |

业务 Helm Chart 中通过 `nodeSelector` + `tolerations` 强约束。

### 1.3 存储规划

| StorageClass | 后端 | 用途 |
|--------------|------|------|
| `longhorn-ssd-r3` | Longhorn 3 副本 NVMe | DEV/STG 一般持久化 |
| `rook-ceph-block-r3` | Ceph RBD | PROD 块存储（DB、MQ） |
| `rook-ceph-fs` | CephFS | PROD 共享卷（日志/快照） |
| `rook-ceph-bucket` | RGW S3 | Velero / Loki / Harbor 制品 |
| `local-path` | local-path-provisioner | 临时缓存（不可用于 DB） |

---

## 2. Rancher 部署方案（管理平面）

### 2.1 高可用部署

1. **mgmt-cluster** 先用 RKE2 自举：
   ```bash
   # control-plane 节点 1
   curl -sfL https://get.rke2.io | INSTALL_RKE2_VERSION=v1.28.10+rke2r1 sh -
   systemctl enable --now rke2-server.service
   # token 取自 /var/lib/rancher/rke2/server/node-token
   # control-plane 节点 2/3 加入：
   mkdir -p /etc/rancher/rke2 && cat > /etc/rancher/rke2/config.yaml <<EOF
   server: https://<cp-1-ip>:9345
   token: <token>
   tls-san: [rancher.calendar.io, mgmt-api.calendar.io]
   EOF
   curl -sfL https://get.rke2.io | INSTALL_RKE2_VERSION=v1.28.10+rke2r1 sh -
   systemctl enable --now rke2-server.service
   ```

2. **cert-manager**（Rancher 依赖）：
   ```bash
   helm repo add jetstack https://charts.jetstack.io && helm repo update
   helm upgrade --install cert-manager jetstack/cert-manager \
     --namespace cert-manager --create-namespace \
     --version v1.14.5 --set installCRDs=true
   ```

3. **Rancher** Helm 安装：
   ```bash
   helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
   kubectl create namespace cattle-system
   helm upgrade --install rancher rancher-stable/rancher \
     --namespace cattle-system \
     --set hostname=rancher.calendar.io \
     --set bootstrapPassword='ChangeMe@2026' \
     --set replicas=3 \
     --set ingress.tls.source=letsEncrypt \
     --set letsEncrypt.email=ops@calendar.io \
     --set letsEncrypt.ingress.class=nginx
   ```

### 2.2 下游集群创建（GitOps 化）

通过 **Rancher Cluster API + Fleet** 一键下发 RKE2 集群定义。`infra-repo/clusters/prod.yaml` 示例：

```yaml
apiVersion: provisioning.cattle.io/v1
kind: Cluster
metadata:
  name: prod-cluster
  namespace: fleet-default
spec:
  kubernetesVersion: v1.28.10+rke2r1
  rkeConfig:
    machineGlobalConfig:
      cni: cilium
      disable: [rke2-ingress-nginx]      # 由我们自管 Ingress
      tls-san: [prod-api.calendar.io]
    machinePoolConfig:
    - name: cp
      quantity: 3
      etcdRole: true
      controlPlaneRole: true
      labels: { node.calendar.io/workload: infra }
      taints:
      - key: node-role.kubernetes.io/control-plane
        effect: NoSchedule
    - name: stateless
      quantity: 6
      workerRole: true
      labels: { node.calendar.io/workload: stateless }
    - name: stateful
      quantity: 4
      workerRole: true
      labels: { node.calendar.io/workload: stateful }
      taints: [{ key: dedicated, value: stateful, effect: NoSchedule }]
    - name: infra
      quantity: 2
      workerRole: true
      labels: { node.calendar.io/workload: infra }
      taints: [{ key: dedicated, value: infra, effect: NoSchedule }]
```

### 2.3 多租户与 RBAC（Rancher Project / Namespace 模型）

| Project（Rancher） | Namespaces | Member Group（LDAP/AD/OIDC） | 权限 |
|-------------------|------------|------------------------------|------|
| `platform`        | `cattle-*`, `kube-system`, `monitoring`, `logging`, `ingress-nginx`, `cert-manager`, `argocd`, `vault` | `grp-platform-admin` | Cluster Owner |
| `calendar-prod`   | `calendar-prod`, `calendar-data-prod` | `grp-calendar-sre`（成员）/ `grp-calendar-dev`（只读） | Project Owner / Read-Only |
| `calendar-stg`    | `calendar-stg`, `calendar-data-stg` | `grp-calendar-dev`（成员） | Project Member |
| `calendar-dev`    | `calendar-dev`, `calendar-data-dev` | `grp-calendar-dev`（成员） | Project Owner |

接入企业 OIDC（Keycloak / Azure AD / 飞书）：Rancher → Auth Provider → OIDC，组映射到 Rancher Group。

### 2.4 Rancher Backup（管理面备份）

```bash
helm repo add rancher-charts https://charts.rancher.io
helm install rancher-backup-crd rancher-charts/rancher-backup-crd -n cattle-resources-system --create-namespace
helm install rancher-backup     rancher-charts/rancher-backup     -n cattle-resources-system
```

```yaml
# 周备份到 S3
apiVersion: resources.cattle.io/v1
kind: Backup
metadata: { name: rancher-weekly }
spec:
  resourceSetName: rancher-resource-set
  schedule: "0 3 * * 0"
  retentionCount: 8
  storageLocation:
    s3:
      bucketName: rancher-backups
      endpoint: s3.calendar.io
      credentialSecretName: s3-creds
      credentialSecretNamespace: cattle-resources-system
```

---

## 3. Harbor 部署方案（企业级镜像与 Helm 仓库）

### 3.1 HA 拓扑

```
            ┌──────────────┐
            │  LoadBalancer │
            └──────┬───────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
 Harbor-1       Harbor-2       Harbor-3   (Helm replicas: 3)
    │              │              │
    └─────┬────────┴──────┬───────┘
          ▼               ▼
   PostgreSQL HA     Redis Sentinel
   (CloudNativePG)   (HA, 3 sentinels)
          │
          ▼
     Object Store (Ceph RGW S3 / 对象存储)
```

### 3.2 Helm 安装（生产配置要点）

```bash
helm repo add harbor https://helm.goharbor.io
helm upgrade --install harbor harbor/harbor \
  --namespace harbor --create-namespace \
  --version 1.14.0 \
  -f harbor-values.yaml
```

`harbor-values.yaml` 关键片段：

```yaml
expose:
  type: ingress
  tls: { enabled: true, certSource: secret, secret: { secretName: harbor-tls } }
  ingress:
    hosts: { core: harbor.calendar.io, notary: notary.calendar.io }
    className: nginx

externalURL: https://harbor.calendar.io

persistence:
  enabled: true
  resourcePolicy: keep
  imageChartStorage:
    type: s3
    s3:
      region: cn-east
      bucket: harbor-registry
      regionendpoint: https://s3.calendar.io
      accesskey: <ACCESS_KEY>
      secretkey: <SECRET_KEY>

database:
  type: external
  external:
    host: pg-harbor-rw.harbor.svc      # 由 CloudNativePG 提供
    port: 5432
    username: harbor
    coreDatabase: registry
    sslmode: verify-full

redis:
  type: external
  external:
    addr: redis-harbor-master.harbor.svc:6379
    sentinelMasterSet: harbor

trivy:    { enabled: true }       # 内置漏洞扫描
notary:   { enabled: false }      # 推荐用 Cosign，更现代
chartmuseum: { enabled: false }   # 用 Harbor OCI 即可

portal: { replicas: 3 }
core:   { replicas: 3 }
jobservice: { replicas: 2 }
registry: { replicas: 3 }
```

### 3.3 Project / 镜像分层规范

| Harbor Project | 用途 | Quota | 公开 | 镜像保留策略 |
|----------------|------|-------|------|-------------|
| `infrastructure` | 基础组件镜像（PG/Redis/...） | 200 GB | 只读公开 | 永久保留最新 5 版 |
| `calendar-base`  | 公司基础镜像（Java 17 + JRE 调优） | 50 GB | 只读公开 | 保留所有 release tag，dev tag 30 天 |
| `calendar-app-dev` | 开发环境业务镜像 | 200 GB | 私有 | 保留最近 14 天 + 100 个 |
| `calendar-app-stg` | 预发业务镜像 | 200 GB | 私有 | 保留最近 60 天 + 200 个 |
| `calendar-app-prod` | 生产镜像（仅由 CI 提升流水线推入） | 500 GB | 私有 | 保留所有 release tag |
| `helm-charts`    | Helm OCI Chart 仓库 | 50 GB | 私有 | 保留所有 |

镜像命名约定：
```
harbor.calendar.io/calendar-app-prod/calendar-core:1.4.0-rc.2-abc123
                                     │             │     │       │
                                     服务名       semver  阶段   git short sha
```

### 3.4 镜像安全：扫描 + 签名 + 准入

1. **漏洞扫描**：Harbor → Project → "Prevent vulnerable images"（CVSS ≥ 7 阻止 pull）。
2. **签名**：CI 出包后 `cosign sign --key cosign.key harbor.calendar.io/...`，密钥存 Vault。
3. **集群准入**：Kyverno 策略（仅允许签名镜像进入 prod）：
   ```yaml
   apiVersion: kyverno.io/v1
   kind: ClusterPolicy
   metadata: { name: verify-images-prod }
   spec:
     validationFailureAction: Enforce
     rules:
     - name: check-image-signature
       match:
         resources: { kinds: [Pod], namespaces: [calendar-prod] }
       verifyImages:
       - imageReferences: [ "harbor.calendar.io/calendar-app-prod/*" ]
         attestors:
         - entries:
           - keys: { publicKeys: |-
               -----BEGIN PUBLIC KEY-----
               ...
               -----END PUBLIC KEY----- }
   ```

### 3.5 Replication（异地灾备 / 加速）

`Harbor → Administration → Replications`：
- Rule 1：`calendar-app-prod/**:*-rc.*` 实时单向同步至 DR Harbor。
- Rule 2：定时（每日 02:00）增量同步公网基础镜像（dockerhub/quay）→ `infrastructure/`。

---

## 4. Helm 仓库与 Chart 体系

### 4.1 Chart 仓库

将 **Harbor 作为 OCI Helm 仓库**（无需额外组件）：

```bash
helm registry login harbor.calendar.io -u ci -p <token>
helm package ./charts/calendar-core -d dist/
helm push dist/calendar-core-1.4.0.tgz oci://harbor.calendar.io/helm-charts
```

### 4.2 Chart 结构（Umbrella Pattern）

```
charts/
├── calendar-platform/                # 顶层 umbrella chart（一键拉起整个业务）
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── values-dev.yaml
│   ├── values-stg.yaml
│   ├── values-prod.yaml
│   └── charts/                        # dependency lock
│       ├── calendar-core-1.4.0.tgz
│       ├── calendar-reminder-1.4.0.tgz
│       ├── calendar-notification-1.4.0.tgz
│       ├── user-setting-1.4.0.tgz
│       └── calendar-gateway-1.4.0.tgz
├── calendar-core/                     # 单服务 chart（公司模板生成）
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── ingress.yaml
│       ├── hpa.yaml
│       ├── pdb.yaml
│       ├── servicemonitor.yaml
│       ├── networkpolicy.yaml
│       ├── rollout.yaml               # Argo Rollouts canary
│       └── _helpers.tpl
└── infra-stack/                       # 业务依赖的中间件 umbrella
    ├── postgresql/                    # CloudNativePG Cluster CR
    ├── redis-cluster/
    ├── rocketmq/
    ├── nacos/
    ├── elasticsearch/
    └── xxl-job/
```

### 4.3 公司基础 Chart 模板（calendar-base-chart）

提供：标准 labels、resources、probes（`/actuator/health/liveness`、`/actuator/health/readiness`、`/actuator/health/startup`）、metrics scrape 注解、ServiceAccount、PodSecurityContext（runAsNonRoot=true）、PDB（`minAvailable: 50%`）、TopologySpreadConstraints（跨 zone）、HPA、Argo Rollouts canary 模板。

`templates/deployment.yaml` 关键片段：
```yaml
spec:
  replicas: {{ .Values.replicas }}
  template:
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              topologyKey: topology.kubernetes.io/zone
              labelSelector: { matchLabels: { app: {{ include "app.name" . }} } }
      topologySpreadConstraints:
      - maxSkew: 1
        topologyKey: kubernetes.io/hostname
        whenUnsatisfiable: ScheduleAnyway
        labelSelector: { matchLabels: { app: {{ include "app.name" . }} } }
      containers:
      - name: app
        image: "{{ .Values.image.registry }}/{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        env:
        - { name: SPRING_PROFILES_ACTIVE, value: {{ .Values.profile }} }
        - { name: TZ, value: UTC }
        - name: NACOS_SERVER_ADDR
          valueFrom: { configMapKeyRef: { name: nacos-conf, key: addr } }
        envFrom:
        - secretRef: { name: {{ include "app.name" . }}-secret }   # 由 ExternalSecret 同步自 Vault
        resources: {{ toYaml .Values.resources | nindent 10 }}
        startupProbe:   { httpGet: { path: /actuator/health/startup,   port: 8080 }, periodSeconds: 5,  failureThreshold: 24 }
        readinessProbe: { httpGet: { path: /actuator/health/readiness, port: 8080 }, periodSeconds: 5 }
        livenessProbe:  { httpGet: { path: /actuator/health/liveness,  port: 8080 }, periodSeconds: 10 }
        lifecycle:
          preStop: { exec: { command: ["sh","-c","sleep 10"] } }    # 等待流量摘除
```

---

## 5. 业务依赖中间件（Helm + Operator 一体化）

### 5.1 PostgreSQL（CloudNativePG）

```bash
helm install cnpg cnpg/cloudnative-pg -n cnpg-system --create-namespace
```

```yaml
# pg-calendar.yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata: { name: pg-calendar, namespace: calendar-data-prod }
spec:
  instances: 3                        # 1 主 2 同步从
  primaryUpdateStrategy: unsupervised
  postgresql:
    parameters:
      shared_buffers: "8GB"
      work_mem: "64MB"
      max_connections: "500"
      wal_level: "replica"
  bootstrap:
    initdb:
      database: calendar_db
      owner: calendar
      secret: { name: pg-calendar-app }
  storage: { size: 500Gi, storageClass: rook-ceph-block-r3 }
  backup:
    barmanObjectStore:
      destinationPath: s3://pg-backup/calendar
      s3Credentials: { accessKeyId: { name: s3-creds, key: ak }, secretAccessKey: { name: s3-creds, key: sk } }
      wal: { compression: gzip }
      data: { compression: gzip, immediateCheckpoint: true, jobs: 4 }
    retentionPolicy: "30d"
  monitoring: { enablePodMonitor: true }
```

业务连接：`pg-calendar-rw` (写) / `pg-calendar-ro` (只读，自动负载到副本)。

### 5.2 Redis Cluster

```bash
helm install redis-operator ot-helm/redis-operator -n redis-operator --create-namespace
```

```yaml
apiVersion: redis.redis.opstreelabs.in/v1beta2
kind: RedisCluster
metadata: { name: redis-calendar, namespace: calendar-data-prod }
spec:
  clusterSize: 3                # 3 主 3 从
  redisLeader:   { replicas: 3, redisConfig: { additionalRedisConfig: redis-conf } }
  redisFollower: { replicas: 3 }
  storage: { volumeClaimTemplate: { spec: { storageClassName: rook-ceph-block-r3, resources: { requests: { storage: 50Gi } } } } }
  redisExporter: { enabled: true }
```

### 5.3 RocketMQ

使用 `apache/rocketmq-operator`：1 NameServer Pod (副本 3) + 1 Broker Cluster (Master 2 + Slave 2，DLedger 模式更佳)。
关键：`brokerClusterName: calendar-rmq`，`storageMode: EmptyDir → PersistentVolume`。

### 5.4 Nacos

```bash
helm install nacos nacos/nacos --version 2.3.x -n calendar-data-prod \
  --set replicaCount=3 \
  --set mode=cluster \
  --set persistence.enabled=true \
  --set persistence.storageClass=rook-ceph-block-r3 \
  --set mysql.enabled=false \
  --set externalDatabase.engine=postgresql \
  --set externalDatabase.host=pg-calendar-rw \
  --set externalDatabase.dbName=nacos
```

### 5.5 Elasticsearch（ECK Operator）

```bash
kubectl apply -f https://download.elastic.co/downloads/eck/2.12.0/crds.yaml
kubectl apply -f https://download.elastic.co/downloads/eck/2.12.0/operator.yaml
```

```yaml
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata: { name: es-calendar, namespace: calendar-data-prod }
spec:
  version: 8.13.4
  nodeSets:
  - name: master
    count: 3
    config: { node.roles: ["master"] }
  - name: data
    count: 3
    config: { node.roles: ["data","ingest"] }
    volumeClaimTemplates:
    - metadata: { name: elasticsearch-data }
      spec: { storageClassName: rook-ceph-block-r3, resources: { requests: { storage: 200Gi } } }
```

### 5.6 XXL-Job

公司自维护 `helm-charts/xxl-job` Chart：
- Admin（StatefulSet × 2，使用 PostgreSQL 后端）。
- Executor 由各业务 Chart 内嵌 Sidecar 暴露 `9999` 端口。
- ConfigMap 注入 `xxl.job.admin.addresses=http://xxl-job-admin.calendar-data-prod:8080/xxl-job-admin`。

---

## 6. 平台横向能力

### 6.1 Ingress + 证书 + WAF

- **NGINX Ingress Controller**（Helm）：3 副本，部署在 `infra` 节点池。
- **cert-manager + ClusterIssuer**：Let's Encrypt + DNS-01（私有 PKI 用 Vault PKI）。
- **WAF**：在 Ingress 前置 ModSecurity（NGINX OWASP CRS Plugin），或选 OpenAppSec。
- 证书 Secret 通过 Reflector 跨 Namespace 同步。

### 6.2 服务网格（生产推荐 Istio）

```bash
istioctl install --set profile=demo -y
kubectl label namespace calendar-prod istio-injection=enabled
```

收益：mTLS、灰度路由（与 Argo Rollouts 集成）、零信任、可观测性补全。

### 6.3 Vault + External Secrets

- Vault 部署模式：Raft HA（3 节点） + auto-unseal（KMS 或 Transit）。
- `kv-v2` 引擎 `secret/calendar/{env}/{service}`。
- ExternalSecrets Operator 把 Vault Secret 同步为 K8s Secret，业务 Pod 通过 `envFrom` 注入。

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata: { name: calendar-core-secret, namespace: calendar-prod }
spec:
  refreshInterval: 1h
  secretStoreRef: { name: vault-prod, kind: ClusterSecretStore }
  target: { name: calendar-core-secret, creationPolicy: Owner }
  data:
  - secretKey: SPRING_DATASOURCE_PASSWORD
    remoteRef: { key: secret/calendar/prod/calendar-core, property: db.password }
  - secretKey: NACOS_TOKEN
    remoteRef: { key: secret/calendar/prod/calendar-core, property: nacos.token }
```

### 6.4 策略与合规（Kyverno）

最小套件（生产强制）：
- `disallow-host-namespaces` / `disallow-host-path` / `disallow-privileged-containers`
- `require-pod-resources` / `require-pod-probes` / `require-pdb`
- `verify-images-prod`（见 §3.4）
- `ensure-network-policy`（每 Namespace 必须有默认 deny + allow 列表）
- `restrict-image-registries`（仅允许 `harbor.calendar.io/*`）

### 6.5 网络策略

每个业务 Namespace 默认 `deny-all`，按依赖白名单：
- `calendar-core` → `pg-calendar-rw:5432`、`redis-calendar:6379`、`nacos:8848/9848`、`rocketmq:9876/10911`、`vault:8200`
- 禁止 `calendar-prod` 直连 `calendar-data-prod` 之外的网段
- 仅 Ingress Namespace 可访问业务 Pod 的 8080 端口

### 6.6 Pod 安全（PSS Restricted）

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: calendar-prod
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: latest
```

镜像基础约束：`runAsNonRoot=true`、`readOnlyRootFilesystem=true`（业务镜像加 `tmpfs`/`emptyDir` 卷供日志）、`seccompProfile=RuntimeDefault`、`capabilities.drop=[ALL]`。

---

## 7. CI/CD 流水线（GitLab + Argo CD）

### 7.1 全景图

```
开发推送代码 ─▶ GitLab Pipeline ─▶ Build/Test/Scan ─▶ Push Image+Helm to Harbor
                                                     │
                                                     ▼
                              Update infra-repo manifest (image tag)
                                                     │
                                                     ▼
                ArgoCD Sync ─▶ Argo Rollouts 渐进发布 ─▶ Prod
                                       │
                                       └─ 失败自动回滚（基于 Prometheus Analysis）
```

### 7.2 GitLab 部署（mgmt-cluster）

```bash
helm repo add gitlab https://charts.gitlab.io
helm upgrade --install gitlab gitlab/gitlab \
  --namespace gitlab --create-namespace \
  -f gitlab-values.yaml
```

`gitlab-values.yaml` 核心：
```yaml
global:
  hosts: { domain: calendar.io }
  edition: ce
  ingress: { configureCertmanager: true, class: nginx }
  appConfig:
    omniauth: { enabled: true, autoSignInWithProvider: oidc, providers: [{ secret: oidc-secret }] }
postgresql: { install: false }   # 用 CloudNativePG 提供
redis:      { install: false }
gitlab-runner: { install: false } # 用专门 Runner Chart 部署在 build 节点
```

### 7.3 GitLab Runner（K8s Executor）

```bash
helm repo add gitlab-runner https://charts.gitlab.io
helm upgrade --install gitlab-runner gitlab-runner/gitlab-runner \
  -n gitlab-runner --create-namespace -f runner-values.yaml
```

```yaml
runners:
  config: |
    [[runners]]
      name = "k8s-build-runner"
      url  = "https://gitlab.calendar.io/"
      executor = "kubernetes"
      [runners.kubernetes]
        namespace = "gitlab-runner"
        cpu_request = "500m"
        memory_request = "1Gi"
        helper_image = "registry.gitlab.com/gitlab-org/gitlab-runner-helper:x86_64-v16.10.0"
        node_selector = { "node.calendar.io/workload" = "build" }
        [[runners.kubernetes.node_tolerations]]
          key = "dedicated"
          operator = "Equal"
          value = "build"
          effect = "NoSchedule"
        [[runners.kubernetes.volumes.empty_dir]]
          name = "docker-cache"
          mount_path = "/var/cache/buildah"
```

### 7.4 Pipeline 设计（`.gitlab-ci.yml` 标准模板）

```yaml
stages: [validate, test, sast, build, image, scan, sign, publish, deploy]

variables:
  HARBOR_REGISTRY: harbor.calendar.io
  HARBOR_PROJECT_DEV:  calendar-app-dev
  HARBOR_PROJECT_STG:  calendar-app-stg
  HARBOR_PROJECT_PROD: calendar-app-prod
  IMAGE_NAME: ${CI_PROJECT_NAME}
  IMAGE_TAG:  ${CI_COMMIT_SHORT_SHA}
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"

cache:
  key: { files: [pom.xml] }
  paths: [.m2/repository, target/]

# ── 1. 静态校验
checkstyle:
  stage: validate
  image: ${HARBOR_REGISTRY}/calendar-base/maven:3.9-eclipse-temurin-17
  script: [ "mvn -B -ntp checkstyle:check spotbugs:check" ]

# ── 2. 单测 + 覆盖率门禁（JaCoCo ≥ 70%）
unit-test:
  stage: test
  image: ${HARBOR_REGISTRY}/calendar-base/maven:3.9-eclipse-temurin-17
  script:
  - mvn -B -ntp -Dmaven.test.failure.ignore=false test jacoco:report
  - awk -F, '{ instr += $4 + $5; cov += $5 } END { printf "Coverage: %.2f%%\n", cov/instr*100; if (cov/instr<0.70) exit 1 }' target/site/jacoco/jacoco.csv
  artifacts: { reports: { junit: "**/target/surefire-reports/TEST-*.xml" }, paths: [target/site/jacoco] }

# ── 3. SAST（Semgrep）
sast:
  stage: sast
  image: returntocorp/semgrep:1.66
  script: [ "semgrep ci --config p/owasp-top-ten --error" ]

# ── 4. 编译打包
package:
  stage: build
  image: ${HARBOR_REGISTRY}/calendar-base/maven:3.9-eclipse-temurin-17
  script: [ "mvn -B -ntp -DskipTests package" ]
  artifacts: { paths: ["**/target/*.jar"], expire_in: 2 days }

# ── 5. 构建镜像（无 Docker Daemon，用 Kaniko，提升安全性）
build-image:
  stage: image
  image: { name: gcr.io/kaniko-project/executor:v1.22.0-debug, entrypoint: [""] }
  script:
  - mkdir -p /kaniko/.docker
  - echo "{\"auths\":{\"$HARBOR_REGISTRY\":{\"auth\":\"$(echo -n $HARBOR_USER:$HARBOR_PASS | base64 -w0)\"}}}" > /kaniko/.docker/config.json
  - >
    /kaniko/executor
      --context $CI_PROJECT_DIR
      --dockerfile $CI_PROJECT_DIR/Dockerfile
      --destination $HARBOR_REGISTRY/$HARBOR_PROJECT_DEV/$IMAGE_NAME:$IMAGE_TAG
      --build-arg JAR_FILE=target/${IMAGE_NAME}.jar
      --cache=true --cache-repo=$HARBOR_REGISTRY/calendar-base/kaniko-cache

# ── 6. 镜像漏洞扫描（Trivy）
scan-image:
  stage: scan
  image: aquasec/trivy:0.50.0
  script:
  - trivy image --exit-code 1 --severity CRITICAL,HIGH --no-progress \
      $HARBOR_REGISTRY/$HARBOR_PROJECT_DEV/$IMAGE_NAME:$IMAGE_TAG

# ── 7. 镜像签名（Cosign + Vault）
sign-image:
  stage: sign
  image: gcr.io/projectsigstore/cosign:v2.2.4
  script:
  - export VAULT_ADDR=https://vault.calendar.io
  - cosign login $HARBOR_REGISTRY -u $HARBOR_USER -p $HARBOR_PASS
  - cosign sign --key hashivault://cosign-key \
      $HARBOR_REGISTRY/$HARBOR_PROJECT_DEV/$IMAGE_NAME:$IMAGE_TAG

# ── 8. 推送 Helm Chart（OCI）
publish-chart:
  stage: publish
  image: alpine/helm:3.14.4
  script:
  - helm registry login $HARBOR_REGISTRY -u $HARBOR_USER -p $HARBOR_PASS
  - helm package charts/${IMAGE_NAME} --version ${CI_COMMIT_TAG:-0.0.0+$CI_COMMIT_SHORT_SHA} -d dist/
  - helm push dist/${IMAGE_NAME}-*.tgz oci://$HARBOR_REGISTRY/helm-charts

# ── 9. 更新 GitOps 仓库（触发 ArgoCD）
deploy-dev:
  stage: deploy
  image: alpine/git:2.43
  variables: { ENV: dev, NS: calendar-dev }
  script:
  - git clone https://oauth2:$GITOPS_TOKEN@gitlab.calendar.io/platform/infra-repo.git
  - cd infra-repo/envs/$ENV/$IMAGE_NAME
  - yq -i ".image.tag = \"$IMAGE_TAG\"" values.yaml
  - git add . && git -c user.email=ci@calendar.io -c user.name=ci commit -m "chore($IMAGE_NAME): $ENV → $IMAGE_TAG"
  - git push origin main
  rules: [{ if: '$CI_COMMIT_BRANCH == "main"' }]

# ── 10. 提升到 STG（手动）
promote-stg:
  stage: deploy
  variables: { ENV: stg }
  script:
  - skopeo copy --src-creds=$H:$HP --dest-creds=$H:$HP \
      docker://$HARBOR_REGISTRY/$HARBOR_PROJECT_DEV/$IMAGE_NAME:$IMAGE_TAG \
      docker://$HARBOR_REGISTRY/$HARBOR_PROJECT_STG/$IMAGE_NAME:$IMAGE_TAG
  - !reference [deploy-dev, script]
  when: manual
  rules: [{ if: '$CI_COMMIT_TAG =~ /^v.*-rc\..*/' }]

# ── 11. 提升到 PROD（双人审批）
promote-prod:
  stage: deploy
  variables: { ENV: prod }
  script:
  - skopeo copy ... $HARBOR_PROJECT_STG → $HARBOR_PROJECT_PROD
  - !reference [deploy-dev, script]
  when: manual
  rules: [{ if: '$CI_COMMIT_TAG =~ /^v\d+\.\d+\.\d+$/' }]
```

### 7.5 GitOps 仓库结构（`infra-repo`）

```
infra-repo/
├── clusters/                    # Rancher Cluster CR、节点池定义
├── platform/                    # 横向能力 Helm release（istio/argocd/vault/...）
│   ├── argocd/
│   ├── ingress-nginx/
│   ├── kube-prometheus-stack/
│   └── kyverno/
├── envs/
│   ├── dev/
│   │   ├── infra-stack/         # PG/Redis/RMQ/Nacos/ES（CR + values）
│   │   ├── calendar-core/values.yaml
│   │   ├── calendar-reminder/values.yaml
│   │   └── ...
│   ├── stg/
│   └── prod/
└── apps/                        # ArgoCD Application/AppProject 资源
    ├── platform.yaml            # AppOfApps：管理平台组件
    ├── calendar-dev.yaml        # AppOfApps：拉所有 dev 业务
    ├── calendar-stg.yaml
    └── calendar-prod.yaml
```

`apps/calendar-prod.yaml` 示例（App-of-Apps 模式）：

```yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata: { name: calendar-prod, namespace: argocd }
spec:
  destinations: [{ namespace: 'calendar-prod', server: 'https://prod-cluster.calendar.io' }]
  sourceRepos: ['https://gitlab.calendar.io/platform/infra-repo.git', 'oci://harbor.calendar.io/helm-charts']
  roles:
  - name: sre
    policies: [ "p, proj:calendar-prod:sre, applications, sync, calendar-prod/*, allow" ]
    groups: [ grp-calendar-sre ]
---
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata: { name: calendar-prod, namespace: argocd }
spec:
  generators:
  - git:
      repoURL: https://gitlab.calendar.io/platform/infra-repo.git
      revision: main
      directories: [{ path: envs/prod/* }]
  template:
    metadata: { name: 'prod-{{path.basename}}' }
    spec:
      project: calendar-prod
      source:
        repoURL: https://gitlab.calendar.io/platform/infra-repo.git
        targetRevision: main
        path: '{{path}}'
        helm: { valueFiles: [ values.yaml ] }
      destination: { server: https://prod-cluster.calendar.io, namespace: calendar-prod }
      syncPolicy:
        automated: { selfHeal: true, prune: false }    # prod 不自动 prune，避免误删
        syncOptions: [ CreateNamespace=true, ApplyOutOfSyncOnly=true ]
```

### 7.6 Argo Rollouts 渐进式发布（金丝雀）

业务 Helm 中渲染 `Rollout` 替代 `Deployment`：

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata: { name: calendar-core }
spec:
  replicas: 12
  strategy:
    canary:
      canaryService: calendar-core-canary
      stableService: calendar-core
      trafficRouting:
        istio:
          virtualService: { name: calendar-core, routes: [primary] }
      steps:
      - setWeight: 5
      - pause: { duration: 5m }
      - analysis: { templates: [{ templateName: success-rate-and-latency }] }
      - setWeight: 25
      - pause: { duration: 10m }
      - setWeight: 50
      - pause: { duration: 10m }
      - setWeight: 100
```

`AnalysisTemplate`（基于 Prometheus）：成功率 ≥ 99.5% 且 p95 RT ≤ 300ms，否则**自动回滚**。

### 7.7 Argo Image Updater（可选）

让 Harbor 中匹配 `^v\d+\.\d+\.\d+-rc\..*$` 的新镜像自动写回 GitOps 仓库（仅 dev/stg），减少手动 promote 的摩擦。

---

## 8. 可观测性（Observability）

### 8.1 指标 — kube-prometheus-stack

```bash
helm install kps prometheus-community/kube-prometheus-stack -n monitoring --create-namespace -f kps-values.yaml
```

要点：
- **Prometheus**：`replicas: 2`、`retention: 30d`、`storage: 500Gi`、`remoteWrite` 到中心 Thanos/Mimir 长期存储。
- **ServiceMonitor**：业务 Helm 渲染 SM 抓取 `/actuator/prometheus`，labels `service=calendar-core, env=prod`。
- **Alertmanager**：Webhook → 钉钉 / 飞书 / PagerDuty。
- **Grafana**：内置 dashboards（K8s/Node/Calico）+ 自维护业务面板（QPS/RT/Cache 命中率/MQ 堆积/PG TPS）。

预置告警样例（`PrometheusRule`）：
```yaml
groups:
- name: calendar.rules
  rules:
  - alert: CalendarCoreHighErrorRate
    expr: sum(rate(http_server_requests_seconds_count{service="calendar-core",status=~"5.."}[5m]))
        / sum(rate(http_server_requests_seconds_count{service="calendar-core"}[5m])) > 0.01
    for: 10m
    labels: { severity: critical }
    annotations:
      summary: "calendar-core 5xx 错误率 > 1%（持续 10 分钟）"
  - alert: PgConnectionsNearLimit
    expr: sum(pg_stat_activity_count{datname="calendar_db"}) by (instance) / max(pg_settings_max_connections) > 0.8
    for: 5m
    labels: { severity: warning }
```

### 8.2 日志 — Loki Stack（首选）

```bash
helm install loki grafana/loki-stack -n logging --create-namespace \
  --set loki.persistence.enabled=true \
  --set loki.persistence.storageClassName=rook-ceph-block-r3 \
  --set loki.persistence.size=500Gi \
  --set promtail.enabled=true
```

业务日志规范（强制）：
- JSON 格式（Logback `LogstashEncoder`）
- 必含字段：`tenant_id`、`trace_id`、`span_id`、`service`、`env`、`level`
- 敏感字段脱敏：`password`、`token`、`mobile`、`id_card` 由公司 Logback Mask 模块自动处理

### 8.3 链路追踪 — Tempo + OpenTelemetry

- 业务通过 `opentelemetry-javaagent.jar` 自动埋点（无侵入）。
- OTel Collector → Tempo；Trace ID 通过 Header 透传，Loki 通过 trace_id 跳转 Tempo。

### 8.4 SRE 仪表盘（Golden Signals）

每个服务必备四张图：流量、错误、延迟、饱和度（饱和度 = HPA 当前/上限、Pod CPU/Memory 使用率）。

---

## 9. 备份与灾备（DR）

### 9.1 工作负载备份 — Velero

```bash
helm install velero vmware-tanzu/velero -n velero --create-namespace -f velero-values.yaml
```

策略：
- 每日全量、每小时增量；保留 30 天。
- 排除 Namespaces：`*-data-*`（数据库由原生工具备份，避免 PV 不一致）。
- 跨集群恢复演练：每月 1 次自动到 DR 集群恢复关键 Namespace 验证（脚本化）。

### 9.2 数据库备份

- **PostgreSQL**：CloudNativePG `barmanObjectStore` → S3，保留 30 天；每日 03:00 异地复制到 DR Bucket（`s3 cp --sse`）。
- **Redis**：RDB + AOF 双开；每日 dump → S3。
- **Elasticsearch**：Snapshot Lifecycle Management → S3，每日 04:00。
- **RocketMQ**：消息归档 → ClickHouse / S3，DLQ 保留 7 天。

### 9.3 镜像 / Chart 灾备

Harbor Replication（§3.5）→ 异地 Harbor，启用 OCI 镜像签名验证。

### 9.4 RTO / RPO 目标

| 资源 | RPO | RTO |
|------|-----|-----|
| 业务无状态 | 0（GitOps 重放） | 30 分钟 |
| PostgreSQL | ≤ 5 分钟（WAL 持续归档） | 1 小时 |
| Redis | ≤ 1 小时 | 30 分钟 |
| 镜像/Chart | 0（双地实时同步） | 0 |

---

## 10. 安全基线

| 域 | 策略 |
|----|------|
| 集群 API | 仅允许跳板机 + Bastion + Mgmt 集群访问；启用 OIDC + audit log → SIEM |
| Pod | PSS Restricted、ImagePolicyWebhook（Kyverno verifyImages）、ReadOnlyRootFS |
| Secret | Vault 中心化；K8s Secret 仅作短期缓存；启用 EncryptionConfig（KMS） |
| 网络 | Default Deny + 白名单 NetworkPolicy；Mesh mTLS；Egress NAT 白名单 |
| 镜像 | Trivy 阻断 Critical/High；Cosign 签名；只允许 Harbor 拉取 |
| 供应链 | SBOM（CycloneDX）随镜像推送；构建产物签名 |
| 审计 | K8s Audit + Vault Audit + Harbor Audit + GitLab Audit → Loki + 长期归档 |
| 合规 | ISO27001 / 等保 2.0 三级 自检表（季度） |

---

## 11. 容量与性能基线（生产）

针对设计文档的"百万 DAU、200ms p95"目标：

| 服务 | 副本 | requests | limits | HPA |
|------|------|----------|--------|-----|
| calendar-core | 6→24 | 1 / 2Gi | 2 / 4Gi | CPU 70% 或 RPS 1000 |
| calendar-reminder | 3→8 | 1 / 1Gi | 2 / 2Gi | CPU 65% |
| calendar-notification | 4→12 | 0.5 / 1Gi | 1 / 2Gi | RPS 800 |
| user-setting | 3→6 | 0.5 / 1Gi | 1 / 2Gi | CPU 70% |
| gateway | 4→16 | 1 / 1Gi | 2 / 2Gi | RPS 2000 |
| PostgreSQL | 3 | 4 / 16Gi | 8 / 32Gi | — |
| Redis-Cluster | 6 | 1 / 4Gi | 2 / 8Gi | — |
| RocketMQ-Broker | 4 | 2 / 8Gi | 4 / 16Gi | — |
| Elasticsearch (data) | 3 | 4 / 16Gi | 8 / 32Gi | — |

压测里程碑：
- **冒烟**：100 vUser × 10 min，错误率 0%。
- **基准**：1k vUser × 30 min，p95 ≤ 300ms。
- **峰值**：5k vUser × 30 min，错误率 ≤ 0.1%。
- **稳定**：3k vUser × 4h，无内存泄漏，GC 时间占比 ≤ 5%。

工具链：k6 + Locust（生成）；Grafana k6 Cloud 看板；JMeter 兼容老团队脚本。

---

## 12. 落地路线（10 周里程碑）

| 周 | 里程碑 | 负责人 | 完成定义 |
|----|--------|-------|---------|
| W1 | 网络/IDC 接通；Mgmt 集群 RKE2 自举 | 基础设施组 | `kubectl get nodes` 显示 6 节点全 Ready |
| W2 | Rancher HA + cert-manager + Harbor HA + Vault | 平台组 | Rancher UI / Harbor UI / Vault unsealed 可登录 |
| W3 | GitLab + GitLab Runner + ArgoCD + Kyverno | 平台组 | 完成 hello-world 全流水线，Kyverno 阻断未签名镜像 |
| W4 | DEV 集群下发；Loki + Prometheus + Tempo 全套 | 平台组 + SRE | Grafana 看到 K8s 仪表盘；Loki 收到日志 |
| W5 | CloudNativePG / Redis / RocketMQ / Nacos / ECK | 平台组 | DEV 中间件全部通过验收用例 |
| W6 | 日历系统 5 个微服务 Helm Chart 入仓 + DEV 部署 | 业务组 | DEV 跑通端到端冒烟 |
| W7 | STG 集群下发 + 灰度 Pipeline + Argo Rollouts | 平台组 | STG 完成一次金丝雀发布 + 自动回滚演练 |
| W8 | 安全加固：mTLS（Istio）/Vault/External Secrets/Kyverno 全开 | 安全组 | 安全扫描报告通过 |
| W9 | PROD 集群上线 + 数据迁移演练 + Velero/Barman 备份 | 全员 | DR 演练 RTO ≤ 1h |
| W10 | 全链路压测 + 监控告警调优 + Runbook | SRE | 通过 §11 全部里程碑 |

---

## 13. 验证清单（Acceptance Checklist）

> 每条都必须以可执行命令证伪，**全部通过**才视为平台 GA。

```bash
# 13.1 集群健康
kubectl --context prod get nodes -o wide | grep -v NotReady
kubectl --context prod get pods -A | awk '$4!="Running" && $4!="Completed"' | (! grep .)

# 13.2 Rancher 接管 4 集群
rancher cluster ls | grep -E '^(mgmt|dev|stg|prod)' | wc -l | grep -q '^4$'

# 13.3 Harbor 健康 + 漏洞扫描启用
curl -fsS https://harbor.calendar.io/api/v2.0/health | jq -e '.status=="healthy"'
curl -fsS https://harbor.calendar.io/api/v2.0/projects/calendar-app-prod \
  -u $H:$P | jq -e '.metadata.prevent_vul=="true" and .metadata.severity=="high"'

# 13.4 Cosign 签名验证
cosign verify --key cosign.pub harbor.calendar.io/calendar-app-prod/calendar-core:1.4.0

# 13.5 Helm OCI 仓库
helm pull oci://harbor.calendar.io/helm-charts/calendar-core --version 1.4.0

# 13.6 ArgoCD 全部 Synced & Healthy
argocd app list -o json | jq -e 'all(.status.health.status=="Healthy" and .status.sync.status=="Synced")'

# 13.7 Kyverno 阻断未签名镜像（应 deny）
kubectl --context prod -n calendar-prod run rogue --image=docker.io/nginx:latest --dry-run=server 2>&1 | grep -q 'denied'

# 13.8 PostgreSQL HA 在线 + WAL 归档
kubectl --context prod -n calendar-data-prod exec pg-calendar-1 -c postgres -- \
  psql -tAc "select count(*) from pg_stat_replication" | grep -q '^2$'
kubectl --context prod -n calendar-data-prod get backup -o json | jq -e '.items|map(.status.phase=="completed")|all'

# 13.9 Velero 周备份成功
velero backup get | grep -E '\bCompleted\b' | head -1

# 13.10 监控/日志/链路全通
curl -fsS http://prometheus.calendar.io/api/v1/targets | jq -e '.data.activeTargets|all(.health=="up")'
curl -fsS http://loki.calendar.io/ready | grep -q ready
curl -fsS http://tempo.calendar.io/ready | grep -q ready

# 13.11 端到端冒烟（业务）
bash scripts/smoke.sh   # 来自《AI 落地实施计划_通用日历系统.md》P16
```

---

## 附录 A：硬件清单参考（私有云裸金属）

| 节点角色 | 数量 | CPU | 内存 | 系统盘 | 数据盘 | 网卡 |
|---------|------|-----|------|--------|--------|------|
| Mgmt CP | 3 | 8C | 16G | 200G SSD | — | 2×10G bond |
| Mgmt Worker | 3 | 16C | 32G | 200G SSD | 1T NVMe | 2×10G bond |
| DEV CP | 3 | 8C | 16G | 200G SSD | — | 2×10G bond |
| DEV Worker | 5 | 16C | 32G | 200G SSD | 1T NVMe | 2×10G bond |
| STG CP | 3 | 8C | 16G | 200G SSD | — | 2×10G bond |
| STG Worker | 8 | 16C | 32G | 200G SSD | 2T NVMe | 2×10G bond |
| PROD CP | 3 | 16C | 32G | 200G SSD | — | 2×25G bond |
| PROD Worker (stateless) | 6 | 32C | 64G | 200G SSD | — | 2×25G bond |
| PROD Worker (stateful) | 4 | 32C | 128G | 200G SSD | 4T NVMe ×4 | 2×25G bond |
| PROD Worker (infra) | 2 | 16C | 32G | 200G SSD | 1T NVMe | 2×25G bond |
| Ceph OSD | 6 | 16C | 64G | 200G SSD | 8T HDD ×8 + 1T NVMe | 2×25G bond |
| Bastion | 2 | 4C | 8G | 200G SSD | — | 2×10G bond |
| LB | 2 | 8C | 16G | 200G SSD | — | 2×25G bond（HAProxy + keepalived） |

## 附录 B：端口矩阵（核心）

| 组件 | 端口 | 暴露范围 |
|------|------|---------|
| K8s API | 6443 | 集群内 + Bastion |
| RKE2 Server | 9345 | 集群内 |
| etcd | 2379-2380 | CP 节点互通 |
| Calico/Cilium | 179 / 8472 / 4240 | 节点互通 |
| Ingress | 80/443 | 公网（SLB） |
| Harbor | 443 | 内网 + DR 集群 |
| Rancher | 443 | 公司内网（SSO） |
| GitLab | 443 / 22 | 公司内网 |
| ArgoCD | 443 | 公司内网（SSO） |
| Vault | 8200 | 集群内 + Bastion |
| PG | 5432 | 仅业务 NS |
| Redis | 6379 / 16379 | 仅业务 NS |
| RocketMQ | 9876 / 10911 / 10909 | 仅业务 NS |
| ES | 9200 / 9300 | 仅业务 NS + Kibana |
| Prometheus | 9090 | infra NS（Grafana 反代） |
| Grafana | 3000 | 公司内网（SSO） |

## 附录 C：对照设计文档一致性

| 设计文档要素 | 本平台落地点 |
|-------------|-------------|
| 微服务（Calendar Core / Reminder / Notification / User Setting） | 5 个 Helm Chart + Argo Rollouts 渐进发布（§4 §7.6） |
| Spring Cloud Gateway + Sentinel | 业务 Chart + Sentinel Dashboard StatefulSet（infra-stack） |
| Nacos | §5.4 cluster 模式，PG 后端 |
| RocketMQ | §5.3 Operator + DLedger HA |
| PostgreSQL（已替代 MySQL） | §5.1 CloudNativePG，3 实例同步流复制，barman 异地备份 |
| Redis Cluster | §5.2 Operator，3M3S，rook-ceph-block-r3 |
| 分层缓存（Caffeine + Redis） | Caffeine 在业务 JVM 内（无需平台支撑）；Redis 由本平台提供，缓存命中率指标在 §8.1 暴露 |
| Elasticsearch | §5.5 ECK，master×3 + data×3 |
| XXL-Job | §5.6 Admin × 2，业务 Sidecar Executor |
| ELK / Loki | §8.2 默认 Loki + Promtail，Kibana 备选 |
| Prometheus + Grafana + Alertmanager | §8.1 kube-prometheus-stack |
| 多租户 / 多环境 | Rancher Project + Argo AppProject + Namespace + NetworkPolicy |
| 国际化时区（UTC 存储 + 动态时区） | 容器 `TZ=UTC`、JVM `-Duser.timezone=UTC`、Locale 由请求驱动 |
| 高可用 99.95% | 三集群 + 跨 Zone 反亲和 + Argo Rollouts + Velero + DR |
| 灰度 / 回滚 | Argo Rollouts canary + Prometheus Analysis 自动回滚 |

---

## 14. 风险与应对

| 风险 | 等级 | 应对 |
|------|------|------|
| etcd 损坏 | 高 | 每 6h 快照 + Velero 多版本；演练每季度 1 次 |
| Harbor 单点 / 仓库爆盘 | 中 | HA + S3 后端 + 配额 + 保留策略 + 异地复制 |
| Vault 不可用 | 高 | Raft 3 节点 + auto-unseal；K8s Secret 短期缓存兜底 |
| Argo Rollouts 误判 | 中 | Analysis 多指标 + 手动 Pause 阶段 + 强制回滚 Runbook |
| 镜像供应链投毒 | 高 | 仅允许 Harbor + Cosign 验签 + Trivy CVSS≥7 阻断 + SBOM |
| 数据库主从分裂 | 高 | CloudNativePG 自动选主；Operator 状态告警 + 人工 Runbook |
| 跨集群升级冲突 | 中 | 版本基线统一；先升 mgmt → dev → stg → prod，各 ≥ 7 天观察期 |

---

## 15. 总结

本方案以 **Rancher 管理多套 RKE2 集群** 为骨架，以 **Harbor 作为镜像 + Helm OCI 仓库**、**Helm + ArgoCD 完成 GitOps 持续交付**，配合 **Argo Rollouts** 实现金丝雀发布与自动回滚；用 **Operator 化中间件**（CloudNativePG / Redis-Operator / RocketMQ-Operator / ECK / 自维护 XXL-Job Chart）承载日历系统全部基础设施依赖；以 **Vault + ExternalSecrets / Kyverno / Cosign / Trivy** 闭环供应链安全；以 **Prometheus + Loki + Tempo** 完成可观测性三件套；以 **Velero + Barman + Harbor Replication** 完成灾备 RPO ≤ 5 分钟、RTO ≤ 1 小时。整套体系满足企业级 99.95% SLA、ISO27001/等保三级合规、并能为日历系统稳定承载百万 DAU 提供工程化保障。

> **配套交付物**：本文 + 《AI 落地实施计划_通用日历系统.md》（业务侧）+ `infra-repo`（GitOps 仓库 skeleton，下一步交付）。
