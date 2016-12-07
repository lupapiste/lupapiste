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

robot -d target --include ie8 --exclude integration --exclude ajanvaraus --exclude fail --variable BROWSER:internetexplorer --variable WAIT_DELAY:15 --variable SELENIUM:http://$remote:$ieport/wd/hub --variable SERVER:$server $target
