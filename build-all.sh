#!/usr/bin/env sh
# Builds and tests everything: engine tests, Fabric jar, Forge jar.  Made by Freak4U.
set -e
./gradlew engineTests
./gradlew -p fabric build
./gradlew -p forge build
echo "Jars: fabric/build/libs/ and forge/build/libs/"
