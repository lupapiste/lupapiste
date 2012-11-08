#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --exclude integration --exclude fail --RunEmptySuite --variable SELENIUM:http://192.168.7.253:4444/wd/hub --variable SERVER:http://129.35.251.17 $target
