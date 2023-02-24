#!/bin/bash

[ -z "$1" ] && echo "Give browser as first arg (ie: headlesschrome), optional second args are the tests" && exit 1

browser=$1
shift
target=$@

if [ -z "$target" ] ; then
  target="tests"
fi

robot --variable BROWSER:$browser --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite -d target common/setup $target common/teardown
