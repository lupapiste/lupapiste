#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

robot --variable BROWSER:chrome -d target --exclude fail --exclude integration --exclude ajanvaraus --RunEmptySuite --variable SERVER:https://www-dev.lupapiste.fi common/setup $target common/teardown
