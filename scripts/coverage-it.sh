#!/usr/bin/env bash
# Coverage of the smoke tests (*IT), which run the shaded agent jar in separate JVMs with the JaCoCo agent.
# Shading rewrites the bytecode of the agent's own classes (relocated references), so this report is built against
# the classes inside the shaded jar, not target/classes. Its line numbers are the same, so tools such as Codecov can
# merge it with the unit test report (target/site/jacoco/jacoco.xml).
#
#   mvn verify -Dgpg.skip=true && scripts/coverage-it.sh   ->  target/site/jacoco-it/{index.html,jacoco.xml}
set -euo pipefail

JACOCO_VERSION=$(mvn -q help:evaluate -Dexpression=jacoco.version -DforceStdout)
JAR=$(ls target/ap-agent-*.jar | grep -vE 'sources|javadoc|original' | head -1)
EXEC=target/jacoco-it.exec
[ -f "$EXEC" ] || { echo "no $EXEC: run mvn verify first" >&2; exit 1; }

mvn -q dependency:copy -Dartifact="org.jacoco:org.jacoco.cli:${JACOCO_VERSION}:jar:nodeps" -DoutputDirectory=target/jacoco-cli
rm -rf target/it-classes && mkdir -p target/it-classes target/site/jacoco-it
# the agent's own classes only, not its relocated dependencies (io/github/dpsoft/ap/libs)
unzip -q -o "$JAR" 'io/github/dpsoft/ap/*' -x 'io/github/dpsoft/ap/libs/*' -d target/it-classes

java -jar "target/jacoco-cli/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar" report "$EXEC" \
  --classfiles target/it-classes --sourcefiles src/main/java \
  --name "smoke tests (-javaagent)" --xml target/site/jacoco-it/jacoco.xml --html target/site/jacoco-it
echo "target/site/jacoco-it/jacoco.xml"
