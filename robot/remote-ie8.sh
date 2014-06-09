#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

server=$2
if [ -z $server ] ; then
	server="https://www-dev.lupapiste.fi"
fi

hubert='hubert.solita.fi'
hubertxp='192.168.7.122'

remote=$hubertxp

pybot -d target --include ie8 --exclude integration --exclude fail --variable BROWSER:internetexplorer --variable WAIT_DELAY:15 --variable SELENIUM:http://$remote:4444/wd/hub --variable SERVER:$server $target
