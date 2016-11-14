#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

pybot -d target --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable BROWSER:chrome --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:http://lupatest.solita.fi $target
