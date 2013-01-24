#!/bin/bash

target=$@

if [ -z $target ] ; then
  target="tests"
fi

pybot --exclude integration --exclude fail --exclude attachments --RunEmptySuite -d target $target
