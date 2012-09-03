#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot -d target --variable BROWSER:internetexplorer --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:lupatest.solita.fi $target
