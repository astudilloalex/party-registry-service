# VPS deployment

The production workflow builds a native image, publishes it to GHCR, and installs
the image and its environment through rootless Podman Quadlet. Pushes to `main`
and manual workflow runs deploy the current production configuration.

## GitHub production environment

Configure these environment secrets in this repository's `production` environment:

| Secret | Purpose |
| --- | --- |
| `DB_PASSWORD` | PostgreSQL runtime role password |
| `FLYWAY_DB_PASSWORD` | PostgreSQL migration role password |
| `RABBITMQ_USERNAME` | Existing application user in RabbitMQ |
| `RABBITMQ_PASSWORD` | That RabbitMQ user's password |
| `PARTY_IDENTIFIER_ENCRYPTION_KEY_V1` | Base64-encoded 32-byte AES key |
| `PARTY_IDENTIFIER_INDEX_HMAC_KEY` | Independent Base64-encoded 32-byte identifier-index key |
| `PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY` | Independent Base64-encoded 32-byte registration key |
| `PARTY_CURSOR_SIGNING_KEY_V1` | Independent Base64-encoded 32-byte cursor key |
| `VPS_HOST` | SSH host |
| `VPS_USER` | Rootless Podman deployment user |
| `VPS_SSH_PRIVATE_KEY` | SSH deployment key |
| `VPS_SSH_KNOWN_HOSTS` | Verified SSH known-hosts entry |

The renderer requires all eight application secrets, rejects placeholders and
control characters, and validates key sizes and independence before creating a
private environment file. It never generates or rotates production keys. When
deploying existing data, provision the keys already used by that deployment.

Optional settings can be stored as production environment variables or secrets:
`DB_USERNAME`, `FLYWAY_DB_USERNAME`, `DB_REACTIVE_URL`, `DB_JDBC_URL`,
`GEOGRAPHIC_REFERENCE_BASE_URL`, `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_VIRTUAL_HOST`, `PARTY_OUTBOX_MODE`, `PARTY_OUTBOX_EXCHANGE`, and
`PARTY_OUTBOX_ROUTING_KEY`. A nonempty secret takes precedence over the matching
environment variable. Otherwise, `quadlet/party-registry-service.env.example`
supplies the default. RabbitMQ defaults to the `platform` virtual host; its user
and permissions must already exist in that virtual host. Updating GitHub settings
takes effect on the next deployment.

## Installation and recovery

The workflow renders the environment in the runner's temporary directory, sends
it over SSH/SCP to a private per-run staging directory, and passes it to
`vps/deploy.sh`. The script pulls the image before replacing live configuration.
It installs the environment at
`/home/containers/deployment/env/party-registry-service.env` with permissions
`600`, creates the Quadlet, and checks readiness. The deployment user must have
write access to the environment destination and Quadlet directories. PostgreSQL,
its roles and database, RabbitMQ, and the `internal-services.network` and
`party-db.network` infrastructure must already be provisioned.

Both installed files are replaced through atomic renames. On installation,
generation, startup, or readiness failures, and handled termination signals, the
script restores both previous files and restarts the previous service. A failed
first deployment removes the newly installed files. If recovery itself fails,
the script reports the private backup directory and retains recovery files.
Runner credentials and remote staging files are removed after deployment.

Configuration rollback does not revert Flyway migrations or password changes
made in PostgreSQL or RabbitMQ. Coordinate credential rotation with the broker
or database and retain application-compatible migrations and cryptographic keys.

For a manual deployment, the script also accepts an optional generated file:

```bash
bash deploy.sh IMAGE_REFERENCE SOURCE_DIRECTORY NEW_ENVIRONMENT_FILE
```

Without the third argument, it reuses the installed production environment.
`DEPLOYMENT_ENVIRONMENT_FILE` can override its destination with an absolute path;
the generated Quadlet uses the same destination.

## Local verification

The deployment tests use test-only credentials, temporary directories, and
simulated Podman/systemd commands. They never contact the VPS or change a real
container service:

```bash
python3 -m unittest discover -s deploy/tests -v
bash -n deploy/vps/deploy.sh
```
