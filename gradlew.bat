@ECHO OFF
SETLOCAL

SET APP_PATH=%~dp0
SET WRAPPER_JAR=%APP_PATH%\gradle\wrapper\gradle-wrapper.jar
SET WRAPPER_MAIN=org.gradle.wrapper.GradleWrapperMain

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Gradle wrapper JAR missing: %WRAPPER_JAR%
  ECHO Fetch the wrapper JAR from a trusted host before building.
  EXIT /B 1
)

SET CLASSPATH=%WRAPPER_JAR%
SET JAVA_OPTS=%JAVA_OPTS% -Xmx1g
java %JAVA_OPTS% -cp "%CLASSPATH%" %WRAPPER_MAIN% %*
ENDLOCAL
