# EventsNest — Production Deployment Guide

Comprehensive guide for deploying EventsNest to AWS EKS with managed MySQL (RDS),
managed Kafka (in-cluster Bitnami chart), HTTPS via CloudFront, and CI/CD via
GitHub Actions.

This guide covers **macOS, Linux, and Windows**. Where commands differ by OS, both
versions are shown.

---

## Table of contents

1. [Architecture overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Backend hardening (must do before deploying)](#3-backend-hardening-must-do-before-deploying)
4. [AWS account setup](#4-aws-account-setup)
5. [Step-by-step deployment](#5-step-by-step-deployment)
   - [5.1 Create ECR repository](#51-create-ecr-repository)
   - [5.2 Provision the EKS cluster](#52-provision-the-eks-cluster)
   - [5.3 Create RDS MySQL in the EKS VPC](#53-create-rds-mysql-in-the-eks-vpc)
   - [5.4 Store secrets in AWS Secrets Manager](#54-store-secrets-in-aws-secrets-manager)
   - [5.5 Wire RDS ↔ EKS networking](#55-wire-rds--eks-networking)
   - [5.6 Bootstrap cluster add-ons](#56-bootstrap-cluster-add-ons)
   - [5.7 Apply Kubernetes manifests](#57-apply-kubernetes-manifests)
   - [5.8 IAM user for the GitHub Actions pipeline](#58-iam-user-for-the-github-actions-pipeline)
   - [5.9 GitHub repo secrets + workflow](#59-github-repo-secrets--workflow)
   - [5.10 First deploy](#510-first-deploy)
   - [5.11 HTTPS via CloudFront](#511-https-via-cloudfront)
   - [5.12 Frontend hookup](#512-frontend-hookup)
6. [Verification & smoke tests](#6-verification--smoke-tests)
7. [Day-2 operations](#7-day-2-operations)
8. [Cleanup & cost control](#8-cleanup--cost-control)
9. [Things we got wrong (and how to avoid them)](#9-things-we-got-wrong-and-how-to-avoid-them)
10. [Troubleshooting reference](#10-troubleshooting-reference)
11. [Glossary](#11-glossary)

---

## 1. Architecture overview

```
┌──────────────── Internet ────────────────┐
                       │
                       ▼  HTTPS
            ┌──────────────────────┐
            │     CloudFront       │  free TLS, dXXXX.cloudfront.net
            │   (or ALB + ACM      │
            │    if you have a     │
            │    domain)           │
            └──────────┬───────────┘
                       │  HTTP
                       ▼
            ┌──────────────────────┐
            │  Application LB      │  internet-facing, auto-created by
            │  (ALB Ingress        │  the AWS Load Balancer Controller
            │   Controller)        │  from the Ingress resource
            └──────────┬───────────┘
                       │
   ┌───────────────────┴────────────────────┐
   │   EKS cluster  (events-nest-prod)      │
   │   eu-north-1, 1 node group, t3.medium  │
   │   ┌──────────────────────────────────┐ │
   │   │ namespace: events-nest           │ │
   │   │  ├ Deployment: events-nest-server│ │
   │   │  │   (Spring Boot 4 / Java 25)   │ │
   │   │  ├ StatefulSet: kafka            │ │
   │   │  │   (Bitnami chart, KRaft)      │ │
   │   │  ├ Service + Ingress             │ │
   │   │  ├ ExternalSecret app-secrets    │ │
   │   │  └ ServiceMonitor                │ │
   │   ├──────────────────────────────────┤ │
   │   │ namespace: external-secrets      │ │
   │   │  └ External Secrets Operator     │ │
   │   ├──────────────────────────────────┤ │
   │   │ namespace: kube-system           │ │
   │   │  └ AWS Load Balancer Controller  │ │
   │   ├──────────────────────────────────┤ │
   │   │ namespace: monitoring            │ │
   │   │  ├ Prometheus + Grafana          │ │
   │   │  └ AlertManager                  │ │
   │   └──────────────────────────────────┘ │
   └────────────────┬───────────────────────┘
                    │
                    ▼
        ┌────────────────────────┐
        │  RDS MySQL 8.0         │  daily snapshots, 7-day retention
        │  db.t4g.micro          │  point-in-time recovery enabled
        │  Private subnets only  │
        └────────────────────────┘
                    ▲
                    │ same VPC, SG-to-SG
                    │
        ┌───────────────────────┐
        │  AWS Secrets Manager  │  eventsnest/prod/app
        │  - ADMIN_*            │  pulled into k8s Secret by ESO
        │  - BREVO_API_KEY      │  via IRSA (no static AWS keys
        │  - JWT_SIGNING_KEY    │   inside the cluster)
        │  - SPRING_DATASOURCE_*│
        │  - APP_CORS_*         │
        └───────────────────────┘

┌─────────── GitHub repo ───────────┐
│  push to main                     │
│   ↓                               │
│  GitHub Actions                   │
│   ├ mvn test                      │
│   ├ docker build                  │
│   ├ push to ECR                   │       OIDC or static creds
│   └ kubectl apply ──────────────────────────────────────┐
└───────────────────────────────────┘                     │
                                                          │
                       ┌──────────────────────────────────┘
                       │
                       ▼
             ┌─────────────────────┐
             │   ECR private       │
             │   events-nest       │
             │   (image registry)  │
             └─────────────────────┘
```

### Cost expectation

| Item | $/month | $/2 weeks |
|---|---|---|
| EKS control plane | ~$73 | ~$33 |
| 1× t3.medium node | ~$30 | ~$13 (or $26 if 2 nodes) |
| ALB | ~$20 | ~$9 |
| RDS db.t4g.micro | ~$13 (or $0 free tier) | ~$6 (or $0) |
| EBS volumes | ~$3 | ~$1.50 |
| CloudFront | ~$0 (well within free tier) | ~$0 |
| Data transfer | ~$5 | ~$3 |
| ECR storage | ~$1 | ~$0.50 |
| **Total (1-node)** | **~$145** | **~$66** |
| **Total (2-node)** | **~$160** | **~$79** |

**Tear-down brings it to ~$13/mo (just RDS) or ~$0 if you also delete RDS.**

---

## 2. Prerequisites

### Tools — install all four

| Tool | What it does |
|---|---|
| `aws` | AWS CLI v2 — talks to all AWS services |
| `eksctl` | Bootstraps the EKS cluster (skips ~30 console clicks) |
| `kubectl` | Talks to the cluster's Kubernetes API |
| `helm` | Installs Kubernetes apps via charts |

#### macOS

```bash
brew install awscli eksctl kubectl helm
```

#### Linux (Debian/Ubuntu)

```bash
# AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install

# eksctl
curl --silent --location "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# helm
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

#### Linux (RHEL/Fedora)

```bash
sudo dnf install -y unzip curl tar
# (then same curl-based installs as Debian above)
```

#### Windows

Install via [Chocolatey](https://chocolatey.org/) (run PowerShell as Administrator):

```powershell
choco install awscli eksctl kubernetes-cli kubernetes-helm -y
```

Or via [Scoop](https://scoop.sh/):

```powershell
scoop install aws eksctl kubectl helm
```

Or via `winget`:

```powershell
winget install Amazon.AWSCLI
winget install Weaveworks.eksctl
winget install Kubernetes.kubectl
winget install Helm.Helm
```

> **Windows shell choice**: examples in this guide use bash syntax. Use **Git Bash**
> (ships with [Git for Windows](https://git-scm.com/download/win)) or **WSL2** for
> drop-in compatibility. PowerShell works for AWS CLI + kubectl + helm but heredocs
> and shell-substitution syntax differs — see [§9.W1](#w1-bash-only-syntax-on-windows).

### Verify

```bash
aws --version          # aws-cli/2.x
eksctl version         # 0.190.x or newer
kubectl version --client
helm version
```

### Other things you need

- An **AWS account** with billing enabled (Free Tier doesn't cover EKS — see [§9.4](#94-aws-free-tier-plan-blocking-t3medium-launches))
- A **GitHub account** with the EventsNest repo
- **Docker** installed locally if you want to test the build before pushing
- A **credit card** to verify the AWS account if it's new

---

## 3. Backend hardening (must do before deploying)

These are the changes that turn a "works on my laptop" Spring Boot project into
something safe to deploy. Skip them and you'll spend hours debugging in
production.

### 3.1 Externalize secrets

**Don't bake secrets into the runnable jar.** The `src/main/resources/env.properties`
pattern that ships with many starter projects gets packaged into the
`BOOT-INF/classes/env.properties` of the jar — anyone with the artifact can `unzip`
it and read your credentials.

**What to do:**

1. Delete `src/main/resources/env.properties` and `src/main/resources/env.example`
2. Move secrets to env vars in `application.properties`:
   ```properties
   admin.password=${ADMIN_PASSWORD}
   jwt.signing.key=${JWT_SIGNING_KEY}
   brevo.api-key=${BREVO_API_KEY:}
   ```
3. Use `${VAR}` (no default) for required secrets so the app fails fast if missing
4. Use `${VAR:default}` only for non-sensitive defaults
5. Create `./env.example` at the project root documenting every var
6. Create `./.env` (gitignored) for local dev
7. In `compose.yaml`, use `env_file: - .env` to inject locally

`.gitignore` should include:
```
.env
.env.local
.env.*.local
```

### 3.2 Schema migrations via Flyway

**Don't rely on `ddl-auto=update`.** Hibernate's `update` mode silently fails to
propagate certain schema changes (column nullability, type changes) — you end
up with `NOT NULL` constraints in production that don't exist in your entities.

**What to do:**

1. Add to `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-flyway</artifactId>
   </dependency>
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-mysql</artifactId>
   </dependency>
   ```
   > Spring Boot 4 split `FlywayAutoConfiguration` into the separate
   > `spring-boot-flyway` artifact. Without it, Flyway never starts.

2. Create `src/main/resources/db/migration/V1__baseline_schema.sql` with the full
   schema (dump from your dev DB with `mysqldump --no-data`).

3. In `application.properties`:
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   spring.flyway.enabled=true
   spring.flyway.baseline-on-migrate=true
   spring.flyway.baseline-version=1
   ```

4. Disable Flyway in the test profile (use H2 + `create-drop` instead):
   ```properties
   # src/test/resources/application-test.properties
   spring.flyway.enabled=false
   spring.jpa.hibernate.ddl-auto=create-drop
   ```

### 3.3 Health endpoints + Docker healthcheck

**A deploy isn't done until orchestrators can probe its health.**

1. Add to `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

2. In `application.properties`:
   ```properties
   management.endpoints.web.exposure.include=health,info,prometheus
   management.endpoint.health.probes.enabled=true
   management.endpoint.health.show-details=always
   management.health.livenessstate.enabled=true
   management.health.readinessstate.enabled=true
   # If using Brevo HTTP API (not SMTP), disable the auto-mail-probe:
   management.health.mail.enabled=false
   ```

3. Permit the actuator endpoints in `SecurityConfig.java`:
   ```java
   .requestMatchers("/actuator/health", "/actuator/health/**",
                    "/actuator/info", "/actuator/prometheus").permitAll()
   ```

4. Add a Docker healthcheck in `compose.yaml` (or rely on k8s probes):
   ```yaml
   healthcheck:
     test: ["CMD", "bash", "-c",
       "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -qE '^HTTP/1\\.[01] 200'"]
     interval: 10s
     timeout: 3s
     retries: 5
     start_period: 30s
   ```

### 3.4 CORS allow-list (avoid the spec-broken `*` + credentials combo)

```java
@Bean
public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(allowedOrigins);
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

In `application.properties`:
```properties
app.cors.allowed-origins=${APP_CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

### 3.5 Verify backend prep before continuing

```bash
mvn test
docker compose up -d --build
curl http://localhost:8080/actuator/health
docker compose down
```

Expected: tests green, container reports `healthy` within 30s, `/actuator/health`
returns `{"status":"UP",...}`.

---

## 4. AWS account setup

### 4.1 Upgrade from Free Tier plan

**Critical for new accounts.** AWS introduced a "Free Tier plan" in 2024 that
locks new accounts to free-tier-eligible resources only — meaning you **cannot
launch a `t3.medium` until you upgrade**. The error looks like:

> "The specified instance type is not eligible for Free Tier"

**Fix:**

Console → top-right account dropdown → **Account** → **Account plan** →
**Upgrade to Pay-As-You-Go**. Takes 30 seconds. You're not committing to
spend — you just gain the ability to launch paid resources. Free-tier-eligible
resources still don't cost anything.

Verify with a dry-run:

```bash
aws ec2 run-instances --dry-run \
  --image-id ami-09a9858973b288bdd \
  --instance-type t3.medium \
  --region eu-north-1 2>&1 | grep -E "DryRunOperation|Free Tier"
```

`DryRunOperation` → upgraded, good. `Free Tier` error → upgrade didn't apply.

### 4.2 Configure AWS CLI with admin credentials

You need an admin-level IAM user (or root, temporarily) for the cluster bootstrap.
The CI/CD user we create later only has narrow ECR + EKS describe perms.

**Create a dedicated admin user** (recommended):

1. IAM → Users → Create user → name `eventsnest-admin`
2. Attach `AdministratorAccess` policy
3. Create access key → Application running outside AWS → save key + secret

Configure the CLI:

```bash
aws configure
# AWS Access Key ID: <paste>
# AWS Secret Access Key: <paste>
# Default region name: eu-north-1
# Default output format: json
```

Verify:

```bash
aws sts get-caller-identity
# Should print your account ID + the user ARN
```

### 4.3 Set billing alarms (do this before anything else)

AWS bills can spiral fast. Set up two alarms:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
EMAIL=you@example.com

aws budgets create-budget \
  --account-id $ACCOUNT_ID \
  --budget "{
    \"BudgetName\": \"eventsnest-50\",
    \"BudgetLimit\": {\"Amount\": \"50\", \"Unit\": \"USD\"},
    \"TimeUnit\": \"MONTHLY\",
    \"BudgetType\": \"COST\"
  }" \
  --notifications-with-subscribers "[{
    \"Notification\": {
      \"NotificationType\": \"ACTUAL\",
      \"ComparisonOperator\": \"GREATER_THAN\",
      \"Threshold\": 100
    },
    \"Subscribers\": [{\"SubscriptionType\": \"EMAIL\", \"Address\": \"$EMAIL\"}]
  }]"
```

Repeat with `eventsnest-80` at $80. Console alternative: Billing → Budgets → Create budget.

---

## 5. Step-by-step deployment

### 5.1 Create ECR repository

```bash
aws ecr create-repository \
  --repository-name events-nest \
  --region eu-north-1 \
  --image-scanning-configuration scanOnPush=true
```

Note the URI returned, e.g. `123456789012.dkr.ecr.eu-north-1.amazonaws.com/events-nest`.

### 5.2 Provision the EKS cluster

Save as `infra/eks/cluster.yml` in your repo:

```yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: events-nest-prod
  region: eu-north-1
  version: "1.30"

iam:
  withOIDC: true   # required for IRSA — IAM Roles for Service Accounts

managedNodeGroups:
  - name: workers
    instanceType: t3.medium
    minSize: 1
    maxSize: 3
    desiredCapacity: 2          # 2 nodes — t3.medium hits a 17-pod limit per node (see §9.5)
    volumeSize: 30
    privateNetworking: false    # public subnets only — saves $32/mo NAT gateway
    iam:
      withAddonPolicies:
        imageBuilder: true
        autoScaler: true
        cloudWatch: true

addons:
  - name: vpc-cni
  - name: coredns
  - name: kube-proxy
  - name: aws-ebs-csi-driver
```

Run:

```bash
eksctl create cluster -f infra/eks/cluster.yml
```

Takes **15–25 min**. CloudFormation creates a VPC, subnets across 3 AZs, IAM
roles, the EKS control plane, and the node group.

When done:

```bash
kubectl get nodes
# Should show 2 nodes Ready
```

### 5.3 Create RDS MySQL in the EKS VPC

> **Critical**: RDS must go into the **EKS VPC**, not the default VPC. AWS won't
> allow security groups to reference each other across VPCs. See [§9.1](#91-rds-in-the-wrong-vpc).

#### Get the EKS VPC + private subnet IDs

```bash
EKS_VPC=$(aws eks describe-cluster --name events-nest-prod --region eu-north-1 \
  --query 'cluster.resourcesVpcConfig.vpcId' --output text)
echo "EKS VPC: $EKS_VPC"

aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$EKS_VPC" "Name=tag:Name,Values=*Private*" \
  --region eu-north-1 \
  --query 'Subnets[].[SubnetId,AvailabilityZone,CidrBlock]' \
  --output table
```

Note the three private subnet IDs.

#### Create a DB subnet group

Console → RDS → Subnet groups → Create:
- Name: `eventsnest-db-subnets`
- VPC: the EKS VPC
- Add the three **private** subnets only (skip public)

#### Create the DB security group

EC2 → Security Groups → Create security group:
- Name: `eventsnest-rds-sg`
- VPC: the EKS VPC
- Inbound: leave empty for now — we'll add the EKS node SG in [§5.5](#55-wire-rds--eks-networking)

#### Create the RDS instance

Console → RDS → Create database (use **Full configuration**, not Easy create):

| Field | Value |
|---|---|
| Engine | MySQL 8.0.x |
| Template | Free tier (if eligible) else Dev/Test |
| DB instance identifier | `events-nest-db-prod` |
| Master username | `admin` |
| Credentials management | **Self managed** (see [§9.7](#97-credentials-management-managed-in-secrets-manager-vs-self-managed)) |
| Master password | `openssl rand -base64 32` (save it somewhere) |
| Instance class | `db.t4g.micro` |
| Storage | 20 GiB gp3 |
| VPC | The EKS VPC |
| DB subnet group | `eventsnest-db-subnets` |
| Public access | **No** |
| VPC security group | Choose existing → `eventsnest-rds-sg` |
| **Initial database name** | `events_nest_db` ⚠️ **don't skip this** (see [§9.2](#92-rds-instance-id-vs-database-name)) |
| Backup retention | **7 days** |
| Encryption | enabled |

Wait ~5–10 min. Note the endpoint:

```bash
aws rds describe-db-instances \
  --db-instance-identifier events-nest-db-prod \
  --region eu-north-1 \
  --query 'DBInstances[0].Endpoint.Address' --output text
```

### 5.4 Store secrets in AWS Secrets Manager

Console → Secrets Manager → Store a new secret:
- Type: **Other type of secret** → **Plaintext** mode
- Paste the JSON below, replacing every `<...>`:

```json
{
  "ADMIN_EMAIL": "admin@eventsnest.com",
  "ADMIN_PASSWORD": "<strong-password-32+-chars>",
  "ADMIN_FIRST_NAME": "Platform",
  "ADMIN_LAST_NAME": "Admin",
  "MAIL_PROVIDER": "brevo",
  "BREVO_API_KEY": "<your-brevo-key>",
  "FRONTEND_URL": "https://eventsnest.your-domain.com",
  "JWT_SIGNING_KEY": "<openssl-rand-base64-48-output>",
  "SPRING_DATASOURCE_URL": "jdbc:mysql://<rds-endpoint>:3306/events_nest_db",
  "SPRING_DATASOURCE_USERNAME": "admin",
  "SPRING_DATASOURCE_PASSWORD": "<rds-master-password>",
  "APP_CORS_ALLOWED_ORIGINS": "https://eventsnest.vercel.app,https://eventsnest-*.vercel.app"
}
```

- Name: `eventsnest/prod/app`
- Encryption: default AWS-managed key
- **Store**

### 5.5 Wire RDS ↔ EKS networking

Get the EKS node security group ID:

```bash
NODE_SG=$(aws eks describe-cluster --name events-nest-prod --region eu-north-1 \
  --query 'cluster.resourcesVpcConfig.clusterSecurityGroupId' --output text)
echo "Node SG: $NODE_SG"
```

Find the RDS security group ID:

```bash
RDS_SG=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=eventsnest-rds-sg" \
  --region eu-north-1 \
  --query 'SecurityGroups[0].GroupId' --output text)
echo "RDS SG: $RDS_SG"
```

Add the inbound rule:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG \
  --protocol tcp \
  --port 3306 \
  --source-group $NODE_SG \
  --region eu-north-1
```

Verify:

```bash
aws ec2 describe-security-groups --group-ids $RDS_SG \
  --region eu-north-1 \
  --query 'SecurityGroups[0].IpPermissions' \
  --output table
```

You should see one rule: TCP 3306, source = the EKS node SG.

### 5.6 Bootstrap cluster add-ons

Run **in this order** — each step depends on the previous one having installed CRDs.

```bash
# 1. OIDC provider (required for IRSA — re-running is a no-op if eksctl already enabled it)
eksctl utils associate-iam-oidc-provider \
  --cluster events-nest-prod --approve

# 2. AWS Load Balancer Controller
helm repo add eks https://aws.github.io/eks-charts
helm repo update

curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json 2>&1 | grep -v EntityAlreadyExists || true

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
eksctl create iamserviceaccount \
  --cluster events-nest-prod \
  --namespace kube-system \
  --name aws-load-balancer-controller \
  --attach-policy-arn arn:aws:iam::$ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts --approve

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=events-nest-prod \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller

# 3. External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm repo update
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace

# 4. IAM service account for ESO to read AWS Secrets Manager
kubectl create namespace events-nest --dry-run=client -o yaml | kubectl apply -f -
eksctl create iamserviceaccount \
  --cluster events-nest-prod \
  --namespace events-nest \
  --name external-secrets-sa \
  --attach-policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite \
  --override-existing-serviceaccounts --approve

# 5. Kafka (Bitnami chart, single broker, ephemeral)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm install kafka bitnami/kafka -n events-nest \
  --set kraft.enabled=true \
  --set controller.replicaCount=1 \
  --set broker.replicaCount=0 \
  --set controller.persistence.enabled=false \
  --set listeners.client.protocol=PLAINTEXT

# 6. kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword='choose-a-real-password' \
  --timeout 15m
```

**Don't Ctrl+C the helm installs.** kube-prometheus-stack takes 1–3 min on small
clusters (see [§9.6](#96-helm-install-stuck-pending-install-after-ctrlc)).

Verify:

```bash
helm list -A
# All four releases should show STATUS: deployed

kubectl get pods -A
# All pods should be Running (give it a few minutes for everything to settle)
```

### 5.7 Apply Kubernetes manifests

Create these files under `infra/k8s/`:

**`infra/k8s/namespace.yaml`**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: events-nest
  labels:
    name: events-nest
```

**`infra/k8s/secret-store.yaml`** — note the API version is **`v1`**, not `v1beta1` (see [§9.3](#93-external-secrets-api-version-v1beta1-vs-v1)):
```yaml
apiVersion: external-secrets.io/v1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: events-nest
spec:
  provider:
    aws:
      service: SecretsManager
      region: eu-north-1
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets-sa
```

**`infra/k8s/external-secret.yaml`**:
```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: app-secrets
  namespace: events-nest
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: app-secrets
    creationPolicy: Owner
  dataFrom:
    - extract:
        key: eventsnest/prod/app
```

**`infra/k8s/deployment.yaml`**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: events-nest-server
  namespace: events-nest
  labels:
    app: events-nest-server
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: events-nest-server
  template:
    metadata:
      labels:
        app: events-nest-server
    spec:
      containers:
        - name: app
          image: PLACEHOLDER_IMAGE       # GHA replaces this on each deploy
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              name: http
          envFrom:
            - secretRef:
                name: app-secrets
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "kafka.events-nest.svc.cluster.local:9092"
          resources:
            requests:
              memory: "768Mi"
              cpu: "250m"
            limits:
              memory: "1280Mi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
```

**`infra/k8s/service.yaml`**:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: events-nest-server
  namespace: events-nest
  labels:
    app: events-nest-server
spec:
  type: ClusterIP
  selector:
    app: events-nest-server
  ports:
    - name: http
      port: 80
      targetPort: http
      protocol: TCP
```

**`infra/k8s/ingress.yaml`** — note: HTTP-only until you have an ACM cert (see [§9.8](#98-ingress-with-https443-listener-but-no-acm-cert)):
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: events-nest-server
  namespace: events-nest
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80}]'      # HTTPS added once you have a cert
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health/readiness
    alb.ingress.kubernetes.io/healthcheck-interval-seconds: "15"
    alb.ingress.kubernetes.io/healthy-threshold-count: "2"
    # Once you have an ACM cert, uncomment and add the ARN:
    # alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:eu-north-1:ACCOUNT:certificate/UUID
spec:
  ingressClassName: alb     # use spec field, NOT the deprecated annotation (see §9.9)
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: events-nest-server
                port:
                  number: 80
```

**`infra/k8s/servicemonitor.yaml`**:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: events-nest-server
  namespace: events-nest
  labels:
    release: monitoring     # must match the kube-prometheus-stack helm release name
spec:
  selector:
    matchLabels:
      app: events-nest-server
  namespaceSelector:
    matchNames:
      - events-nest
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
```

Apply the static manifests once (the Deployment is applied by GHA on each push):

```bash
kubectl apply -f infra/k8s/namespace.yaml
kubectl apply -f infra/k8s/secret-store.yaml
kubectl apply -f infra/k8s/external-secret.yaml
kubectl apply -f infra/k8s/service.yaml
kubectl apply -f infra/k8s/ingress.yaml
kubectl apply -f infra/k8s/servicemonitor.yaml
```

Verify the External Secret pulled cleanly:

```bash
kubectl get externalsecret -n events-nest
# READY should be True

kubectl get secret app-secrets -n events-nest -o jsonpath='{.data}' | jq 'keys'
# Should list all 12 keys from your AWS Secrets Manager JSON
```

### 5.8 IAM user for the GitHub Actions pipeline

Don't reuse your admin user. Create a narrowly-scoped pipeline user.

Console → IAM → Users → Create user:
- Username: `eventsnest-cicd`
- Attach policies:
  - `AmazonEC2ContainerRegistryPowerUser`
- Inline policy `eventsnest-cicd-eks`:
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": ["eks:DescribeCluster", "eks:ListClusters"],
        "Resource": "*"
      }
    ]
  }
  ```
- Create access key → **Application running outside AWS** → save key + secret

Map this IAM user into the cluster's `aws-auth` ConfigMap so kubectl works:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
eksctl create iamidentitymapping \
  --cluster events-nest-prod \
  --region eu-north-1 \
  --arn arn:aws:iam::$ACCOUNT_ID:user/eventsnest-cicd \
  --group system:masters \
  --username eventsnest-cicd
```

### 5.9 GitHub repo secrets + workflow

Add these secrets in **Settings → Secrets and variables → Actions**:

| Name | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | from §5.8 |
| `AWS_SECRET_ACCESS_KEY` | from §5.8 |
| `AWS_ACCOUNT_ID` | your 12-digit account ID (no hyphens) |
| `AWS_REGION` | `eu-north-1` |
| `EKS_CLUSTER_NAME` | `events-nest-prod` |

Save the workflow as `.github/workflows/deploy.yml`:

```yaml
name: CI/CD to AWS EKS

on:
  push:
    branches: [main]
  workflow_dispatch:

env:
  AWS_REGION: ${{ secrets.AWS_REGION }}
  AWS_ACCOUNT_ID: ${{ secrets.AWS_ACCOUNT_ID }}
  EKS_CLUSTER_NAME: ${{ secrets.EKS_CLUSTER_NAME }}
  ECR_REPOSITORY: events-nest
  IMAGE_TAG: ${{ github.run_number }}-${{ github.sha }}

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25

      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-

      - run: mvn -B test

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - uses: aws-actions/amazon-ecr-login@v2
        id: ecr-login

      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.ecr-login.outputs.registry }}
        run: |
          IMAGE_URI=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker build -t $IMAGE_URI .
          docker push $IMAGE_URI
          docker tag $IMAGE_URI $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
          echo "IMAGE_URI=$IMAGE_URI" >> $GITHUB_ENV

      - name: Update kubeconfig
        run: aws eks update-kubeconfig --name $EKS_CLUSTER_NAME --region $AWS_REGION

      - name: Apply manifests
        run: |
          kubectl apply -f infra/k8s/namespace.yaml
          kubectl apply -f infra/k8s/secret-store.yaml
          kubectl apply -f infra/k8s/external-secret.yaml
          kubectl apply -f infra/k8s/service.yaml
          kubectl apply -f infra/k8s/ingress.yaml
          kubectl apply -f infra/k8s/servicemonitor.yaml || true

      - name: Deploy app
        run: |
          sed "s|PLACEHOLDER_IMAGE|$IMAGE_URI|g" infra/k8s/deployment.yaml | kubectl apply -f -

      - name: Wait for rollout
        run: kubectl rollout status deployment/events-nest-server -n events-nest --timeout=5m

      - name: Show ALB endpoint
        if: success()
        run: |
          ALB=$(kubectl get ingress events-nest-server -n events-nest -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
          [ -n "$ALB" ] && echo "::notice ::App reachable at http://$ALB"

      - name: Diagnostic dump on failure
        if: failure()
        run: |
          aws eks update-kubeconfig --name $EKS_CLUSTER_NAME --region $AWS_REGION 2>/dev/null || exit 0
          kubectl describe deployment/events-nest-server -n events-nest || true
          kubectl get pods -n events-nest -o wide || true
          kubectl logs -n events-nest -l app=events-nest-server --tail=200 --previous || true
          kubectl describe externalsecret/app-secrets -n events-nest || true
```

### 5.10 First deploy

```bash
git add infra/ .github/workflows/deploy.yml
git commit -m "feat[deploy]: EKS infra + CI/CD pipeline"
git push origin main
```

Open the GitHub Actions tab. ~5–7 min for the workflow to:
1. Run `mvn test`
2. Build + push image to ECR
3. Apply manifests
4. Wait for rollout

End state: green check + a notice "App reachable at http://k8s-...elb.amazonaws.com".

```bash
ALB=$(kubectl get ingress events-nest-server -n events-nest \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
curl "http://$ALB/actuator/health"
# {"status":"UP",...}
```

### 5.11 HTTPS via CloudFront

Vercel-hosted frontends are HTTPS, browsers refuse mixed content, so the API
needs HTTPS. Easiest no-domain path is CloudFront in front of the ALB —
you get a free `*.cloudfront.net` URL with TLS handled by AWS.

```bash
ALB_DNS=$(kubectl get ingress events-nest-server -n events-nest \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

cat > /tmp/cf-config.json <<EOF
{
  "CallerReference": "eventsnest-$(date +%s)",
  "Comment": "EventsNest API",
  "Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [{
      "Id": "alb-origin",
      "DomainName": "$ALB_DNS",
      "CustomOriginConfig": {
        "HTTPPort": 80,
        "HTTPSPort": 443,
        "OriginProtocolPolicy": "http-only",
        "OriginSslProtocols": {"Quantity": 1, "Items": ["TLSv1.2"]},
        "OriginReadTimeout": 30,
        "OriginKeepaliveTimeout": 5
      },
      "CustomHeaders": {"Quantity": 0}
    }]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "alb-origin",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 7,
      "Items": ["GET","HEAD","OPTIONS","PUT","POST","PATCH","DELETE"],
      "CachedMethods": {"Quantity": 2, "Items": ["GET","HEAD"]}
    },
    "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
    "OriginRequestPolicyId": "216adef6-5c7f-47e4-b989-5492eafa07d3",
    "Compress": true
  },
  "PriceClass": "PriceClass_100"
}
EOF

aws cloudfront create-distribution \
  --distribution-config file:///tmp/cf-config.json \
  --query 'Distribution.[Id,DomainName,Status]' \
  --output table
```

The cache policy `4135ea2d-...` is AWS's "CachingDisabled" managed policy
(important for an API). The origin request policy `216adef6-...` is "AllViewer"
(forwards everything to origin).

CloudFront takes ~5–15 min to deploy globally. Watch:

```bash
aws cloudfront list-distributions \
  --query 'DistributionList.Items[?Comment==`EventsNest API`].[Id,DomainName,Status]' \
  --output table
```

Once `Deployed`, your API is at `https://dXXXXXXXX.cloudfront.net`.

> Windows / PowerShell: replace the `cat <<EOF` heredoc with `Set-Content` on a
> file. See [§9.W1](#w1-bash-only-syntax-on-windows).

### 5.12 Frontend hookup

1. **Update Vercel env vars**:
   - Project Settings → Environment Variables → edit `VITE_API_BASE_URL`:
     ```
     https://dXXXXXXXX.cloudfront.net/api/v1
     ```
   - Deployments → click latest → Redeploy

2. **Update CORS allow-list in AWS Secrets Manager**:
   - Console → Secrets Manager → `eventsnest/prod/app` → Edit secret value
   - Set `APP_CORS_ALLOWED_ORIGINS` to your Vercel origins, comma-separated:
     ```
     https://eventsnest.vercel.app,https://eventsnest-*.vercel.app
     ```
   - Save

3. **Force pod refresh** so the new CORS list takes effect:
   ```bash
   kubectl delete secret app-secrets -n events-nest
   kubectl rollout restart deployment/events-nest-server -n events-nest
   ```

---

## 6. Verification & smoke tests

```bash
# 0. Get the API URL
API="https://$(aws cloudfront list-distributions \
  --query 'DistributionList.Items[?Comment==`EventsNest API`].DomainName | [0]' \
  --output text)"
echo $API

# 1. Health
curl -s $API/actuator/health | jq

# 2. CORS preflight
curl -i -X OPTIONS \
  -H "Origin: https://eventsnest.vercel.app" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type, authorization" \
  $API/api/v1/auth/login | grep -iE "^access-control"
# Expected: Access-Control-Allow-Origin: https://eventsnest.vercel.app
#           Access-Control-Allow-Credentials: true

# 3. Login as the seeded admin
curl -s -X POST $API/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@eventsnest.com","password":"<your-ADMIN_PASSWORD>"}'
# Expected: {access, refresh tokens}

# 4. Public events endpoint
curl -s $API/api/v1/events
# Expected: EventsNestResponse wrapper with empty events list initially

# 5. Prometheus metrics
curl -s $API/actuator/prometheus | head -10
# Expected: Prometheus exposition format
```

### Verify backups (after 24h)

Console → RDS → `events-nest-db-prod` → **Maintenance & backups** tab. Confirm
at least one automated snapshot is listed.

**Practice a restore** before you actually need it:
1. Right-click snapshot → Restore snapshot
2. Restore to `events-nest-db-prod-restore-test`
3. Wait ~10 min, connect, verify tables/rows are present
4. Delete the test instance

---

## 7. Day-2 operations

### Tail logs

```bash
kubectl logs -n events-nest -l app=events-nest-server --follow --tail=100
```

For a previously-crashed pod:
```bash
kubectl logs -n events-nest -l app=events-nest-server --previous --tail=200
```

### Rotate a secret (e.g. JWT key)

```bash
NEW_JWT=$(openssl rand -base64 48)
echo "New: $NEW_JWT"

# Update in AWS Secrets Manager (console: Edit secret value)

# Force ESO to re-pull (default refresh is 1h)
kubectl delete secret app-secrets -n events-nest

# Restart pods to pick up the new env var
kubectl rollout restart deployment/events-nest-server -n events-nest
```

### Restart pods (e.g. after secret change)

```bash
kubectl rollout restart deployment/events-nest-server -n events-nest
kubectl rollout status deployment/events-nest-server -n events-nest
```

### Scale horizontally

```bash
kubectl scale deployment/events-nest-server -n events-nest --replicas=3
```

For automatic scaling, add an HPA:
```bash
kubectl autoscale deployment events-nest-server -n events-nest \
  --cpu-percent=70 --min=1 --max=5
```

### Add a node

```bash
eksctl scale nodegroup --cluster events-nest-prod --name workers \
  --nodes 3 --region eu-north-1
```

### Open Grafana

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
# Open http://localhost:3000 — admin / your-password
```

### Manual redeploy (skip GHA)

```bash
# Force pull the :latest tag and restart
kubectl rollout restart deployment/events-nest-server -n events-nest
```

### Connect to RDS from a debug pod

```bash
ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier events-nest-db-prod \
  --region eu-north-1 \
  --query 'DBInstances[0].Endpoint.Address' --output text)

kubectl run mysql-test -n events-nest --rm -it --restart=Never \
  --image=mysql:8.0 \
  -- mysql -h $ENDPOINT -u admin -p
```

---

## 8. Cleanup & cost control

### Tear down everything

```bash
# 1. Delete the CloudFront distribution (must disable first, takes ~15 min to propagate)
DIST_ID=$(aws cloudfront list-distributions \
  --query 'DistributionList.Items[?Comment==`EventsNest API`].Id | [0]' \
  --output text)
aws cloudfront get-distribution-config --id $DIST_ID > /tmp/cf.json
# Edit /tmp/cf.json to set Enabled=false, then update + delete (or do via console — easier)

# 2. Delete the EKS cluster (deletes nodes, ALB, ENIs)
eksctl delete cluster -f infra/eks/cluster.yml

# 3. Delete RDS (loses data — take a manual snapshot first if you care)
aws rds delete-db-instance \
  --db-instance-identifier events-nest-db-prod \
  --skip-final-snapshot \
  --region eu-north-1

# 4. (Optional) Delete ECR repo
aws ecr delete-repository --repository-name events-nest --force --region eu-north-1

# 5. (Optional) Delete the secret
aws secretsmanager delete-secret \
  --secret-id eventsnest/prod/app \
  --force-delete-without-recovery \
  --region eu-north-1
```

### Pause spend without losing the cluster

If you just want to stop paying for nodes overnight:

```bash
# Scale node group to zero (saves node + EBS cost; control plane $73/mo continues)
eksctl scale nodegroup --cluster events-nest-prod --name workers \
  --nodes 0 --region eu-north-1

# Stop RDS (saves $13/mo; max 7 days, then it auto-starts)
aws rds stop-db-instance --db-instance-identifier events-nest-db-prod \
  --region eu-north-1
```

To resume:
```bash
eksctl scale nodegroup --cluster events-nest-prod --name workers \
  --nodes 2 --region eu-north-1
aws rds start-db-instance --db-instance-identifier events-nest-db-prod \
  --region eu-north-1
```

---

## 9. Things we got wrong (and how to avoid them)

This section covers the gotchas we hit during the actual deployment. Skip reading
these the first time and you'll discover them yourself.

### 9.1 RDS in the wrong VPC

**What we did wrong:** Followed an early version of the plan that said "RDS:
VPC = default". Then `eksctl create cluster` produced a different VPC, and we
couldn't reference the EKS node SG from the RDS SG (cross-VPC SG references
aren't supported).

**Symptom:**
> "You may not specify a referenced group id for an existing IPv4 CIDR rule"
> or "You have specified two resources that belong to different networks"

**Fix:** Recreate RDS in the EKS VPC. Specifically:
1. Delete the wrong-VPC RDS instance
2. Create a DB subnet group from the EKS VPC's private subnets
3. Create a new SG in the EKS VPC
4. Recreate RDS using those

**How to avoid:** Always check `aws eks describe-cluster --query
cluster.resourcesVpcConfig.vpcId` *before* clicking through the RDS creation
wizard. Or use Terraform/CloudFormation to make VPC choice explicit.

### 9.2 RDS instance ID vs database name

**What we did wrong:** Set `SPRING_DATASOURCE_URL` to
`jdbc:mysql://endpoint:3306/events-nest-prod` — using the RDS **instance
identifier** as the path. But the instance ID is the AWS resource name; the
database name is what's *inside* the MySQL server, set by the "Initial database
name" field during RDS creation (which we left blank).

**Symptom:**
> `SQLSyntaxErrorException: Unknown database 'events-nest-prod'`

App crash-looped on startup with this error during Flyway init.

**Fix:** Either set "Initial database name" during RDS creation, or
`CREATE DATABASE events_nest_db` manually after the fact, then update the JDBC
URL to use it.

**How to avoid:** Always fill in "Initial database name" during RDS creation
(under Additional configuration). Use underscores, not hyphens — MySQL DB names
with hyphens require backtick-quoting everywhere.

### 9.3 External Secrets API version (v1beta1 vs v1)

**What we did wrong:** Used `apiVersion: external-secrets.io/v1beta1` in the
ESO manifests. ESO 0.10+ ships only the stable `v1` API; the older `v1beta1`
was removed.

**Symptom:**
> `error: resource mapping not found for name: "aws-secrets-manager"
> namespace: "events-nest" from "infra/k8s/secret-store.yaml":
> no matches for kind "SecretStore" in version "external-secrets.io/v1beta1"
> ensure CRDs are installed first`

**Fix:** Change all ESO manifests to `apiVersion: external-secrets.io/v1`.

**How to avoid:** Check what your installed CRDs actually expose:
```bash
kubectl api-resources | grep external-secrets
```

### 9.4 AWS Free Tier plan blocking t3.medium launches

**What we did wrong:** Followed the plan straight through cluster creation
without realizing AWS launched a "Free Tier plan" in 2024 that locks new
accounts to free-tier-eligible resources. `t3.medium` isn't on the allow-list.

**Symptom:** `eksctl create cluster` runs for ~25 min, then times out on the
node group with:
> "InvalidParameterCombination - The specified instance type is not eligible
> for Free Tier"

**Fix:** Console → Account → Account plan → **Upgrade to Pay-As-You-Go**.
30 seconds. Then `eksctl delete cluster` to clean up the half-built remains,
then `eksctl create cluster` again.

**How to avoid:** Check at the start of any AWS work on a new account:
```bash
aws ec2 run-instances --dry-run \
  --image-id ami-09a9858973b288bdd \
  --instance-type t3.medium \
  --region eu-north-1 2>&1 | grep -E "DryRunOperation|Free Tier"
```
`DryRunOperation` = upgraded. `Free Tier` error = upgrade first.

### 9.5 Pod-per-node limit on t3.medium

**What we did wrong:** Started with a single t3.medium node, which has a hard
**17-pod-per-node limit** (set by ENI capacity, not memory or CPU). After
installing the kube-prometheus-stack the cluster had:
- ~9 kube-system pods
- 3 external-secrets pods
- 1 Kafka pod
- Trying to land 6+ monitoring pods

= 19+ pods, but only 17 slots → some stay `Pending`.

**Symptom:**
> `0/1 nodes are available: 1 Too many pods.`

**Fix options:**
- **Easiest**: scale to 2 nodes:
  ```bash
  eksctl scale nodegroup --cluster events-nest-prod --name workers --nodes 2 --region eu-north-1
  ```
- **Cheaper**: enable VPC CNI **prefix delegation** (lifts limit to ~110/node):
  ```bash
  kubectl set env daemonset aws-node -n kube-system ENABLE_PREFIX_DELEGATION=true
  # Then drain + uncordon the node, or replace it
  ```

**How to avoid:** Plan for 2 nodes from the start if you want monitoring,
or use a larger instance type (t3.large = 35 max pods).

### 9.6 Helm install stuck "pending-install" after Ctrl+C

**What we did wrong:** Cancelled a slow `helm install` mid-flight thinking it
was hung. The next `helm install` failed with:
> "cannot reuse a name that is still in use"

The release was registered but never finished deploying.

**Fix:**
```bash
helm uninstall <release> -n <namespace>
# Then retry the install
```

**How to avoid:** Don't Ctrl+C `helm install`. The kube-prometheus-stack chart
takes 1–3 min to send all 60+ resources to the API server before returning.
Watch progress in another terminal with `kubectl get pods -n monitoring -w`.

If install genuinely fails, use `--timeout 15m` to give it more time, or
`--atomic` to auto-rollback on failure.

### 9.7 Credentials management: "Managed in Secrets Manager" vs "Self managed"

**The trap:** During RDS creation, AWS shows two options:
- **Managed in AWS Secrets Manager — most secure**
- **Self managed**

The "most secure" tag is misleading for our setup. **Pick Self managed.**

**Why:** "Managed in Secrets Manager" creates a *separate* secret (named
`rds!db-xxxx`) for just the master password, with auto-rotation. Our app reads
all secrets from `eventsnest/prod/app` (one JSON object). Picking the
auto-managed option means either:
- Plumb a second secret reference into the app (more code), or
- Manually copy the auto-generated password into `eventsnest/prod/app` after
  every rotation (defeats the auto-rotation)

Self managed = you choose the password, paste it into your app secret, done.
One secret, one source of truth.

### 9.8 Ingress with HTTPS:443 listener but no ACM cert

**What we did wrong:** Left `listen-ports: '[{"HTTP":80},{"HTTPS":443}]'` in
the Ingress while having no ACM certificate annotation. The ALB Controller
tried to create the HTTPS listener every reconcile, AWS rejected with:
> `api error ValidationError: A certificate must be specified for HTTPS listeners`

The whole deploy failed — ALB never got created, Ingress address stayed empty,
the spike was ~30 min lost.

**Fix:** Remove HTTPS from `listen-ports` until you have a cert:
```yaml
alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80}]'
```

Add HTTPS back when you have an ACM cert:
```yaml
alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80},{"HTTPS":443}]'
alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:eu-north-1:...:certificate/...
```

**How to avoid:** Only configure listeners you can actually fulfil. Get HTTPS
via CloudFront (no domain needed) or via ACM after buying a domain.

### 9.9 Deprecated `kubernetes.io/ingress.class` annotation

**What we did wrong:** Used `kubernetes.io/ingress.class: alb` annotation. It
works but emits a deprecation warning every apply.

**Fix:** Use the spec field:
```yaml
spec:
  ingressClassName: alb
```

**How to avoid:** Always check `kubectl explain ingress.spec.ingressClassName`
for the canonical form.

### 9.10 ECR repo name mismatch with the workflow

**What we did wrong:** Created an ECR repo named `events-nest`, but the GHA
workflow's `ECR_REPOSITORY` env var was `events-nest-server`. The push step
failed with:
> "name unknown: The repository with name 'events-nest-server' does not exist"

**Fix:** Match either side — either rename the repo, or change `ECR_REPOSITORY`
in the workflow.

**How to avoid:** Bake the ECR repo name into a single source of truth (env var,
Helm value, or Terraform output) used by both `aws ecr create-repository` and
the GHA workflow.

### 9.11 Hardcoded JWT signing key in `application.properties`

**What we did wrong:** When externalizing secrets in [§3.1](#31-externalize-secrets),
moved most secrets to env vars but left:
```properties
jwt.signing.key=boom-supersonic-flight-secret-key-long-enough
```
in `application.properties`. This shipped in the runnable jar — anyone with the
jar could `unzip -p app.jar BOOT-INF/classes/application.properties` and read it.

**Fix:**
```properties
jwt.signing.key=${JWT_SIGNING_KEY}
```
No default — the app refuses to boot without one.

**How to avoid:** When auditing for leaked secrets, run:
```bash
git grep -nE "(api[_-]?key|password|secret|token|signing[_-]?key)\s*[:=]\s*['\"]?[a-zA-Z0-9_-]{16,}" \
  -- ':!*.md' ':!env.example'
```

### 9.12 Mixed content blocked on Vercel

**What we did wrong:** Pointed the (HTTPS) Vercel frontend at the (HTTP) ALB DNS.
Browsers refused — modern browsers block mixed-content requests entirely.

**Symptom:**
> `Mixed Content: The page at 'https://eventsnest.vercel.app/login' was loaded
> over HTTPS, but requested an insecure resource 'http://k8s-...elb...'.
> This request has been blocked.`

**Fix:** Put TLS in front of the API. Easiest no-domain path is
[CloudFront in front of the ALB](#511-https-via-cloudfront).

**How to avoid:** Always plan for HTTPS at the API edge before connecting a
hosted frontend.

### 9.13 `.env` accidentally committed

**What we did wrong:** `.env` got committed and pushed before the `.gitignore`
entry was in place.

**Fix:**
1. **Rotate every actual secret** in the file (don't skip this — git removal
   is hygiene, not security)
2. `git rm --cached .env`
3. Add `.env` and friends to `.gitignore`
4. Optionally scrub from history with `git filter-repo --path .env --invert-paths`

**How to avoid:**
- Add `.env` to `.gitignore` *before* the first commit
- Use `git status` religiously before each push
- Enable GitHub's secret scanning (Settings → Security → Secret scanning)

### 9.W1 Bash-only syntax on Windows

**The trap:** Several commands in this guide use bash heredocs (`cat <<EOF ... EOF`)
and shell substitution (`$()`). PowerShell handles `$()` differently and doesn't
support heredocs at all.

**Fix:** Run AWS CLI commands from one of:
- **Git Bash** (ships with Git for Windows) — drop-in compatible
- **WSL2** (Ubuntu inside Windows) — drop-in compatible
- **PowerShell** with manual translation:

PowerShell heredoc equivalent:
```powershell
$config = @"
{
  "CallerReference": "eventsnest-$([DateTimeOffset]::Now.ToUnixTimeSeconds())",
  ...
}
"@
$config | Out-File -Encoding utf8 cf-config.json
aws cloudfront create-distribution --distribution-config file://cf-config.json
```

PowerShell command substitution:
```powershell
# Instead of:  ALB=$(kubectl get ingress ... -o jsonpath='{.metadata.name}')
$ALB = kubectl get ingress events-nest-server -n events-nest `
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

For long-running multi-step procedures, just install Git Bash and copy-paste —
it's far less error-prone than translating each command.

### 9.W2 `openssl` not on PATH on Windows

**The trap:** Bash commands use `openssl rand -base64 48` to generate strong
keys. Windows doesn't ship openssl in the default PATH.

**Fix:**
- Git Bash bundles openssl — use that
- Or install via Chocolatey: `choco install openssl.light`
- Or PowerShell substitute:
  ```powershell
  $bytes = New-Object byte[] 48
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  [Convert]::ToBase64String($bytes)
  ```

---

## 10. Troubleshooting reference

| Symptom | Likely cause | Where in this guide |
|---|---|---|
| `eksctl create cluster` stalls 20+ min on nodegroup, then "Free Tier" error | New AWS account on Free Tier plan | [§9.4](#94-aws-free-tier-plan-blocking-t3medium-launches) |
| `eksctl create cluster` after delete fails: `AlreadyExistsException` | Async delete still running | wait for `aws cloudformation wait stack-delete-complete` |
| RDS SG inbound rule fails: "different networks" | RDS in default VPC, EKS in its own | [§9.1](#91-rds-in-the-wrong-vpc) |
| Pod crashlooping with "Unknown database 'X'" | JDBC URL using RDS instance ID instead of DB name | [§9.2](#92-rds-instance-id-vs-database-name) |
| `kubectl apply` of secret-store.yaml: "no matches for kind SecretStore" | API version is `v1beta1` but installed CRDs only have `v1` | [§9.3](#93-external-secrets-api-version-v1beta1-vs-v1) |
| ESO `app-secrets` not syncing, `READY=False` | IRSA service account annotation missing or wrong secret name | `kubectl describe externalsecret/app-secrets -n events-nest` |
| Ingress address never populates | ALB controller failing — check `kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller` | [§9.8](#98-ingress-with-https443-listener-but-no-acm-cert) |
| Pods stuck `Pending` with "Too many pods" | t3.medium 17-pod cap | [§9.5](#95-pod-per-node-limit-on-t3medium) |
| `helm install` errors "cannot reuse a name that is still in use" | Stuck pending-install from prior Ctrl+C | [§9.6](#96-helm-install-stuck-pending-install-after-ctrlc) |
| GHA workflow: "ECR repo not found" | Workflow `ECR_REPOSITORY` doesn't match created repo | [§9.10](#910-ecr-repo-name-mismatch-with-the-workflow) |
| Frontend gets `Mixed Content` blocked | HTTPS frontend → HTTP backend | [§9.12](#912-mixed-content-blocked-on-vercel) |
| App boots but UI calls fail with CORS error | `APP_CORS_ALLOWED_ORIGINS` not set in prod secret | [§5.12](#512-frontend-hookup) step 2 |
| `kubectl get ingress` shows ADDRESS empty | ALB controller logs will show why; usually missing subnet tags or cert | `kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller` |
| Health = `503 DOWN` with `mail` indicator showing error | SMTP autoconfig probing with no creds | `management.health.mail.enabled=false` |

---

## 11. Glossary

| Term | Definition |
|---|---|
| **EKS** | Elastic Kubernetes Service — AWS's managed Kubernetes control plane |
| **ALB** | Application Load Balancer — AWS layer-7 load balancer |
| **ECR** | Elastic Container Registry — AWS's private Docker registry |
| **RDS** | Relational Database Service — AWS's managed SQL databases |
| **IRSA** | IAM Roles for Service Accounts — lets pods assume IAM roles via OIDC, no static keys in cluster |
| **ESO** | External Secrets Operator — Kubernetes operator that pulls from external secret stores into native k8s Secrets |
| **CRD** | CustomResourceDefinition — extends the Kubernetes API with new object types |
| **Helm** | Kubernetes package manager — installs apps via "charts" |
| **eksctl** | EKS-specific CLI that wraps CloudFormation to bootstrap clusters |
| **kubectl** | The Kubernetes CLI, talks to the cluster's API server |
| **Cluster security group** | The SG AWS auto-attaches to all EKS nodes and the control plane ENIs |
| **Cloud Map / Service discovery** | k8s built-in: services reachable at `<svc>.<ns>.svc.cluster.local:<port>` |
| **CloudFront** | AWS's CDN — used here for free TLS in front of an HTTP origin |
| **Free Tier plan** | AWS account mode that locks new accounts to free-eligible resources only |
| **Pay-As-You-Go plan** | Standard AWS account mode where you pay for what you use |
| **VPC** | Virtual Private Cloud — your isolated AWS network |
| **NAT Gateway** | Routes outbound traffic from private subnets — $32/mo, avoid if possible |
| **Prefix delegation** | VPC CNI feature that gives each ENI a block of IPs, lifting the per-node pod cap |

---

## Appendix A — local dev quick-start

```bash
# Backend
cp env.example .env  # then fill in BREVO_API_KEY etc.
docker compose up -d --build
curl http://localhost:8080/actuator/health
# Stop: docker compose down

# Frontend
cd ../events-nest-frontend
echo "VITE_API_BASE_URL=http://localhost:8080/api/v1" > .env
npm install
npm run dev
# http://localhost:5173
```

## Appendix B — useful one-liners

```bash
# Get the API URL
kubectl get ingress events-nest-server -n events-nest \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Tail app logs
kubectl logs -n events-nest -l app=events-nest-server --follow --tail=50

# Force a fresh deploy without pushing code
kubectl rollout restart deployment/events-nest-server -n events-nest

# Get the secret keys (without values)
kubectl get secret app-secrets -n events-nest -o jsonpath='{.data}' | jq 'keys'

# Decode a single secret value
kubectl get secret app-secrets -n events-nest \
  -o jsonpath='{.data.SPRING_DATASOURCE_URL}' | base64 -d ; echo

# Connect to RDS via a debug pod
kubectl run mysql-test -n events-nest --rm -it --restart=Never \
  --image=mysql:8.0 -- mysql -h <rds-endpoint> -u admin -p

# Check current monthly spend
aws ce get-cost-and-usage \
  --time-period Start=$(date -u +%Y-%m-01),End=$(date -u +%Y-%m-%d) \
  --granularity MONTHLY \
  --metrics BlendedCost
```

---

*Last updated: this is a living document — keep it accurate as the
infrastructure evolves.*
