#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

pybot -d target --include ajanvaraus --exclude integration --exclude fail --RunEmptySuite --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
