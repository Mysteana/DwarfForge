#!/bin/bash

if [ ! -d obj ]; then
    mkdir obj
fi

find src -name "*.java" > srcs
set -x
if javac -d obj/ -extdirs lib/ -Xlint @srcs; then
    jar cf DwarfForge.jar -C obj . -C etc .
fi
set +x
rm srcs

