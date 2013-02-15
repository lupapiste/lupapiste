#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

server=$2
if [ -z $server ] ; then
	server="lupadev.solita.fi"
fi

pybot -d target --exclude integration --exclude fail --variable BROWSER:internetexplorer --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:http://$server $target
