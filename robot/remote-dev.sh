#!/bin/bash

target=$@

if [ -z "$target" ] ; then
	target="tests"
fi

source remote-config.sh

pybot -d target --exclude fail --exclude integration --RunEmptySuite --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
