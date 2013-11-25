REM this file sets a classpath of commonly used JARS for projects\strategent
REM applications.
REM @echo off

REM location of Maven JAR repository
SET M2="%HOMEDRIVE%\%HOMEPATH%\.m2\repository"

REM load JARs from Maven repository
REM you can tell the path of the JAR from its maven pom.xml file entry.
REM 
REM     <stratmanVersion>2.0-SNAPSHOT</stratmanVersion>
REM     <stratplanVersion>2.0-SNAPSHOT</stratplanVersion>
REM	<tacmanVersion>1.0-SNAPSHOT</tacmanVersion>
REM	<stratmanIntegrationVersion>2.0-INTEGRATION</stratmanIntegrationVersion>
REM     <clientVersion>3.0-SNAPSHOT</clientVersion>

REM stratagusai client JAR and JARS it depends on
SET CP=%M2%\orst\stratagusai\stratagusai-client\3.0-SNAPSHOT\stratagusai-client-3.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\log4j\log4j\1.2.14\log4j-1.2.14.jar
SET CP=%CP%;%M2%\commons-cli\commons-cli\1.1\commons-cli-1.1.jar
SET CP=%CP%;%M2%\org\yaml\snakeyaml\1.7\snakeyaml-1.7.jar
SET CP=%CP%;%M2%\net\sf\jung\jung-api\2.0.1\jung-api-2.0.1.jar
SET CP=%CP%;%M2%\net\sf\jung\jung-graph-impl\2.0.1\jung-graph-impl-2.0.1.jar
SET CP=%CP%;%M2%\net\sf\jung\jung-algorithms\2.0.1\jung-algorithms-2.0.1.jar
SET CP=%CP%;%M2%\net\sourceforge\collections\collections-generic\4.01\collections-generic-4.01.jar
SET CP=%CP%;%M2%\org\antlr\antlr-runtime\3.1.3\antlr-runtime-3.1.3.jar

SET CP=%CP%;%M2%\orst\stratagusai\stratplan\2.0-SNAPSHOT\stratplan-2.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\orst\stratagusai\prodman\2.0-SNAPSHOT\prodman-2.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\orst\stratagusai\prodh\1.0-SNAPSHOT\prodh-1.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\orst\stratagusai\stratsim\1.0-SNAPSHOT\stratsim-1.0-SNAPSHOT.jar

REM matrix math and LP solver for tac-lp
SET CP=%CP%;%M2%\orst\stratagusai\tac-lp\1.0-SNAPSHOT\tac-lp-1.0-SNAPSHOT.jar
SET CP=%CP%;%M2%\org\apache\commons\commons-math\2.0\commons-math-2.0.jar
SET CP=%CP%;%M2%\org\gnu\glpk\glpk-java\4.47\glpk-java-4.47.jar

REM TODO: all these dependencies drawn in by BeanUtils might not be worth it.
REM BeanUtils used in GameStateEval.g to set properties.
SET CP=%CP%;%M2%\commons-beanutils\commons-beanutils\1.8.3\commons-beanutils-1.8.3.jar
SET CP=%CP%;%M2%\commons-collections\commons-collections\3.2.1\commons-collections-3.2.1.jar
SET CP=%CP%;%M2%\commons-logging\commons-logging\1.1.1\commons-logging-1.1.1.jar
