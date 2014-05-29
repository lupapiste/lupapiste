#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

hubert='hubert.solita.fi'
bianca='192.168.7.253'

#remote=$bianca
remote=$hubert

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:https://test.lupapiste.fi $target
