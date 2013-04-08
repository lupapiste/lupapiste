#!/bin/bash

target=$@

if [ -z $target ] ; then
  target="tests"
fi

# download lastest chromedriver from https://code.google.com/p/chromedriver/downloads/list
# put it to your path and maek it executable

pybot --exclude integration --exclude fail --RunEmptySuite --variable BROWSER:chrome -d target $target
