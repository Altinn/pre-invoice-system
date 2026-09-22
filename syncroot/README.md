# syncroot — GitOps deployment to dis-core

Kubernetes manifests for `preinvoicingsystem`, deployed to the `product-preinvoicingsystem`
namespace in Altinn's `dis-core` AKS cluster. This follows the golden path the platform team
described in [Altinn/altinn-platform#3809](https://github.com/Altinn/altinn-platform/issues/3809).

Deployment is **pull-based** (altinn-platform RFC 0001). Nothing in this repository talks to the
cluster:

```
ci.yml            ──▶ ghcr.io/altinn/pre-invoice-system:<tag>
                                     │
publish-syncroot.yml ──▶ oci://altinncr.azurecr.io/preinvoicingsystem/syncroot:<env>
                                     │
                            Flux (in dis-core)
                                     │
              product-preinvoicingsystem namespace ──▶ pulls the image via the ACR cache
```

Azure resources are **declared here, not clicked or Terraformed**: the dis-* operators reconcile
the custom resources in `base/preinvoicingsystem/` into a PostgreSQL Flexible Server, a Key
Vault and a managed identity.

## Layout

```
base/preinvoicingsystem/
  applicationidentity.yaml   ApplicationIdentity x2 (app + db admin) -> identity + ServiceAccount
  vault.yaml                 Vault -> Azure Key Vault + a namespaced SecretStore
  external-secrets.yaml      ExternalSecret -> OIDC login parameters as a Kubernetes Secret
  database.yaml              DatabaseServer + Database -> PostgreSQL Flexible Server
  storage.yaml               PVC for the export archive (LG04/PDF/CSV/XLSX)
  deployment.yaml            the application
  service.yaml               ClusterIP
  httproute.yaml             Gateway API route via Traefik
  network-policies.yaml      Linkerd: only Traefik may reach the app port
tt02/   prod/                overlays: image tag, hostname, env tag, server size
```

`tt02` is the test environment and `prod` production — the two the issue asks for. **The folder
name is the OCI artifact tag** the platform team's Flux `Kustomization` must reference, so it has
to match on both sides. If a different cluster is assigned, rename the folder and update the
`environment` choices in `.github/workflows/publish-syncroot.yml` and the `environments` input in
`.github/workflows/syncroot-validate.yml`. Nothing else depends on the name.

## Deploying

1. Merge to `main`. `ci.yml` builds and pushes `ghcr.io/altinn/pre-invoice-system:<sha>`.
2. Run the **Publish syncroot** workflow and choose the environment. Leave **Image tag** empty to
   deploy the head of `main`; fill it in to pin an older sha, for a rollback.
3. Flux reconciles within its sync interval.

The empty field resolves to the commit sha of `main`, not the `latest` tag CI also pushes: the
syncroot is reconciled by content, so an artifact that always reads `newTag: latest` is
byte-identical on every deploy and Flux never rolls the Deployment. Since the workflow only runs
on `main`, that sha is also the commit the overlays themselves are built from. Let `ci.yml` finish
on that commit first — nothing here checks that the image exists, and a missing one shows up as
`ImagePullBackOff` in the cluster rather than as a failed workflow.

The overlays keep `newTag: will-be-replaced` in git on purpose; the publish workflow stamps the
real tag in at deploy time, and `syncroot-validate.yml` fails if a real tag is ever committed.

## Before the first deploy

These are prerequisites the platform team and this team have to settle — the manifests are
written so that a missing one fails loudly rather than deploying something subtly wrong.

| What | Who | Status |
|---|---|---|
| GitHub secrets `DIS_SYNCROOT_AZURE_{CLIENT_ID,TENANT_ID,SUBSCRIPTION_ID}` | platform | needed by `publish-syncroot.yml` |
| Flux `OCIRepository` + `Kustomization` in dis-core pointing at `preinvoicingsystem/syncroot` | platform | onboarding step |
| ACR cache rule for `ghcr.io/altinn/pre-invoice-system` | platform | onboarding step |
| `groupObjectId` in `vault.yaml` — the Entra group that may write secrets | platform | **TODO(OQ-17)**, placeholder is all-zero |
| Entra app registration + the three vault secrets below | platform / Digdir IT | **TODO(OQ-17)** |
| Role-mapping groups (reader / maintainer / approver) | Digdir IT | **TODO(OQ-17)** |
| PostgreSQL authentication mode | both | **TODO(OQ-16)**, blocks startup |
| Externally advertised hostname | platform | **TODO(OQ-17)**, internal dis-core name used meanwhile |

### Vault secrets the app expects

`external-secrets.yaml` syncs these out of the Key Vault the `Vault` resource creates. They are
kept in the vault rather than in git so that no plausible-but-wrong tenant or client id can be
committed — see docs/07 OQ-17.

| Key in Key Vault | Becomes |
|---|---|
| `oidc-client-id` | `OIDC_CLIENT_ID` |
| `oidc-client-secret` | `OIDC_CLIENT_SECRET` |
| `oidc-issuer-uri` | `OIDC_ISSUER_URI` (e.g. `https://login.microsoftonline.com/<tenant>/v2.0`) |

### PostgreSQL authentication (TODO(OQ-16))

`dis-pgsql-operator` publishes a **non-secret** ConfigMap named `<Database>-<identityRef>-dis-pgsql`
— here `preinvoicingsystem-preinvoicingsystem-dis-pgsql` — with `host` / `port` / `dbname` /
`user` / `sslmode` / `uri`. The deployment reads it and composes `DB_URL`. There is no password
key: the golden path is passwordless, the app presenting an Entra access token as the password.

`application.yaml` reads a static `${DB_PASSWORD}` today, so **the app cannot authenticate until
that is implemented** (or the platform team's `Vault`-supplied-credential fallback is chosen). The
deployment deliberately sets no `DB_PASSWORD`: startup fails at authentication, loudly, instead of
appearing to work. See docs/07 OQ-16.

## Known gaps

- **APIM.** The issue puts APIM in front of the UI. `dis-apim-operator`'s `Api` resource requires
  `content` — an OpenAPI document — and this is a server-rendered Thymeleaf UI whose `/api` REST
  surface is future work (docs/02, docs/06). Add `Backend` + `Api` once there is a document to
  import and an APIM product is assigned. The Traefik `HTTPRoute` works in the meantime.
- **Blob archive.** The issue asks for a storage account for export files; the platform team will
  provision it with Terraform until a storage operator ships. The app has only `LocalFileArchive`
  today (docs/02), so the archive is a `managed-csi-premium` PVC. Swapping in the `AzureBlobArchive`
  adapter is an application change, and it is what makes the deployment horizontally scalable —
  the PVC is why `replicas: 1` and `strategy: Recreate`.
- **Role mapping.** `SecurityConfig` maps the ID token's `groups` claim to `LESER` / `FORVALTER` /
  `GODKJENNER`; the three `forsystem-*` group object ids default in `application-prod.yaml`
  (overridable via `OIDC_GRUPPE_*` env vars). Requires the Entra app registration to emit the
  groups claim. A user in none of the groups authenticates with read access only.
- **Telemetry.** Logs go to stdout as ECS JSON and are picked up by the platform. There is no
  OpenTelemetry dependency in `pom.xml`, so no `OTEL_*` wiring here — adding it is an application
  change, not a manifest one.

## Working on the manifests

```bash
kustomize build syncroot/tt02    # render an overlay
kustomize build syncroot/prod

# schema-check a rendered overlay exactly the way CI does
kustomize build syncroot/tt02 | kubeconform -strict -summary \
  -schema-location default \
  -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
  -skip 'application.dis.altinn.cloud/v1alpha1/ApplicationIdentity,storage.dis.altinn.cloud/v1alpha1/Database,storage.dis.altinn.cloud/v1alpha1/DatabaseServer,vault.dis.altinn.cloud/v1alpha1/Vault' \
  -
```

All of it runs in CI on every PR touching `syncroot/`, via `syncroot-validate.yml`.

`kustomize build` only proves the overlay is well-formed YAML, so the rendered output is also
schema-checked. `-strict` makes unknown fields and duplicate keys errors — the failure mode that
otherwise leaves Flux silently stuck in-cluster. There are **no published JSON schemas for the
four `dis-*` CRDs**, so `applicationidentity.yaml`, `vault.yaml` and `database.yaml` are skipped
and reach validation only when the operators reconcile them; everything else (Deployment,
Service, PVC, `HTTPRoute`, `ExternalSecret`, Linkerd policy) is validated here.
