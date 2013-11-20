@echo off

REM location of Maven JAR repository
SET M2="%HOMEPATH%\.m2\repository"

REM load JARs from Maven repository
SET CP=%M2%\orst\stratagusai\stratagusai-client\3.0-SNAPSHOT\stratagusai-client-3.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\log4j\log4j\1.2.14\log4j-1.2.14.jar
SET CP=%CP%;%M2%\commons-cli\commons-cli\1.1\commons-cli-1.1.jar
SET CP=%CP%;%M2%\org\yaml\snakeyaml\1.7\snakeyaml-1.7.jar
SET CP=%CP%;%M2%\org\antlr\antlr-runtime\3.1.3\antlr-runtime-3.1.3.jar

SET JAVA_CMD="java"

%JAVA_CMD% -ea -classpath %CP% orst.stratagusai.Main %*
