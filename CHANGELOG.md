# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-10-08

User stories: code-corhuila/barber-saas-docs#4, code-corhuila/barber-saas-docs#7, code-corhuila/barber-saas-docs#12, code-corhuila/barber-saas-docs#59, code-corhuila/barber-saas-docs#76

### Added

- **domain:** add the business rule violation and the shared length checks
- **domain:** add the barbershop status and which ones clients see
- **domain:** add the barbershop aggregate with its trial and edit rules
- **domain:** add the catalog service with its duration and price in cents
- **domain:** add the barber specialty
- **domain:** add the barber profile and who owns it
- **application:** declare the caller, its role and the tenant taken from the token
- **application:** add the page every collection answers with
- **application:** declare the public catalog and my barbershop use cases
- **application:** declare the repository ports with their idempotency keys
- **application:** serve the public catalog and edit my barbershop
- **application:** declare the service catalog and barber use cases
- **application:** hash requests with their tenant for idempotency keys
- **application:** create, list and edit services of the token's barbershop
- **application:** manage barber profiles and their specialties
- **persistence:** generate identifiers in the service
- **persistence:** search barbershops in memory by distance or recency
- **persistence:** add in-memory services and barbers for runs without a database
- **persistence:** page with bound values and store idempotency keys in jdbc
- **persistence:** search and edit barbershops with jdbc
- **persistence:** store services and their keys in one transaction
- **persistence:** store barber profiles and specialties with jdbc
- **http:** add the shared error envelope
- **http:** turn every error into one status code and the envelope
- **http:** reuse or create the correlation id and log one line per request
- **http:** answer the liveness probe without a token
- **security:** verify rs256 tokens and read the role and the tenant
- **http:** require a token except on the public discovery catalog
- **app:** add the spring boot entry point
- **app:** wire every port to its adapter with explicit pool limits
- **app:** set timeouts, graceful shutdown and json logs
- **http:** read bodies strictly, rejecting fields the contract does not allow
- **http:** validate paging and the idempotency key
- **http:** add the response views and the page envelope
- **http:** expose the public catalog and my barbershop
- **http:** expose the service catalog of the token's barbershop
- **http:** expose barber profiles and their specialties
- **deploy:** build the image and compose the service on the shared instance
- **domain:** register a barbershop with its sign-up data and guard its removal
- **persistence:** insert a barbershop with its key and delete it only while removable
- **application:** create and remove a barbershop for the onboarding saga
- **http:** serve the internal create and removal of a barbershop
- **application:** check a new barber's user and keep its name and photo
- **persistence:** store and read the barber name and photo snapshot
- **http:** read identity-auth's user and answer 503 when it does not answer
- **app:** wire the identity-auth client with this service's token
- **deploy:** give the service identity-auth's address and its own token
- **domain:** apply the barbershop lifecycle and assign its plan
- **application:** let platform-admin list, read and change any barbershop
- **persistence:** list every barbershop and write its lifecycle conditionally
- **http:** serve the platform-admin operations on /internal/v1/barbershops

### Documentation

- **readme:** explain the service, how to run and test it, and what is missing
- **readme:** explain the internal operations of the onboarding saga
- **readme:** explain the barber name snapshot and drop the closed limitation
- **readme:** point the header to Barber Saas and barber-saas-docs
- **readme:** describe the platform-admin operations and the lifecycle

### Tests

- **ci:** build and test every pull request with java 21
- **domain:** specify the trial, visibility and edit rules of the barbershop
- **domain:** specify the service catalog, barber profile and specialty rules
- **application:** specify the public catalog and my barbershop with fake ports
- **application:** specify the service catalog with retries and another tenant
- **application:** specify barber profiles, ownership and specialties
- **persistence:** specify the order of the public search
- **persistence:** check the jdbc repositories against a migrated database
- **security:** specify which tokens are accepted and what they carry
- **app:** check health, the envelope and the correlation id over http
- **http:** specify the strict body reader and the paging parameters
- **http:** specify the catalog, services and my barbershop over http
- **http:** specify barber profiles and specialties over http
- **domain:** specify a full registration and when a barbershop can be removed
- **application:** specify the internal create and removal of a barbershop
- **http:** specify the internal barbershop operations over http
- **application:** specify the barber name snapshot and the checks of a new profile
- **http:** specify the client of identity-auth's internal user read
- **http:** check a new barber's user against a stand-in identity-auth
- **domain:** specify the barbershop lifecycle and the plan assignment
- **application:** specify the internal operations of platform-admin
- **http:** specify the internal operations of platform-admin over http

### Maintenance

- **build:** ignore build output, ide files, env files and keys
- **github:** add the pull request template
- **github:** track the story environment on the board
- **build:** add the maven parent on spring boot 3.5 and java 21
- **build:** add the core module without any framework dependency
- **build:** add the adapters module with spring web and jdbc
- **build:** add the app module that composes the service
- **env:** list every variable without real values
- use the new repository name barber-saas-infra-postgres

[2.0.0]: https://github.com/code-corhuila/barber-saas-barbershop-api/releases/tag/v2.0.0
