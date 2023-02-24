#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

robot -d target --exclude fail --exclude integration --exclude ajanvaraus --RunEmptySuite --variable SELENIUM:http://USERNAME:APIKEY@hub.browserstack.com:80/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
