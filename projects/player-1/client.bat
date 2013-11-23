@echo off

call ..\..\tools\classpaths

REM Call client Main class. This will load a configuration that specifies
REM what other classes will be run as Controllers.
java -ea -classpath %CP% orst.stratagusai.Main %*
