#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

pybot --include integration --exclude fail --RunEmptySuite --variable BROWSER:chrome -d target common/setup $target common/teardown
