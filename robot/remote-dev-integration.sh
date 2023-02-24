#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

robot -d target --include integration --exclude fail --RunEmptySuite --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
