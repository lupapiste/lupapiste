#!/bin/bash

target=$@

if [ -z $target ] ; then
  target="tests"
fi

# download lastest chromedriver from http://chromedriver.storage.googleapis.com/index.html
# put it to your path and maek it executablez

robot --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable BROWSER:chrome -d target $target
