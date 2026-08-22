#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="${1:-my-upload-key.jks}"
KEY_ALIAS="${KEY_ALIAS:-upload}"

if [[ -e "$KEYSTORE_PATH" ]]; then
  echo "Refusing to overwrite existing keystore: $KEYSTORE_PATH" >&2
  exit 1
fi

read -r -s -p "Keystore password: " STORE_PASSWORD
printf '\n'
read -r -s -p "Key password: " KEY_PASSWORD
printf '\n'

if [[ -z "$STORE_PASSWORD" || -z "$KEY_PASSWORD" ]]; then
  echo "Passwords must not be empty." >&2
  exit 1
fi

umask 077
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -storetype JKS \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Win rah train, OU=Mobile, O=Win rah train, L=Algiers, ST=Algiers, C=DZ"

chmod 600 "$KEYSTORE_PATH"
echo "Created upload keystore: $KEYSTORE_PATH"
echo "Alias: $KEY_ALIAS"
echo "Keep this keystore and both passwords in a secure backup. Never commit the file."
