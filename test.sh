#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Deterministic synthetic calendar fixtures; production follows the tablet's local timezone.
export TZ=America/New_York
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_cmd="$JAVA_HOME/bin/java"
  javac_cmd="$JAVA_HOME/bin/javac"
else
  java_cmd=java
  javac_cmd=javac
fi
test_output="$(mktemp -d "${TMPDIR:-/tmp}/console-tests.XXXXXXXX")"
trap 'rm -rf "$test_output"' EXIT
mkdir -p "$test_output/pure"
classes=(Control Protocol SessionTotals WorkoutMath WorkoutRecord Stats HeartPacket ActivityType
  VideoGate ProfileTheme ControllerAuth PersonalBests IdlePolicy HeartReading GripPulse TrackProgress
  MediaPolicy RibbonPlot WakeTouchGuard Calories ProfileData)
sources=()
for name in "${classes[@]}"; do sources+=("app/src/main/java/org/opentread/console/$name.java"); done
"$javac_cmd" -d "$test_output/pure" "${sources[@]}" tests/*.java
"$java_cmd" -cp "$test_output/pure" ConsoleTest
python3 tests/migration_test.py
python3 tests/heart_migration_test.py
python3 tests/calorie_migration_test.py
python3 tests/power_loss_test.py
python3 tests/response_code_test.py
bash tests/engine-host/run.sh "${1:-1}"
