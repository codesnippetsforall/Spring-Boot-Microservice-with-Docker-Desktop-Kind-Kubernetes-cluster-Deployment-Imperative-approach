# Docker and Kubernetes Lab Guide

Project overview, APIs, and local run: **[README.md](README.md)**

Local setup of a Kubernetes cluster on a Windows machine (Docker Desktop) and deployment of the **Users** Spring Boot microservice.

This guide follows the imperative workflow in `Useful.info` and includes the practical notes from running that lab (PowerShell quoting, metrics-server, load balancing, CPU limits, pod identity).

---

## 1. What you are building

```text
Postman / browser
        |
        v
  localhost:8082
        |
        v
  Service  users-ms-service   (LoadBalancer, port 8082)
        |
        +-- kube-proxy picks one healthy Pod IP from Endpoints
        |
        v
  Deployment  user-ms-deployment
        |
        +-- Pod A  users-ws-k8s  :8082
        +-- Pod B  users-ws-k8s  :8082
        +-- ...
```

| Item | Value used in this lab |
|---|---|
| App | Users microservice (Spring Boot 3, Java 21, port **8082**) |
| Docker Hub repo | `dockermano/users-ws-k8s` |
| Image tags | `1.0.0`, then `1.0.1`, `latest` |
| Deployment | `user-ms-deployment` |
| Container name | `users-ws-k8s` (from the image name when using `kubectl create`) |
| Service | `users-ms-service` |
| Label | `app=user-ms-deployment` |

The Service is **not** a separate process that “thinks” on every HTTP click. Kubernetes stores Endpoints (pod IPs). **kube-proxy** on the node forwards each **TCP connection** to one of those IPs.

---

## 2. Prerequisites

Install and confirm:

| Tool | Check |
|---|---|
| Docker Desktop (Windows) | `docker version` |
| Kubernetes CLI | `kubectl version --client` |
| JDK 21 | `java -version` |
| Maven | `mvn -version` |
| Docker Hub account | browser login |

Give Docker Desktop enough RAM (8 GB+ recommended). Each Users pod uses roughly **250–280 MB**.

---

## 3. Enable Kubernetes on the local machine

### 3.1 Docker Desktop Kubernetes

1. Open **Docker Desktop** → **Settings** → **Kubernetes**.
2. Enable **Kubernetes**.
3. Apply and wait until Kubernetes is running (green).
4. Confirm:

```powershell
kubectl config current-context
kubectl get nodes
```

Docker Desktop’s built-in cluster usually shows a node named **`docker-desktop`**.

### 3.2 kind (what this lab’s node name indicates)

If `kubectl get pods -o wide` shows node **`desktop-control-plane`**, the cluster is **kind** (Kubernetes in Docker), not Docker Desktop’s built-in node.

kind names nodes `{cluster-name}-control-plane`. Cluster name `desktop` → node `desktop-control-plane`. That name is created with kind, not with `kubectl create deployment`.

```powershell
kind get clusters
docker ps --format "{{.Names}}"
```

Create a one-node kind cluster (if you do not have one yet):

```powershell
kind create cluster --name desktop
kubectl cluster-info
kubectl get nodes
```

You do **not** pass the control-plane name when deploying the app. The scheduler places pods on whatever nodes exist.

Both Docker Desktop Kubernetes and kind work with the commands below. Use the node name your `kubectl get nodes` actually prints.

---

## 4. Architecture on a single node (this lab)

This lab is a **single-node** cluster. That one node is **both** control plane and worker.

| Role | Components | On this machine |
|---|---|---|
| Control plane (old term: master) | API server, scheduler, controller-manager, etcd | `desktop-control-plane` (or `docker-desktop`) |
| Worker | kubelet, kube-proxy, **your app pods** | **same node** |

There is no “send the request to the master, then the master picks a worker.” HTTP traffic never goes through the API server. The API server only stores Deployment / Service / Endpoints objects.

Confirm:

```powershell
kubectl get nodes
kubectl get pods -o wide
```

The `NODE` column is the node that runs the pod.

---

## 5. Docker Hub repository

On [https://hub.docker.com](https://hub.docker.com) create a **public** repository:

```text
dockermano/users-ws-k8s
```

Log in from the machine that will push images:

```powershell
docker login
```

Do not put Docker Hub passwords, tokens, or kubeconfig secrets into Git.

---

## 6. Build the application image

From the project root (`UserMicroservice`):

```powershell
mvn -DskipTests package
```

Build and tag (preferred: two tags in one build):

```powershell
docker build -t dockermano/users-ws-k8s:latest -t dockermano/users-ws-k8s:1.0.0 --build-arg IMAGE_VERSION=1.0.0 .
```

Equivalent split steps:

```powershell
docker build -t dockermano/users-ws-k8s:latest --build-arg IMAGE_VERSION=1.0.0 .
docker tag dockermano/users-ws-k8s:latest dockermano/users-ws-k8s:1.0.0
```

The `Dockerfile` copies `target/*.jar`, runs on Amazon Corretto 21 Alpine, and starts the app on port **8082**.

Push:

```powershell
docker push dockermano/users-ws-k8s:1.0.0
```

---

## 7. Deploy the microservice (imperative)

### 7.1 Deployment

```powershell
kubectl create deployment user-ms-deployment --image dockermano/users-ws-k8s:1.0.0 --port=8082 --replicas=3
```

### 7.2 Status

```powershell
kubectl get deployments
kubectl get pods
kubectl describe deployment user-ms-deployment
```

`kubectl describe` shows replicas, image, ports, strategy, events, and container spec.

Wait until pods are `1/1 Running`. Image pull from Docker Hub needs network the first time.

### 7.3 Service (discovery + load balancer)

```powershell
kubectl expose deployment user-ms-deployment --type=LoadBalancer --port=8082 --target-port=8082 --name users-ms-service
```

Service types you will meet:

| Type | Typical use |
|---|---|
| ClusterIP | Default; reachable only inside the cluster |
| NodePort | Port on every node |
| LoadBalancer | Cloud LB, or Docker Desktop / kind publishing `localhost` |
| Ingress | HTTP routing (extra controller) |
| ExternalName | DNS alias |

```powershell
kubectl get services
kubectl describe service users-ms-service
```

### 7.4 Endpoints (the real backend list)

```powershell
kubectl get endpoints users-ms-service
```

Example:

```text
NAME               ENDPOINTS
users-ms-service   10.244.0.25:8082,10.244.0.26:8082
```

Comma-separated entries are **pod IPs**. Match them to pods:

```powershell
kubectl get pods -l app=user-ms-deployment -o wide
```

If only **one** IP is listed, the Service can only send traffic to that one pod.

### 7.5 Test from Docker Desktop / Postman

On Docker Desktop, LoadBalancer often maps to **`http://localhost:8082`**.

Useful APIs (this app):

| Method | Path |
|---|---|
| GET | `/users` |
| GET | `/users/{userId}` |
| POST | `/users` |

The JSON field **`podName`** is filled from `HOSTNAME` (the pod name in Kubernetes). Use it to see which replica handled the call.

---

## 8. How traffic is distributed

```text
localhost:8082  →  Service  →  kube-proxy  →  one Endpoint (pod IP:8082)
```

- **Keep-alive (Postman default):** one TCP connection → **same pod** for every click.
- **New connection each time:** kube-proxy picks again from the endpoint list (random, not strict A-B-A-B).

This is expected. The Service is already a load balancer. You do not add another balancer for this lab.

Force a new connection (PowerShell):

```powershell
1..20 | ForEach-Object {
  curl.exe -s -H "Connection: close" http://localhost:8082/users
  Write-Host "`n---- $_"
}
```

From **inside** the cluster (clearest proof of distribution on kind / Docker Desktop):

```powershell
kubectl run curlbox --rm -it --image=curlimages/curl --restart=Never -- sh -c "for i in 1 2 3 4 5 6 7 8 9 10; do curl -s http://users-ms-service:8082/users; echo; done"
```

Pin traffic to **one** pod (bypasses the Service):

```powershell
kubectl port-forward pod/user-ms-deployment-7f67689797-6cnjs 8082:8082
```

Replace the pod name with a name from `kubectl get pods`. `kubectl port-forward` and `kubectl exec deploy/...` always use **one** pod; they are not load balanced.

---

## 9. Change the app and roll out a new image

```powershell
mvn -DskipTests package
docker build -t dockermano/users-ws-k8s:1.0.1 .
docker push dockermano/users-ws-k8s:1.0.1
```

Point the Deployment at the new tag. Container name is **`users-ws-k8s`**:

```powershell
kubectl set image deployment/user-ms-deployment users-ws-k8s=dockermano/users-ws-k8s:1.0.1
```

Old pods are terminated; new pods are created.

```powershell
kubectl rollout history deployment/user-ms-deployment
kubectl annotate deployment/user-ms-deployment kubernetes.io/change-cause="Updated docker image into dockermano/users-ws-k8s:1.0.1"
kubectl get pods
```

### 9.1 `imagePullPolicy`

| Value | Behavior |
|---|---|
| `Always` | Always pull; picks up a changed image for the same tag |
| `IfNotPresent` | Pull only if the node does not already have the image |
| `Never` | Use only a local image; never pull |

Prefer a **new tag** (`1.0.1`) over mutating `latest`, so rollouts are obvious.

---

## 10. Environment variables and annotations

Set env on the Deployment (pods restart):

```powershell
kubectl set env deployment/user-ms-deployment SPRING_PROFILES_ACTIVE=test LOG_LEVEL=DEBUG
```

Kubernetes records a change-cause for this rollout. List env:

```powershell
kubectl set env pods --all --list
kubectl exec -it deploy/user-ms-deployment -- env
```

`kubectl exec deploy/...` attaches to **one** replica only.

Document a rollout:

```powershell
kubectl annotate deployment/user-ms-deployment kubernetes.io/change-cause="Set environment variables SPRING_PROFILES_ACTIVE=test, LOG_LEVEL=DEBUG"
```

Remove one variable (trailing `-`):

```powershell
kubectl set env deployment/user-ms-deployment LOG_LEVEL-
kubectl annotate deployment/user-ms-deployment kubernetes.io/change-cause="Removed environment variable LOG_LEVEL"
```

### 10.1 What is already in the container

| Env / source | Meaning |
|---|---|
| `HOSTNAME` | Pod name (used as `podName` in the API) |
| `KUBERNETES_SERVICE_HOST` / `_PORT` | Cluster API Service |
| `{SVC}_SERVICE_HOST` | Other Services in the namespace (Service links) |
| File `.../serviceaccount/namespace` | Namespace (not an env var by default) |

Pod IP, node name, namespace, CPU limit are **not** in env unless you inject the **Downward API** (`fieldRef` / `resourceFieldRef`). `kubectl set env` cannot attach those; use `kubectl edit` or YAML.

---

## 11. Rollout undo and scale

Previous revision:

```powershell
kubectl rollout undo deployment/user-ms-deployment
```

Specific revision:

```powershell
kubectl rollout undo deployment/user-ms-deployment --to-revision=2
kubectl rollout status deployment/user-ms-deployment
```

Scale:

```powershell
kubectl scale deployment/user-ms-deployment --replicas=1
kubectl scale deployment/user-ms-deployment --replicas=2
kubectl scale deployment/user-ms-deployment --replicas=5
```

Logs (all pods):

```powershell
kubectl logs -f deployment/user-ms-deployment --all-pods
kubectl logs -f deployment/user-ms-deployment --all-pods --prefix
```

`--prefix` prints the pod name on each line.

---

## 12. CPU and memory: usage vs limits

### 12.1 Node/container stats without Metrics Server

`crictl` talks to the container runtime **on the node**. Use the node container name from `docker ps`.

```powershell
docker exec desktop-control-plane crictl stats --all
docker exec desktop-control-plane crictl stats --all | Select-String "users-ws-k8s"
```

The `NAME` column is the **container** name (`users-ws-k8s`), not the pod name (`user-ms-deployment-...`).

`CPU %` in `crictl` is **percent of one core**, not millicores.

| `crictl` CPU % | Approx millicores |
|---|---|
| `0.14` | ~1.4m (idle JVM) |
| `10.00` | ~100m |

Idle Spring Boot sitting with no traffic will stay near `0.1`. Setting a request/limit does **not** raise usage. Generate HTTP load if you want to see ~100m.

Filter by pod-name regex (CRI pods, then stats):

```powershell
docker exec desktop-control-plane crictl pods --name user-ms
```

### 12.2 Metrics Server (`kubectl top`)

```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

On kind / Docker Desktop, kubelet certificates are not trusted. Append `--kubelet-insecure-tls`.

**PowerShell 5.1 strips JSON quotes**, so this fails:

```powershell
kubectl patch ... --type=json -p '[{"op":"add",...}]'
```

Use a patch file (Windows):

```powershell
Set-Content -Path patch.json -Value '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' -Encoding ascii
kubectl patch deployment metrics-server -n kube-system --type=json --patch-file patch.json
```

Wait until the metrics-server pod is `1/1 Running`, then:

```powershell
kubectl -n kube-system get pods -l k8s-app=metrics-server
kubectl get apiservice v1beta1.metrics.k8s.io
kubectl top nodes
kubectl top pods
kubectl top pods -l app=user-ms-deployment --sort-by=memory
kubectl top pods -l app=user-ms-deployment --sort-by=cpu
```

`Metrics API not available` means the APIService is not Ready yet (pod still creating, TLS, or image pull). Do not keep retrying `kubectl top` without checking the pod.

Pasting two PowerShell lines into the Cursor terminal often **drops the newline**. Paste one line at a time, or join with `;`.

### 12.3 CPU request and limit (imperative)

These values **reserve** (request) and **cap** (limit) CPU. They do not make an idle pod use 100m.

```powershell
kubectl set resources deployment/user-ms-deployment -c=users-ws-k8s --requests=cpu=100m --limits=cpu=250m
```

| Flag | Meaning |
|---|---|
| `--requests=cpu=100m` | Scheduler guarantees 100m; pod will not land on a node that cannot spare it |
| `--limits=cpu=250m` | Max 250m; extra CPU is **throttled** (not killed). Memory over limit **does** OOMKill |

`kubectl create deployment` sets **no** CPU limit. Check:

```powershell
kubectl get deployment user-ms-deployment -o jsonpath="{range .spec.template.spec.containers[*]}{.name}{'\n'}CPU request: {.resources.requests.cpu}{'\n'}CPU limit: {.resources.limits.cpu}{'\n'}{end}"

kubectl get pods -l app=user-ms-deployment -o custom-columns="POD:.metadata.name,CPU_REQ:.spec.containers[*].resources.requests.cpu,CPU_LIM:.spec.containers[*].resources.limits.cpu"
```

Blank / `<none>` means unlimited (no cap). Clear limits:

```powershell
kubectl set resources deployment/user-ms-deployment -c=users-ws-k8s --requests=cpu=0 --limits=cpu=0
```

---

## 13. API versions

```powershell
kubectl api-versions
```

---

## 14. Two nodes (optional)

You cannot `kubectl scale` nodes. kind cannot add a worker to a **running** cluster; you recreate it.

This **deletes** Deployments, Services, and pods on that cluster.

`kind-2nodes.yaml`:

```yaml
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

Expected:

```text
desktop-control-plane   Ready   control-plane
desktop-worker          Ready   <none>
```

Then repeat sections 7–8 to deploy the app again. `kubectl get pods -o wide` may show different `NODE` values. Docker Desktop’s **built-in** Kubernetes stays one node (`docker-desktop`); extra nodes need kind.

---

## 15. Declarative alternative (YAML in `kubernetes/`)

The same app can be applied from files (different names than the imperative lab):

```powershell
kubectl apply -f kubernetes/users-config.yaml
kubectl apply -f kubernetes/users-mysql-secret.yaml
kubectl apply -f kubernetes/users-deployment.yaml
kubectl apply -f kubernetes/users-service.yaml
```

Those manifests use Deployment `users-deployment`, Service `users-service`, and image `skargopolov/users-ws-public-demo`. Keep **one** style per lab (imperative `user-ms-deployment` **or** these YAML names) so you do not run two copies on port 8082.

Do not commit real database passwords. The sample Secret is for local learning only.

---

## 16. Troubleshooting

| Symptom | What to check |
|---|---|
| Pod `ImagePullBackOff` | Image name/tag, `docker login`, public repo |
| Pod `CrashLoopBackOff` | `kubectl logs <pod>` |
| `localhost:8082` connection refused | `kubectl get svc`, LoadBalancer / port-forward |
| Always the same `podName` | Keep-alive, or `port-forward` / `exec` to one pod |
| `kubectl get endpoints` has one IP | Second pod not Ready, or label/selector mismatch |
| `Metrics API not available` | metrics-server pod + `--kubelet-insecure-tls` via **patch file** |
| `The request is invalid` on `kubectl patch` | PowerShell ate JSON quotes; use `--patch-file` |
| Cursor terminal concatenates two pasted lines | Paste one line at a time |
| `Unexpected token 'kubectl'` | Two commands pasted as one line |

---

## 17. Command index (from `Useful.info`)

```powershell
# Image
mvn -DskipTests package
docker build -t dockermano/users-ws-k8s:latest -t dockermano/users-ws-k8s:1.0.0 --build-arg IMAGE_VERSION=1.0.0 .
docker push dockermano/users-ws-k8s:1.0.0

# Deploy
kubectl create deployment user-ms-deployment --image dockermano/users-ws-k8s:1.0.0 --port=8082 --replicas=3
kubectl get deployments
kubectl get pods
kubectl describe deployment user-ms-deployment
kubectl expose deployment user-ms-deployment --type=LoadBalancer --port=8082 --target-port=8082 --name users-ms-service
kubectl get services
kubectl describe service users-ms-service
kubectl get endpoints users-ms-service

# Rollout
kubectl set image deployment/user-ms-deployment users-ws-k8s=dockermano/users-ws-k8s:1.0.1
kubectl rollout history deployment/user-ms-deployment
kubectl annotate deployment/user-ms-deployment kubernetes.io/change-cause="Updated docker image into dockermano/users-ws-k8s:1.0.1"
kubectl set env deployment/user-ms-deployment SPRING_PROFILES_ACTIVE=test LOG_LEVEL=DEBUG
kubectl set env pods --all --list
kubectl set env deployment/user-ms-deployment LOG_LEVEL-
kubectl rollout undo deployment/user-ms-deployment
kubectl rollout undo deployment/user-ms-deployment --to-revision=2
kubectl rollout status deployment/user-ms-deployment
kubectl scale deployment/user-ms-deployment --replicas=2
kubectl logs -f deployment/user-ms-deployment --all-pods

# Metrics and resources
docker exec desktop-control-plane crictl stats --all
kubectl top pods
kubectl api-versions
kubectl set resources deployment/user-ms-deployment -c=users-ws-k8s --requests=cpu=100m --limits=cpu=250m
kubectl exec -it deploy/user-ms-deployment -- env
kubectl port-forward pod/<pod-name> 8082:8082
```

---

## 18. Mental model (keep this)

1. **Docker** packages the JAR into an image and stores it on Docker Hub.
2. **Deployment** says how many copies of that image to run.
3. **Pods** are those copies; each has a name, an IP, and `HOSTNAME`.
4. **Service** holds a stable name/IP and an Endpoints list of healthy pod IPs.
5. **kube-proxy** forwards each **connection** to one endpoint.
6. **Control plane** stores desired state; it does not sit in the HTTP path.
7. On this laptop, control plane and worker are usually **the same node**.
