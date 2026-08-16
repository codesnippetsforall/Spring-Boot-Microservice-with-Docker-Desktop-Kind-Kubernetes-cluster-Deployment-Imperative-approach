# Users Microservice

Spring Boot **Users** API (Java 21) with simple CRUD (no authentication), packaged as a container image and deployed to a **local Kubernetes cluster** (Docker Desktop **kind**, Docker Desktop **kubeadm**, or Podman + Quay).

This README is the project home. Numbered copy-paste commands live in the cheatsheets below. The longer cluster lab (metrics-server, CPU limits, load balancing, kind nodes) is in **[DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md)**.

---

## Contents

1. [Overview](#overview)
2. [Tech stack](#tech-stack)
3. [Project structure](#project-structure)
4. [Prerequisites](#prerequisites)
5. [Run locally](#run-locally)
6. [API reference](#api-reference)
7. [Build the Docker image](#build-the-docker-image)
8. [Kubernetes on the local machine](#kubernetes-on-the-local-machine)
9. [Command cheatsheets](#command-cheatsheets)
10. [How traffic reaches a pod](#how-traffic-reaches-a-pod)
11. [Day-2 operations](#day-2-operations)
12. [Metrics, CPU, and resources](#metrics-cpu-and-resources)
13. [Troubleshooting](#troubleshooting)
14. [Related documents](#related-documents)

---

## Overview

The service stores users (H2 in-memory for the lab, MySQL for `prod`), exposes REST endpoints under `/users`, and returns **`podName`** on each response so you can see which Kubernetes replica handled the request (`HOSTNAME` inside the container).

```text
Client (Postman / curl)
        |
        v
  localhost:8082
        |
        v
  Service  users-ms-service   (LoadBalancer :8082)
        |
        |  kube-proxy on the node that received the packet
        |  picks one Ready pod IP from Endpoints
        v
  Deployment  user-ms-deployment
        +-- Pod  users-ws-k8s :8082
        +-- Pod  users-ws-k8s :8082
```

| Lab object | Name |
|---|---|
| Docker Hub (kind) | `dockermano/users-ws-k8s` |
| Docker Hub (kubeadm) | `dockermano/users-ws-kubeadm-k8s` |
| Quay (Podman) | `quay.io/podmano/users-ws-k8s` |
| Tags | `1.0.0`, `1.0.1`, `latest` |
| Deployment | `user-ms-deployment` |
| Container | `users-ws-k8s` |
| Service | `users-ms-service` |
| App port | **8082** |
| Label | `app=user-ms-deployment` |

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.4 |
| API | Spring Web |
| Persistence | Spring Data JPA, H2 (default/test), MySQL (`prod`) |
| Security | None (open CRUD for the lab) |
| Mapping | ModelMapper, Lombok |
| Observability | Spring Actuator (`/actuator/health`, `/actuator/info`) |
| Container | Amazon Corretto 21 Alpine (`Dockerfile`) |
| Orchestration | Kubernetes (Docker Desktop kind, Docker Desktop kubeadm, or Podman) |

---

## Project structure

```text
UserMicroservice/
├── Dockerfile
├── pom.xml
├── environment.env
├── Docker_Kind_Kubernetes_Commands_Cheatsheet.info
├── Docker_kubeadm_Kubernetes_Commands_Cheatsheet.info
├── Podman_Kubernetes_Commands_Cheatsheet.info
├── DOCKER_KUBERNETES_GUIDE.md       # full cluster lab
├── README.md                        # this file
└── src/main/java/com/appsdeveloperblog/api/users/
    ├── UsersApplication.java
    ├── ui/controllers/UsersController.java
    ├── ui/request/  ui/response/
    ├── service/UsersServiceImpl.java
    ├── io/UserEntity.java  UsersRepository.java
    └── exceptions/
```

---

## Prerequisites

| Tool | Check |
|---|---|
| JDK 21 | `java -version` |
| Maven | `mvn -version` |
| Docker Desktop (Windows) | `docker version` |
| kubectl | `kubectl version --client` |
| Docker Hub account | `docker login` (kind / kubeadm cheatsheets) |
| kind (Docker Desktop kind cluster) | `kind version` (optional; cluster can be created in Docker Desktop UI) |
| Podman + Quay (optional) | `podman version`, `podman login quay.io` |

Give Docker **8 GB+** RAM. Each Users pod uses roughly **250–280 MB**.

---

## Run locally

Default profile uses **H2** in memory and listens on **8082**.

```powershell
mvn -DskipTests spring-boot:run
```

Or after packaging:

```powershell
mvn -DskipTests package
java -jar target/Users-0.0.1-SNAPSHOT.jar
```

Profiles:

| Profile | Config | Database |
|---|---|---|
| default | `application.properties` | H2 `jdbc:h2:mem:testdb` |
| `test` | `application-test.properties` | H2 + file log `logs/users-ws.log` |
| `prod` | `application-prod.properties` | MySQL (`host.docker.internal:3306/photo_app`) |

```powershell
java -jar target/Users-0.0.1-SNAPSHOT.jar --spring.profiles.active=test
```

The Docker image default `CMD` is `--spring.profiles.active=test`.

H2 console (when enabled): `http://localhost:8082/h2-console`

Do not commit real DB passwords or JWT secrets. Keep them out of Git.

---

## API reference

Base URL locally or via the Service: `http://localhost:8082`

| Method | Path | Description |
|---|---|---|
| `POST` | `/users` | Create user (`201`) |
| `GET` | `/users` | List users (`page`, `limit`; default 0 / 20) |
| `GET` | `/users/{userId}` | Get one user |
| `PUT` | `/users/{userId}` | Update user |
| `DELETE` | `/users/{userId}` | Delete user (`204`) |
| `GET` | `/actuator/health` | Health |
| `GET` | `/actuator/info` | Info |

No authentication. Duplicate email returns `409`; missing user returns `404`.

Create / update body:

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com"
}
```

Constraints: first/last name min 2 chars; email valid.

Each user JSON includes **`podName`**:

- On Kubernetes: the pod name (`user-ms-deployment-...`)
- On a laptop: `"local"`

That field is set from `System.getenv("HOSTNAME")` in `UsersController`.

---

## Build the Docker image

```powershell
mvn -DskipTests package
docker build -t dockermano/users-ws-k8s:latest -t dockermano/users-ws-k8s:1.0.0 --build-arg IMAGE_VERSION=1.0.0 .
docker push dockermano/users-ws-k8s:1.0.0
```

The image copies `target/*.jar`, runs as `java -XX:MaxRAMPercentage=75.0 -jar app.jar`.

Create the public Hub repo `dockermano/users-ws-k8s` first. Full image/tag/push notes: [DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md) sections 5–6.

---

## Kubernetes on the local machine

Pick a cheatsheet for the cluster you created, then follow its numbered steps. Narrative (kind vs kubeadm, expose, Postman, PowerShell patch pitfalls) is in **[DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md)**. Summary below.

### Enable a cluster

**Docker Desktop kind:** Settings → Kubernetes → create/edit cluster → **kind**. Node is usually `desktop-control-plane` (cluster name **`desktop`**). Optional extra worker via the UI slider (resets the cluster).

**Docker Desktop kubeadm:** Settings → Kubernetes → create/edit cluster → **kubeadm**. Node is usually `docker-desktop` (single node; no extra worker slider). Switching kind ↔ kubeadm **resets** the cluster.

**kind CLI** (if installed): this lab’s node `desktop-control-plane` means cluster name **`desktop`**.

```powershell
kind create cluster --name desktop
kubectl get nodes
```

You never pass that name into `kubectl create deployment`. kind names nodes `{cluster}-control-plane`.

This laptop lab is **one node**: that node is **both** control plane (API server, scheduler, etcd) and worker (kubelet, kube-proxy, your pods).

### Deploy (imperative)

```powershell
kubectl create deployment user-ms-deployment --image dockermano/users-ws-k8s:1.0.0 --port=8082 --replicas=3
kubectl expose deployment user-ms-deployment --type=LoadBalancer --port=8082 --target-port=8082 --name users-ms-service
kubectl get pods
kubectl get svc
kubectl get endpoints users-ms-service
```

Endpoints example (two healthy pods):

```text
users-ms-service   10.244.0.25:8082,10.244.0.26:8082
```

Match IPs to pods:

```powershell
kubectl get pods -l app=user-ms-deployment -o wide
```

On Docker Desktop kubeadm, try **`http://localhost:8082`**. On kind, localhost often fails — use `kubectl port-forward svc/users-ms-service 8082:8082`.

### New image rollout

```powershell
mvn -DskipTests package
docker build -t dockermano/users-ws-k8s:1.0.1 .
docker push dockermano/users-ws-k8s:1.0.1
kubectl set image deployment/user-ms-deployment users-ws-k8s=dockermano/users-ws-k8s:1.0.1
kubectl rollout status deployment/user-ms-deployment
```

`imagePullPolicy`: `Always` / `IfNotPresent` / `Never` — prefer a **new tag** over mutating `latest`. For kubeadm / Podman, use the image name from that cheatsheet.

---

## Command cheatsheets

Same lab flow (build → push → deploy → expose → rollout → env → scale → logs → metrics → cleanup). Use **one** file so image names and node names match your runtime.

| File | Runtime | Image / registry | Cluster node |
|---|---|---|---|
| **[Docker_Kind_Kubernetes_Commands_Cheatsheet.info](Docker_Kind_Kubernetes_Commands_Cheatsheet.info)** | Docker Desktop **kind** | Docker Hub `dockermano/users-ws-k8s` | `desktop-control-plane` (+ optional worker). `crictl` via `docker exec desktop-control-plane` |
| **[Docker_kubeadm_Kubernetes_Commands_Cheatsheet.info](Docker_kubeadm_Kubernetes_Commands_Cheatsheet.info)** | Docker Desktop **kubeadm** | Docker Hub `dockermano/users-ws-kubeadm-k8s` | `docker-desktop` only. No kind `crictl` container — use `docker stats` / `kubectl top` |
| **[Podman_Kubernetes_Commands_Cheatsheet.info](Podman_Kubernetes_Commands_Cheatsheet.info)** | **Podman** + Kubernetes | Quay `quay.io/podmano/users-ws-k8s` | Do not run Podman Desktop and Docker Desktop together on this Windows box (WSL conflict) |

`kubectl` app commands (`create deployment`, `expose`, `set image`, `rollout`, `scale`) are the same in all three. Differences are image build/push, node name, metrics (`crictl` vs `kubectl top`), and how you reset the cluster.

---

## How traffic reaches a pod

```text
localhost:8082
    → published onto one node (Docker Desktop / kind)
    → that node's kube-proxy (iptables / IPVS)
    → one IP from Endpoints
    → pod (same node, or another node via CNI if you have workers)
```

The **API server is not** on the HTTP path. It only stores Service and Endpoints.

### Healthy pod list (not kube-proxy)

```text
kubelet  (Ready = probe OK, or container Running if no probe)
    → Pod status in the API
EndpointSlice controller  (in kube-controller-manager)
    → writes EndpointSlice / Endpoints
kube-proxy on every node  (watch)
    → updates local iptables
```

- kubelet does **not** update iptables and does **not** push a list to other nodes.  
- Default **readiness probe** interval is **10s** only if you define a probe. This lab’s `kubectl create deployment` has **no** HTTP probe; Ready means the container is running.  
- Endpoint controller and kube-proxy **watch** the API (event-driven), they do not poll kubelet.

`kubectl get endpoints users-ms-service` is the human view of that list.

### One node vs N nodes

Every node has kubelet, runtime, and kube-proxy. They are **peers**, not a chain.

- **1 node:** that kube-proxy DNATs to a local pod IP.  
- **N nodes:** the **ingress node’s** kube-proxy still does the pick (one hop). Other kube-proxies are idle for that packet. The chosen pod IP may live on another node.

All kube-proxies watch the **same** Endpoints object. They do not ask each other for healthy IPs.

### Why Postman always hits one pod

kube-proxy load-balances **TCP connections**, not HTTP clicks. Keep-alive (Postman default) reuses one connection → same pod.

```powershell
1..20 | ForEach-Object {
  curl.exe -s -H "Connection: close" http://localhost:8082/users
  Write-Host "`n---- $_"
}
```

Distribution is **random**, not strict A-B-A-B. Pin one pod:

```powershell
kubectl port-forward pod/<pod-name> 8082:8082
```

`kubectl exec deploy/...` and `port-forward` always use **one** pod.

More detail: [DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md) sections 4, 8, 18.

---

## Day-2 operations

```powershell
# Env
kubectl set env deployment/user-ms-deployment SPRING_PROFILES_ACTIVE=test LOG_LEVEL=DEBUG
kubectl set env pods --all --list
kubectl exec -it deploy/user-ms-deployment -- env
kubectl set env deployment/user-ms-deployment LOG_LEVEL-

# Annotate / history / undo
kubectl annotate deployment/user-ms-deployment kubernetes.io/change-cause="Updated image to 1.0.1"
kubectl rollout history deployment/user-ms-deployment
kubectl rollout undo deployment/user-ms-deployment
kubectl rollout undo deployment/user-ms-deployment --to-revision=2

# Scale and logs
kubectl scale deployment/user-ms-deployment --replicas=2
kubectl logs -f deployment/user-ms-deployment --all-pods --prefix
```

`HOSTNAME` is already in the container (pod name). Pod IP, node name, CPU limit need the **Downward API** (`kubectl edit` / YAML `fieldRef`), not `kubectl set env`.

---

## Metrics, CPU, and resources

**Without Metrics Server** (CRI on the node). `crictl` `NAME` is the **container** (`users-ws-k8s`), not the pod name. `CPU %` is percent of one core (`0.14` ≈ 1.4m, idle).

```powershell
docker exec desktop-control-plane crictl stats --all | Select-String "users-ws-k8s"
```

**With Metrics Server** (`kubectl top`). On kind / Docker Desktop, add `--kubelet-insecure-tls`. PowerShell 5.1 strips JSON quotes on `-p '[{...}]'` — use a **patch file**:

```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
Set-Content -Path patch.json -Value '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' -Encoding ascii
kubectl patch deployment metrics-server -n kube-system --type=json --patch-file patch.json
kubectl top pods -l app=user-ms-deployment --sort-by=memory
```

**Request vs limit vs usage**

```powershell
kubectl set resources deployment/user-ms-deployment -c=users-ws-k8s --requests=cpu=100m --limits=cpu=250m
```

| | Meaning |
|---|---|
| request | Scheduler reservation |
| limit | Max CPU (throttle); memory over limit OOMKills |
| `crictl` / `kubectl top` | Actual use (idle JVM stays ~1m until you send traffic) |

`kubectl create deployment` sets **no** CPU limit. Check:

```powershell
kubectl get pods -l app=user-ms-deployment -o custom-columns="POD:.metadata.name,CPU_REQ:.spec.containers[*].resources.requests.cpu,CPU_LIM:.spec.containers[*].resources.limits.cpu"
```

---

## Two nodes (kind)

You cannot `kubectl scale` nodes. Recreate the kind cluster (this **deletes** current workloads):

```yaml
# kind-2nodes.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: desktop
nodes:
  - role: control-plane
  - role: worker
```

```powershell
kind delete cluster --name desktop
kind create cluster --name desktop --config kind-2nodes.yaml
kubectl get nodes
```

Then redeploy the app. Docker Desktop **kubeadm** stays one node (`docker-desktop`). Extra workers are a **kind** feature.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| `ImagePullBackOff` | Image name/tag, Hub login, public repo |
| `CrashLoopBackOff` | `kubectl logs <pod>` |
| `localhost:8082` refused | `kubectl get svc`, wait for LoadBalancer, or port-forward |
| Always the same `podName` | Keep-alive; or port-forward/exec to one pod |
| One Endpoint IP | Second pod not Ready; selector vs labels |
| `Metrics API not available` | metrics-server Ready + TLS patch **file** |
| `The request is invalid` on patch | PowerShell ate JSON quotes |
| Cursor paste joins two lines | Paste one PowerShell line at a time |

Full list: [DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md) section 16.

---

## Related documents

| File | What it is |
|---|---|
| **[Docker_Kind_Kubernetes_Commands_Cheatsheet.info](Docker_Kind_Kubernetes_Commands_Cheatsheet.info)** | Numbered commands: Docker Hub + Docker Desktop **kind** (`desktop-control-plane`, `crictl`) |
| **[Docker_kubeadm_Kubernetes_Commands_Cheatsheet.info](Docker_kubeadm_Kubernetes_Commands_Cheatsheet.info)** | Numbered commands: Docker Hub + Docker Desktop **kubeadm** (`docker-desktop`, reset-cluster) |
| **[Podman_Kubernetes_Commands_Cheatsheet.info](Podman_Kubernetes_Commands_Cheatsheet.info)** | Numbered commands: Quay.io + **Podman** (`quay.io/podmano/users-ws-k8s`) |
| **[DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md)** | Longer Docker Desktop / kind lab: cluster setup, deploy, Service, rollouts, env, metrics-server, CPU, two nodes, cleanup, command index |
| [`Dockerfile`](Dockerfile) | Image build |

### Guide map ([DOCKER_KUBERNETES_GUIDE.md](DOCKER_KUBERNETES_GUIDE.md))

| Section | Topic |
|---|---|
| 1–4 | Architecture, prerequisites, enable Kubernetes, single-node roles |
| 5–7 | Docker Hub, build/push, create Deployment + Service + Endpoints |
| 8 | Load balancing, keep-alive, port-forward |
| 9–11 | New image, env, annotate, undo, scale, logs |
| 12 | crictl vs Metrics Server, CPU request/limit |
| 13–14 | api-versions, two kind nodes |
| 15 | Cleanup / destroy (app, metrics-server, kind cluster) |
| 16–18 | Troubleshooting, command index, mental model |
