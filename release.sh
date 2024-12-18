#!/bin/bash

# Path to the keystore file
export RELEASE_KEYSTORE=/Users/pancake/development/pancake_bcr_release_key.jks

# Alias name for the release key
export RELEASE_KEY_ALIAS=pancake_bcr_release_key

# Read the keystore passphrase securely (no echo)
echo "Enter the keystore passphrase:"
read -r -s RELEASE_KEYSTORE_PASSPHRASE

# Read the key alias passphrase securely (no echo)
echo "Enter the key alias passphrase:"
read -r -s RELEASE_KEY_PASSPHRASE

# Export the passphrases as environment variables
export RELEASE_KEYSTORE_PASSPHRASE
export RELEASE_KEY_PASSPHRASE

echo "Environment variables for release keystore set successfully."

echo "Start release zip"
./gradlew zipRelease
