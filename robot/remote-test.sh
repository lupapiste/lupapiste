#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

robot -d target --variable BROWSER:firefox --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:https://test.lupapiste.fi $target
