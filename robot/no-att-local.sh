#!/bin/bash

target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

pybot --exclude integration --exclude ajanvaraus --exclude fail --exclude attachments --RunEmptySuite -d target $target
