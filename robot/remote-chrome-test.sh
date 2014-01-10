#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

hubert='192.168.7.223'

remote=$hubert

pybot -d target --exclude integration --exclude fail --RunEmptySuite --variable BROWSER:chrome --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:http://lupatest.solita.fi $target
