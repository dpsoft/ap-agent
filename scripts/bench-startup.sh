#!/usr/bin/env bash
# Measures the agent startup time ("Startup completed in N ms") and how many classes it loads.
#
#   scripts/bench-startup.sh [agent.jar] [runs]
#
# Defaults to the jar built by `mvn package` and 15 runs. Uses `java` from PATH (or $JAVA_HOME).
set -euo pipefail

JAR=${1:-$(ls target/ap-agent-*.jar | grep -vE 'sources|javadoc|original' | head -1)}
RUNS=${2:-15}
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# a minimal application that exits on its own (the agent's HTTP server threads are not daemons)
cat > "$WORK/Idle.java" <<'JAVA'
public class Idle { public static void main(String[] a) throws Exception { Thread.sleep(800); System.exit(0); } }
JAVA
"${JAVA}c" -d "$WORK" "$WORK/Idle.java"

for i in $(seq "$RUNS"); do
  port=$((20000 + RANDOM % 20000))
  timeout 30 "$JAVA" -Xlog:class+load=info:file="$WORK/classes.log" -javaagent:"$JAR" \
      -Dap-agent.server.port="$port" -Dap-agent.show-banner=false -cp "$WORK" Idle > "$WORK/run.log" 2>&1
  ms=$(grep -oE 'Startup completed in [0-9]+' "$WORK/run.log" | grep -oE '[0-9]+$' || echo NaN)
  classes=$(grep -c "source: .*$(basename "$JAR")" "$WORK/classes.log" || true)
  echo "$ms $classes"
done | sort -n | awk -v jar="$JAR" -v bytes="$(wc -c < "$JAR")" '
  { ms[NR] = $1; classes = $2 }
  END { printf "%s (%d bytes): startup median=%d ms min=%d max=%d over %d runs, %d classes loaded from the agent\n",
               jar, bytes, ms[int((NR + 1) / 2)], ms[1], ms[NR], NR, classes }'
