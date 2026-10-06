# barber-saas-barbershop-api

> barbershop bounded context: service API

Part of the **LMS Library** distributed system — team `lms-library`, Grupo 2.
Governance and documentation live in [`library-docs`](https://github.com/code-corhuila/library-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.

---

## BarberSaaS — what this repository is

The barbershop service: the barbershop as a tenant, its service catalog, its barbers' profiles and
their specialties (`07-api/contracts/openapi/barbershop-service.yaml`). Appointment books against
this catalog: the price and the duration of a service come from here. Hexagonal, three Maven
modules (ADR-012, annex C): `barbershop-core` (domain and use cases, no Spring),
`barbershop-adapters` (HTTP, JDBC, RS256 validation) and `barbershop-app` (composition root).
It never migrates its schema: that is `barber-saas-barbershop-db`.

| Operation | Who |
|---|---|
| `GET /api/v1/barbershops` (`city`, `lat`+`lng`), `/{id}`, `/{id}/services`, `/{id}/barbers` | anonymous — only `TRIAL`/`ACTIVE` barbershops and active services |
| `GET` / `PATCH /api/v1/barbershops/me` | the token's barbershop; only `ADMIN_BARBERSHOP` edits, never `status` or `planId` |
| `GET` / `POST /api/v1/services`, `GET` / `PUT /api/v1/services/{id}` | `ADMIN_BARBERSHOP` manages; `BARBER` and `CLIENT` see active ones |
| `GET` / `POST /api/v1/barbers`, `GET` / `PATCH /api/v1/barbers/{id}` | `ADMIN_BARBERSHOP` manages; a `BARBER` edits only their own profile |
| `GET` / `POST /api/v1/barbers/{id}/specialties`, `DELETE …/{specialtyId}` | `ADMIN_BARBERSHOP` or the barber themselves |
| `POST /internal/v1/barbershops`, `DELETE /internal/v1/barbershops/{id}` | only the service token of `barber-saas-workflow` (owner onboarding, `DEC-SHOP-05`) |
| `GET /health` | liveness, no token |

The tenant comes **only** from the token's `barbershopId` claim; another barbershop's resource
answers `404`. Every creation needs `Idempotency-Key` (a retry answers `200` with the same
resource). Errors use the shared envelope with `traceId` = `X-Correlation-Id`.

Every barber read returns `fullName` and `profilePhotoUrl`, a copy taken from identity-auth when the
profile is created (ADR-014): `POST /api/v1/barbers` reads `GET /internal/v1/users/{id}` once with
this service's `SERVICE_TOKEN`, answers `404` if the user is unknown or of another barbershop, `422` if
it is not an active `BARBER`, and `503` if identity-auth does not answer — no profile is created
unchecked. No read calls identity-auth; profiles created before have `null` until saved again.

The `/internal/v1` operations are the barbershop steps of the owner-onboarding saga: create the
barbershop in `TRIAL` (`trialEndsAt` = `createdAt` + 60 days) and, as its compensation, delete it
while it is still `TRIAL` and has no barber (`204` also when it is already gone; otherwise `422`).
They answer only on the internal network: the api-gateway routes `/api/v1`, never `/internal`. A
user's token answers `403`, and so does the token of another service. To call one by hand in
`develop`, use `WORKFLOW_SERVICE_TOKEN` of `barber-saas-infra-postgres/env/dev.env` (or
`./scripts/dev-token.sh barber-saas-workflow SERVICE 60`).

### How to start it

As part of the platform: `./scripts/up.sh dev` in `barber-saas-infra-postgres`. Alone, without a database
(in-memory repositories):

```bash
mvn -B -DskipTests package
JWT_PUBLIC_KEY="$(cat ../barber-saas-infra-postgres/keys/jwt-public.pem)" java -jar barbershop-app/target/barbershop-app-0.1.0.jar
```

### Where the data is

Schema `barbershop` of the shared PostgreSQL instance, as `barbershop_app` (`DATABASE_URL`,
`DATABASE_USER`, `DATABASE_PASSWORD`; see `.env.example`), never as the administrator.

### How it is tested

`mvn -B verify` (no Docker needed): the domain, the use cases with fake ports, the token verifier,
and the HTTP contract over the whole service with the in-memory repositories, including
cross-tenant tests (HU-TENANT-001). The JDBC repositories are also tested against a database
migrated by `barber-saas-barbershop-db` when these variables are set:

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/barbersaas TEST_DATABASE_USER=barbershop_app \
TEST_DATABASE_PASSWORD=... mvn -B verify
```

### What is missing

- **Platform-admin changes.** Suspending a barbershop or assigning it a plan (OQ-10) has no
  operation here yet.
- **Clients and `/barbershops/me`.** A `CLIENT` token carries no barbershop (OQ-07), so a client
  uses the public catalog; the tenant-scoped reads answer `403` for them until OQ-07 is closed.
- **Other service tokens.** Only the `/internal` operations accept `role: SERVICE`; the contract does
  not say which public ones should (e.g. price and duration for appointment).
