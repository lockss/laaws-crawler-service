#!/usr/bin/env bash

if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_80e12d08aaa4_key -iv $encrypted_80e12d08aaa4_iv -in codesigning.asc.enc -out codesigning.asc -d
    gpg --fast-import ci/codesigning.asc
    docker login -u $DOCKER_USERNAME -p $DOCKER_PASSPHRASE ;
fi
