#!/bin/bash

target=$@

if [ -z "$target" ] ; then
	target="tests"
fi

source remote-config.sh

pybot -d target --exclude fail --exclude integration --exclude ajanvaraus --RunEmptySuite --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
