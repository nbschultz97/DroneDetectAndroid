#!/usr/bin/env sh

# Simplified Gradle wrapper for air-gapped builds.

app_path=$(dirname "$0")
GRADLE_WRAPPER_JAR="$app_path/gradle/wrapper/gradle-wrapper.jar"
GRADLE_WRAPPER_MAIN="org.gradle.wrapper.GradleWrapperMain"

# Download wrapper jar if missing.
if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
  echo "Gradle wrapper JAR missing: $GRADLE_WRAPPER_JAR"
  echo "Fetch the wrapper JAR from a trusted host before building."
  exit 1
fi

CLASSPATH=$GRADLE_WRAPPER_JAR
JAVA_OPTS=${JAVA_OPTS:-"-Xmx1g"}
exec java $JAVA_OPTS -cp "$CLASSPATH" $GRADLE_WRAPPER_MAIN "$@"
