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
| `GET /health` | liveness, no token |

The tenant comes **only** from the token's `barbershopId` claim; another barbershop's resource
answers `404`. Every creation needs `Idempotency-Key` (a retry answers `200` with the same
resource). Errors use the shared envelope with `traceId` = `X-Correlation-Id`.

### How to start it

As part of the platform: `./scripts/up.sh dev` in `barber-saas-infra`. Alone, without a database
(in-memory repositories):

```bash
mvn -B -DskipTests package
JWT_PUBLIC_KEY="$(cat ../barber-saas-infra/keys/jwt-public.pem)" java -jar barbershop-app/target/barbershop-app-0.1.0.jar
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

- **Creating a barbershop.** It belongs to platform-admin and the workflow (onboarding, FR-004),
  through a service-to-service interface that is not contracted yet (OQ-10, OQ-12).
- **Checking a new barber profile's user.** `auth-service.yaml` has no operation to read a user, so
  `userId` is not yet verified to be a `BARBER` of the same barbershop.
- **Clients and `/barbershops/me`.** A `CLIENT` token carries no barbershop (OQ-07), so a client
  uses the public catalog; the tenant-scoped reads answer `403` for them until OQ-07 is closed.
- **Service tokens.** No operation accepts `role: SERVICE` yet; the contract does not say which
  ones should (e.g. price and duration for appointment).
