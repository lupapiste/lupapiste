#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

server=$2
if [ -z $server ] ; then
	server="lupatest.solita.fi"
fi

pybot -d target --variable BROWSER:internetexplorer --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:$server $target
