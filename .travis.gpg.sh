#!/usr/bin/env bash
# Based on: https://github.com/idealo/logback-redis/wiki/Release-Process
# Original work by James Ward: https://github.com/jamesward/travis-central-test/blob/master/.travis.gpg.sh
set -e

if [[ ! -z "${TRAVIS}" && ! -z "${GPG_NAME}" && ! -z "${GPG_EMAIL}" ]]; then

    export GPG_PASSPHRASE=$(echo "$RANDOM$(date)" | md5sum | cut -d\  -f1)

    echo -e "%echo Generating a basic OpenPGP key" >> gen-key-script
    echo -e "Key-Type: RSA" >> gen-key-script
    echo -e "Key-Length: 4096" >> gen-key-script
    echo -e "Subkey-Type: 1" >> gen-key-script
    echo -e "Subkey-Length: 4096" >> gen-key-script
    echo -e "Name-Real: ${GPG_NAME}" >> gen-key-script
    echo -e "Name-Email: ${GPG_EMAIL}" >> gen-key-script
    echo -e "Expire-Date: 1d" >> gen-key-script
    echo -e "Passphrase: ${GPG_PASSPHRASE}" >> gen-key-script
    echo -e "%commit" >> gen-key-script
    echo -e "%echo done" >> gen-key-script

    gpg --batch --gen-key gen-key-script

    export GPG_KEY_NAME=$(gpg -K | grep ^sec | cut -d/  -f2 | cut -d\  -f1 | head -n1)

    shred gen-key-script

    gpg --keyserver keyserver.ubuntu.com --send-keys ${GPG_KEY_NAME}

    while(true); do
        date
        gpg --keyserver keyserver.ubuntu.com  --recv-keys ${GPG_KEY_NAME} && break || sleep 30
    done
else
    echo "The TRAVIS, GPG_NAME, and GPG_EMAIL env vars must be set"
    exit 1
fi
