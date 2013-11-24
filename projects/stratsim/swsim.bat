@echo off

call ..\..\tools\classpaths

java -ea -classpath %CP% orst.stratagusai.stratsim.analysis.SwitchingPlannerSimulation %*
