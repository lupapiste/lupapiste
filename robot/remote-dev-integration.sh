#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

hubert='192.168.7.223'
bianca='192.168.7.253'

#remote=$bianca
remote=$hubert

pybot -d target --include integration --exclude fail --RunEmptySuite --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:https://www-dev.lupapiste.fi $target
