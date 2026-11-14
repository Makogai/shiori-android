#!/bin/sh
# Rebuilds app/libs/quickjs-kt-android-<version>-qjskt.aar from quickjs-kt's published AAR
# (https://github.com/dokar3/quickjs-kt, Apache-2.0; QuickJS is MIT).
#
# Its native library is renamed from libquickjs.so to libqjskt.so (file name and soname), because
# zhanghai's quickjs-android, which extensions use through app.cash.quickjs, ships a libquickjs.so too
# and an APK holds one file per name. JsRuntime loads libqjskt before the first QuickJs.create().
set -eu

version=1.0.18
here=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

curl -fsSL -o "$work/in.aar" \
    "https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/$version/quickjs-kt-android-$version.aar"
mkdir "$work/aar"
(cd "$work/aar" && unzip -q ../in.aar)
for so in "$work"/aar/jni/*/libquickjs.so; do
    # Same-length replacement keeps the ELF string table intact.
    python3 - "$so" <<'EOF'
import sys
path = sys.argv[1]
data = open(path, "rb").read()
old, new = b"libquickjs.so\0", b"libqjskt.so\0\0\0"
assert data.count(old) == 1, path
open(path, "wb").write(data.replace(old, new))
EOF
    mv "$so" "$(dirname "$so")/libqjskt.so"
done
rm -f "$here/app/libs/quickjs-kt-android-$version-qjskt.aar"
mkdir -p "$here/app/libs"
(cd "$work/aar" && zip -q -X -r "$here/app/libs/quickjs-kt-android-$version-qjskt.aar" .)
