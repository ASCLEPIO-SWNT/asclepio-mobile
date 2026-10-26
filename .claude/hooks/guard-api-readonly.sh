#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit) — clientes: ../asclepio-api es de solo lectura (contrato y briefs).
f=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)
[[ -n "$f" ]] || exit 0
root="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
api=$(realpath -m "$root/../asclepio-api")
target=$(cd "$root" && realpath -m "$f")
if [[ "$target" == "$api" || "$target" == "$api"/* ]]; then
  echo "BLOQUEADO por el harness: ../asclepio-api es de solo lectura desde este repo. Los cambios de contrato o de producto se hacen en una sesión de asclepio-api." >&2
  exit 2
fi
exit 0
