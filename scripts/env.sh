#!/usr/bin/env bash
# Optional project-local overrides. Keep secrets out of source control.
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi
if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required for direct server runs; install Maven or use Docker Compose." >&2
fi
export PORT="${PORT:-8080}"
export BIND_ADDRESS="${BIND_ADDRESS:-127.0.0.1}"
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3307/unicomms?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC}"
export DB_USER="${DB_USER:-unicomms}"
export DB_PASSWORD="${DB_PASSWORD:-unicomms-local-only}"
export REGISTRATION_ENABLED="${REGISTRATION_ENABLED:-true}"
export ALLOWED_ORIGINS="${ALLOWED_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173}"
