#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

hubert='192.168.7.223'
bianca='192.168.7.253'

remote=$bianca

pybot -d target --exclude fail --exclude integration --RunEmptySuite --variable SERVER:https://www-dev.lupapiste.fi common/setup $target common/teardown
