#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

server=$2
if [ -z $server ] ; then
	server="https://www-dev.lupapiste.fi"
fi

source remote-config.sh

pybot -d target --exclude integration --exclude ajanvaraus --exclude fail --variable BROWSER:internetexplorer --variable SELENIUM:http://$remote:$port/wd/hub --variable SERVER:$server $target
