#!/bin/bash

target=$@

if [ -z $target ] ; then
  target="tests"
fi

pybot --exclude integration --exclude fail --RunEmptySuite -d target $target
