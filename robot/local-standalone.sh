#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

pybot --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite -d target common/setup $target common/teardown
