#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../../../.." && pwd)"
gate="${1:-}"

cd "$repo_root"

module_gate() {
    local module="$1"
    exec ./gradlew ":${module}:test" ":${module}:javadoc" --warning-mode=fail
}

fixture_gate() {
    if [[ "$(uname -s)" == "Linux" ]]; then
        if ! command -v xvfb-run >/dev/null 2>&1; then
            echo "fixture gate requires xvfb-run on Linux" >&2
            exit 2
        fi
        exec xvfb-run -a ./gradlew :effects-fixtures:test --rerun-tasks --warning-mode=fail
    fi
    exec ./gradlew :effects-fixtures:testClasses --warning-mode=fail
}

full_gate() {
    if [[ "$(uname -s)" == "Linux" ]]; then
        if ! command -v xvfb-run >/dev/null 2>&1; then
            echo "full gate requires xvfb-run on Linux" >&2
            exit 2
        fi
        exec xvfb-run -a ./gradlew clean check javadoc --warning-mode=fail
    fi
    ./gradlew clean check javadoc \
        -x :effects-fixtures:test --warning-mode=fail
    exec ./gradlew :effects-fixtures:testClasses --warning-mode=fail
}

case "$gate" in
    core) module_gate effects-core ;;
    libgdx) module_gate effects-libgdx ;;
    protocol) module_gate effects-protocol ;;
    mcp) module_gate effects-mcp ;;
    fixture) fixture_gate ;;
    check) exec ./gradlew check javadoc --warning-mode=fail ;;
    full) full_gate ;;
    *)
        echo "usage: $0 {core|libgdx|protocol|mcp|fixture|check|full}" >&2
        exit 2
        ;;
esac
