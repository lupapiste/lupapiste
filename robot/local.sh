#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

robot --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite -d target $target
