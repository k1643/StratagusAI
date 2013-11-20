#!/bin/sh

M2=${HOME}/.m2/repository

# load JARs from Maven repository
CP=${M2}/orst/stratagusai/stratagusai-client/3.0-SNAPSHOT/stratagusai-client-3.0-SNAPSHOT.jar
CP=${CP}:${M2}/log4j/log4j/1.2.14/log4j-1.2.14.jar
CP=${CP}:${M2}/commons-cli/commons-cli/1.1/commons-cli-1.1.jar
CP=${CP}:${M2}/org/yaml/snakeyaml/1.7/snakeyaml-1.7.jar
CP=${CP}:${M2}/org/antlr/antlr-runtime/3.1.3/antlr-runtime-3.1.3.jar

java -ea -classpath ${CP} orst.stratagusai.Main $*
