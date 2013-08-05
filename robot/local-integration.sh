#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

pybot --include integration --RunEmptySuite -d target $target
