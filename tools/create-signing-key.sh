#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE="$ROOT_DIR/alttile-release.jks"
PROPS="$ROOT_DIR/keystore.properties"
BASE64_FILE="$ROOT_DIR/alttile-release.jks.base64"

for path in "$KEYSTORE" "$PROPS" "$BASE64_FILE"; do
  if [[ -e "$path" ]]; then
    echo "ERROR: $path already exists; refusing to overwrite permanent signing material" >&2
    exit 1
  fi
done

read -rsp "AltTile keystore password: " STORE_PASSWORD
echo
if [[ -z "$STORE_PASSWORD" ]]; then
  echo "ERROR: password must not be empty" >&2
  exit 1
fi

umask 077
keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
  -storepass "$STORE_PASSWORD" -keypass "$STORE_PASSWORD" \
  -alias alttile -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=AltTile, OU=AltTile, O=AltTile, L=Unknown, ST=Unknown, C=IT"

cat > "$PROPS" <<EOF
storeFile=alttile-release.jks
storePassword=$STORE_PASSWORD
keyAlias=alttile
keyPassword=$STORE_PASSWORD
EOF

base64 "$KEYSTORE" | tr -d '\n' > "$BASE64_FILE"
chmod 600 "$KEYSTORE" "$PROPS" "$BASE64_FILE"

echo "Created local signing material. Configure the four ALTTILE_* GitHub secrets."
echo "BACK UP your private keystore and password outside this repository."
