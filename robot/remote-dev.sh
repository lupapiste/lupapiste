#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --variable SELENIUM:http://192.168.7.172:4444/wd/hub --variable SERVER:lupadev.solita.fi $target
