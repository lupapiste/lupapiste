#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

robot --include integration --exclude fail --RunEmptySuite -d target common/setup $target common/teardown
