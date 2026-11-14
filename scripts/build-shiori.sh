#!/bin/zsh
# Builds both arm64 release APKs (R8, signed with the debug key as before) into build-out/; see RELEASING-SHIORI.md.
set -e
cd "${0:A:h}/.."
export JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=$HOME/Library/Android/sdk
mkdir -p build-out
log=build-out/build-shiori.log
apk=app/build/outputs/apk/release/app-arm64-v8a-release.apk

build() {
  ./gradlew -q -Pshiori.social=$1 :app:assembleRelease > $log 2>&1 || { echo "$2 FAILED"; tail -20 $log; exit 1; }
  cp $apk build-out/$2
  echo "$2 ok"
}

build true Shiori-Friends-arm64.apk
build false Shiori-arm64.apk
./gradlew --stop >/dev/null 2>&1
