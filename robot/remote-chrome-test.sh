#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --variable BROWSER:chrome --variable SELENIUM:http://192.168.7.172:4444/wd/hub --variable SERVER:http://lupatest.solita.fi $target
