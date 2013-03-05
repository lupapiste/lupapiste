#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --exclude integration --exclude fail --RunEmptySuite --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:http://lupadev.solita.fi $target
