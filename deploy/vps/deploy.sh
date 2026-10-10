#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_NAME="party-registry-service"
readonly SERVICE_NAME="${APP_NAME}.service"
readonly IMAGE_REFERENCE="${1:?Usage: deploy.sh IMAGE_REFERENCE [SOURCE_DIRECTORY] [NEW_ENVIRONMENT_FILE]}"
readonly SOURCE_DIRECTORY="${2:-${HOME}/.local/share/${APP_NAME}}"
readonly CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"
readonly QUADLET_DIRECTORY="${CONFIG_HOME}/containers/systemd"
readonly QUADLET_TEMPLATE="${SOURCE_DIRECTORY}/${APP_NAME}.container.template"
readonly QUADLET_FILE="${QUADLET_DIRECTORY}/${APP_NAME}.container"
readonly ENVIRONMENT_FILE="${DEPLOYMENT_ENVIRONMENT_FILE:-/home/containers/deployment/env/${APP_NAME}.env}"
readonly INCOMING_ENVIRONMENT_FILE="${3:-}"
readonly REGISTRY_AUTH_FILE="${CONFIG_HOME}/containers/auth.json"
readonly HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://127.0.0.1:8082/q/health/ready}"
readonly -a REQUIRED_NETWORKS=("internal-services" "party-db")

umask 077
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=${XDG_RUNTIME_DIR}/bus}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

show_service_logs() {
  journalctl --user --unit "${SERVICE_NAME}" --lines 80 --no-pager >&2 || true
}

cleanup_files=()
backup_directory=""
transaction_active=false
environment_replaced=false
had_previous_quadlet=false
had_previous_environment=false

atomic_install() {
  local source_file="$1" destination_file="$2" temporary_file
  temporary_file="$(mktemp "${destination_file}.new.XXXXXX")" || return 1
  cleanup_files+=("${temporary_file}")
  install -m 600 "${source_file}" "${temporary_file}" || return 1
  mv -f -- "${temporary_file}" "${destination_file}"
}

rollback() {
  printf 'Rolling back the environment and Quadlet configuration...\n' >&2
  systemctl --user stop "${SERVICE_NAME}" || true

  if [[ "${environment_replaced}" == true ]]; then
    if [[ "${had_previous_environment}" == true ]]; then
      atomic_install "${backup_directory}/previous.env" "${ENVIRONMENT_FILE}" || return 1
    else
      rm -f -- "${ENVIRONMENT_FILE}" || return 1
    fi
  fi

  if [[ "${had_previous_quadlet}" == true ]]; then
    atomic_install "${backup_directory}/previous.container" "${QUADLET_FILE}" || return 1
  else
    rm -f -- "${QUADLET_FILE}" || return 1
  fi
  systemctl --user daemon-reload || return 1
  if [[ "${had_previous_quadlet}" == true ]]; then
    systemctl --user restart "${SERVICE_NAME}" || return 1
  fi
}

cleanup() {
  local exit_status=$? keep_backup=false
  trap - EXIT INT TERM HUP
  set +e
  if [[ "${transaction_active}" == true ]]; then
    if ! rollback; then
      keep_backup=true
      printf 'ERROR: Rollback was incomplete; private recovery files remain in %s\n' "${backup_directory}" >&2
    fi
    exit_status=1
  fi
  rm -f -- "${cleanup_files[@]}"
  if [[ -n "${backup_directory}" && "${keep_backup}" == false ]]; then
    rm -f -- "${backup_directory}/previous.env" "${backup_directory}/previous.container"
    rmdir -- "${backup_directory}"
  fi
  exit "${exit_status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

for required_command in curl grep id install journalctl mktemp mv podman rm rmdir sed sleep stat systemctl uname; do
  command -v "${required_command}" >/dev/null 2>&1 ||
    fail "Missing required command on the VPS: ${required_command}"
done

case "$(uname -m)" in
  x86_64 | amd64) ;;
  *)
    fail "This workflow publishes linux/amd64 images; the VPS architecture is $(uname -m)"
    ;;
esac

if [[ ! "${IMAGE_REFERENCE}" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._/-]+:sha-[0-9a-f]{40}$ ]]; then
  fail "Unexpected image reference: ${IMAGE_REFERENCE}"
fi

[[ -f "${QUADLET_TEMPLATE}" ]] ||
  fail "Quadlet template not found: ${QUADLET_TEMPLATE}"

[[ "${ENVIRONMENT_FILE}" =~ ^/[-a-zA-Z0-9_./]+$ ]] ||
  fail "DEPLOYMENT_ENVIRONMENT_FILE must be an absolute path using letters, digits, dots, hyphens, underscores, and slashes"

check_private_environment() {
  local file="$1" environment_mode
  [[ -f "${file}" && -r "${file}" ]] ||
    fail "The deployment user must be able to read the environment file: ${file}"
  environment_mode="$(stat -c '%a' "${file}")"
  if (( (8#${environment_mode} & 077) != 0 )); then
    fail "${file} contains secrets and must not be accessible by group or others (use chmod 600)"
  fi
}

if [[ -n "${INCOMING_ENVIRONMENT_FILE}" ]]; then
  [[ "${INCOMING_ENVIRONMENT_FILE}" != "${ENVIRONMENT_FILE}" ]] ||
    fail "Stage the new environment separately from the installed production file"
  check_private_environment "${INCOMING_ENVIRONMENT_FILE}"
  environment_to_validate="${INCOMING_ENVIRONMENT_FILE}"
else
  [[ -f "${ENVIRONMENT_FILE}" ]] ||
    fail "Provide a generated NEW_ENVIRONMENT_FILE or provision ${ENVIRONMENT_FILE} before deployment"
  check_private_environment "${ENVIRONMENT_FILE}"
  environment_to_validate="${ENVIRONMENT_FILE}"
fi

if [[ -f "${ENVIRONMENT_FILE}" ]]; then
  check_private_environment "${ENVIRONMENT_FILE}"
fi

if grep -Eq '^[A-Z][A-Z0-9_]*=.*REPLACE_WITH_' "${environment_to_validate}"; then
  fail "The production environment still contains placeholder values"
fi

missing_variables=()
for variable_name in \
  DB_USERNAME DB_PASSWORD DB_REACTIVE_URL DB_JDBC_URL \
  FLYWAY_DB_USERNAME FLYWAY_DB_PASSWORD \
  GEOGRAPHIC_REFERENCE_BASE_URL \
  RABBITMQ_HOST RABBITMQ_USERNAME RABBITMQ_PASSWORD \
  PARTY_IDENTIFIER_ENCRYPTION_KEY_V1 PARTY_IDENTIFIER_CURRENT_ENCRYPTION_KEY_VERSION \
  PARTY_IDENTIFIER_INDEX_HMAC_KEY PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY \
  PARTY_CURSOR_SIGNING_KEY_V1; do
  if [[ "$(grep -Ec "^${variable_name}=" "${environment_to_validate}")" != 1 ]] ||
    ! grep -Eq "^${variable_name}=.+" "${environment_to_validate}"; then
    missing_variables+=("${variable_name}")
  fi
done

if (( ${#missing_variables[@]} > 0 )); then
  fail "Missing or duplicate production variables: ${missing_variables[*]}"
fi

missing_networks=()
for network_name in "${REQUIRED_NETWORKS[@]}"; do
  if ! podman network exists "${network_name}"; then
    missing_networks+=("${network_name}")
  fi
done

if (( ${#missing_networks[@]} > 0 )); then
  fail "Required Podman networks are missing for user $(id -un): ${missing_networks[*]}. Install and start them from the VPS infrastructure configuration"
fi

pull_options=()
if [[ -f "${REGISTRY_AUTH_FILE}" ]]; then
  registry_auth_mode="$(stat -c '%a' "${REGISTRY_AUTH_FILE}")"
  if (( (8#${registry_auth_mode} & 077) != 0 )); then
    fail "${REGISTRY_AUTH_FILE} must not be accessible by group or others (use chmod 600)"
  fi
  pull_options+=(--authfile "${REGISTRY_AUTH_FILE}")
fi

printf 'Pulling %s before restarting the service...\n' "${IMAGE_REFERENCE}"
if ! podman pull "${pull_options[@]}" "${IMAGE_REFERENCE}"; then
  fail "Unable to pull the image. If GHCR is private, log in with podman using ${REGISTRY_AUTH_FILE}"
fi

install -d -m 700 "${QUADLET_DIRECTORY}"
backup_directory="$(mktemp -d)"

if [[ -f "${QUADLET_FILE}" ]]; then
  install -m 600 "${QUADLET_FILE}" "${backup_directory}/previous.container"
  had_previous_quadlet=true
fi
if [[ -f "${ENVIRONMENT_FILE}" ]]; then
  install -m 600 "${ENVIRONMENT_FILE}" "${backup_directory}/previous.env"
  had_previous_environment=true
fi

new_quadlet="$(mktemp)"
cleanup_files+=("${new_quadlet}")
sed -e "s|@@IMAGE@@|${IMAGE_REFERENCE}|g" \
  -e "s|@@ENVIRONMENT_FILE@@|${ENVIRONMENT_FILE}|g" \
  "${QUADLET_TEMPLATE}" > "${new_quadlet}"

transaction_active=true
if [[ -n "${INCOMING_ENVIRONMENT_FILE}" ]]; then
  install -d -m 700 "${ENVIRONMENT_FILE%/*}"
  environment_replaced=true
  atomic_install "${INCOMING_ENVIRONMENT_FILE}" "${ENVIRONMENT_FILE}"
fi
atomic_install "${new_quadlet}" "${QUADLET_FILE}"
systemctl --user daemon-reload

if ! systemctl --user cat "${SERVICE_NAME}" >/dev/null; then
  fail "Quadlet could not generate ${SERVICE_NAME}; inspect the Podman generator output on the VPS"
fi

if ! systemctl --user restart "${SERVICE_NAME}"; then
  show_service_logs
  fail "The new Quadlet service did not start"
fi

healthy=false
for ((attempt = 1; attempt <= 60; attempt++)); do
  if curl --fail --silent --show-error --max-time 2 "${HEALTHCHECK_URL}" >/dev/null; then
    healthy=true
    break
  fi
  sleep 1
done

if [[ "${healthy}" != true ]]; then
  show_service_logs
  fail "The service did not become healthy at ${HEALTHCHECK_URL}"
fi

systemctl --user --no-pager --full status "${SERVICE_NAME}"
transaction_active=false
printf 'Deployment completed: %s\n' "${IMAGE_REFERENCE}"
