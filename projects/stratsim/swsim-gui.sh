#!/bin/bash


if [ ! -f ../../tools/classpaths ] ; then
        echo "../../tools/classpaths not found"
        exit 1
fi
source ../../tools/classpaths
echo $CP

java -ea -classpath $CP orst.stratagusai.stratsim.gui.Main $*