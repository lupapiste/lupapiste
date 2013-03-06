#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:https://lupatest.solita.fi $target
