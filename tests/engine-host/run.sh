#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
export TZ=America/New_York
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_cmd="$JAVA_HOME/bin/java"
  javac_cmd="$JAVA_HOME/bin/javac"
else
  java_cmd=java
  javac_cmd=javac
fi
test_output="$(mktemp -d "${TMPDIR:-/tmp}/console-host.XXXXXXXX")"
trap 'rm -rf "$test_output"' EXIT
stubs=()
while IFS= read -r -d '' path; do stubs+=("$path"); done < <(find tests/engine-host/stubs -name '*.java' -print0)
classes=(ControllerEngine ControllerAuth Control Protocol SessionTotals WorkoutMath WorkoutRecord
  ActivityType WorkoutRecorder HeartReading GripPulse Calories ProfileData ProfileSettings)
sources=()
for name in "${classes[@]}"; do sources+=("app/src/main/java/org/opentread/console/$name.java"); done
"$javac_cmd" -d "$test_output" "${stubs[@]}" "${sources[@]}" tests/engine-host/{EngineHostTest,RecorderHostTest}.java
"$java_cmd" -cp "$test_output" org.opentread.console.EngineHostTest "${1:-1}"
"$java_cmd" -cp "$test_output" org.opentread.console.RecorderHostTest
