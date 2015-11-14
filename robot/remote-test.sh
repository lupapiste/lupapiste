#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

source remote-config.sh

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:https://test.lupapiste.fi $target
