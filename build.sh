#!/bin/bash

if [ ! -d obj ]; then
    mkdir obj
fi

echo "-d obj" > opts
find lib -name "*.jar" | sed -e 's/^/-cp /' >> opts
find src -name "*.java" > srcs
set -x
if javac @opts @srcs; then
    jar cf DwarfForge.jar -C obj . -C etc .
fi
set +x
rm opts srcs

