#!/bin/bash

# Pass: pancake123
# Path to the keystore file
export RELEASE_KEYSTORE=/Users/pancake/development/pancake_bcr_release_key.jks

# Alias name for the release key
export RELEASE_KEY_ALIAS=pancake_bcr_release_key

# Export the passphrases as environment variables
export RELEASE_KEYSTORE_PASSPHRASE=pancake123 
export RELEASE_KEY_PASSPHRASE=pancake123

echo "Environment variables for release keystore set successfully."

echo "Start release zip"
rm -rf ./app/build/distributions/release/
./gradlew zipRelease
