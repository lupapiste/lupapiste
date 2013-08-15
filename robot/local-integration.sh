#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

pybot --include integration --exclude fail --RunEmptySuite -d target $target
