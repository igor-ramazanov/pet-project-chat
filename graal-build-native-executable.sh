#!/bin/bash
set -euo pipefail
trap "echo 'error: Script failed: see failed command above'" ERR

echo "sbt appJVM/assembly"
sbt appJVM/assembly

echo "native-image --static --enable-http -H:IncludeResources='.*' -H:ReflectionConfigurationFiles=graal-native-reflect.json -jar app/jvm/target/scala-2.12/pet-project-chat-backend-assembly-1.0.jar"
native-image --static --enable-http -H:IncludeResources='.*' -H:ReflectionConfigurationFiles=graal-native-reflect.json -jar app/jvm/target/scala-2.12/pet-project-chat-backend-assembly-1.0.jar