# 🚀 Deployment Guide — Zero Cost, Full Production

## Cost Breakdown (Everything Free)

| Component | Service | Cost |
|---|---|---|
| Source code & CI/CD | GitHub + GitHub Actions | **$0** (2,000 min/month free) |
| Container registry | GitHub Container Registry (GHCR) | **$0** |
| Cloud server | Oracle Cloud Always Free | **$0 forever** (4 CPU, 24 GB RAM) |
| Kubernetes | k3s (lightweight K8s) | **$0** (open source) |
| TLS certificate | Let's Encrypt via cert-manager | **$0** |
| Domain (optional) | Freenom / DuckDNS | **$0** |

---

## Step 1 — Initialize Git & Push to GitHub

```bash
cd /Users/ishaanvijaywargia/Documents/Projects/adaptive-lms

# Initialize git
git init
git branch -M main

# Stage everything (secrets are .gitignored)
git add .
git commit -m "feat: initial commit — adaptive LMS"

# Create repo on GitHub (go to github.com → New Repository → adaptive-lms → Private)
# Then:
git remote add origin https://github.com/YOUR_USERNAME/adaptive-lms.git
git push -u origin main
```

---

## Step 2 — Add GitHub Secrets

In your GitHub repo → **Settings → Secrets and Variables → Actions**, add these:

| Secret Name | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://your-domain.com/api` |
| `VITE_KEYCLOAK_URL` | `https://your-domain.com/auth` |
| `VITE_WS_URL` | `wss://your-domain.com/ws` |
| `SERVER_HOST` | Your Oracle Cloud server IP |
| `SERVER_USER` | `ubuntu` (default Oracle Cloud user) |
| `SSH_PRIVATE_KEY` | Your SSH private key (contents of `~/.ssh/id_rsa`) |

> The `GITHUB_TOKEN` secret is **automatically provided** by GitHub Actions — you do NOT need to create it.

---

## Step 3 — Get Oracle Cloud Free Tier Server

1. Go to [cloud.oracle.com](https://cloud.oracle.com) → Sign up (credit card required for identity verification, but **nothing is charged**)
2. Create a **VM Instance**:
   - Shape: **VM.Standard.A1.Flex** (ARM) → 4 OCPU, 24 GB RAM — **Always Free**
   - OS: Ubuntu 22.04
   - Add your SSH public key
3. Note the **public IP address**

---

## Step 4 — Set Up the Server

SSH into your server and run:

```bash
# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu
newgrp docker

# Install k3s (lightweight Kubernetes — single command)
curl -sfL https://get.k3s.io | sh -

# Configure kubectl
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config

# Verify k3s is running
kubectl get nodes
```

---

## Step 5 — Deploy Infrastructure to Kubernetes

```bash
# On your server, clone your repo
git clone https://github.com/YOUR_USERNAME/adaptive-lms.git ~/adaptive-lms
cd ~/adaptive-lms

# Create namespace
kubectl apply -f k8s/00-namespace.yaml

# Create secrets (copy the example, fill in real values, then apply)
cp k8s/01-secrets.yaml.example k8s/01-secrets.yaml
nano k8s/01-secrets.yaml   # Fill in your passwords
kubectl apply -f k8s/01-secrets.yaml

# Deploy all infrastructure (order matters: DB first)
kubectl apply -f k8s/02-postgres.yaml
kubectl apply -f k8s/03-redis.yaml
kubectl apply -f k8s/04-kafka.yaml
kubectl apply -f k8s/05-minio.yaml
kubectl apply -f k8s/06-ai-infra.yaml
kubectl apply -f k8s/07-keycloak.yaml

# Wait for all pods to be ready
kubectl get pods -n lms -w

# Copy Keycloak realm config
kubectl create configmap keycloak-realm \
  --from-file=infra/keycloak/ -n lms

# Deploy the app
kubectl apply -f k8s/08-backend.yaml
kubectl apply -f k8s/09-frontend.yaml
```

---

## Step 6 — Set Up Ingress + TLS

```bash
# Install ingress-nginx (free, one command)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/baremetal/deploy.yaml

# Install cert-manager (free TLS via Let's Encrypt)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml

# Edit the ingress to set your domain
nano k8s/10-ingress.yaml   # Replace "your-domain.com" with your real domain/IP
kubectl apply -f k8s/10-ingress.yaml
```

---

## Step 7 — Enable CD Auto-Deploy

Add these Secrets to GitHub (Step 2) and the CD pipeline will automatically:
1. Build Docker images on every push to `main`
2. Push them to GHCR (free)
3. SSH into your Oracle Cloud server and do a **zero-downtime rolling update**

---

## How to Update Your App Going Forward

```bash
# On your local machine — just push code:
git add .
git commit -m "feat: my new feature"
git push origin main

# → GitHub Actions CI runs (builds + type checks) ← automatic
# → GitHub Actions CD runs (builds Docker + deploys) ← automatic
# → Your live server updates with zero downtime ← automatic
```

---

## Useful Commands

```bash
# Check pod status
kubectl get pods -n lms

# View backend logs
kubectl logs -n lms -l app=backend -f

# View frontend logs  
kubectl logs -n lms -l app=frontend -f

# Restart backend (force re-pull image)
kubectl rollout restart deployment/backend -n lms

# Scale backend to 2 replicas (when you need more capacity)
kubectl scale deployment/backend --replicas=2 -n lms

# Check resource usage
kubectl top pods -n lms
```
