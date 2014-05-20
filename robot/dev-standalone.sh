#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

hubert='hubert.solita.fi'
bianca='192.168.7.253'

remote=$hubert

pybot -d target --exclude fail --exclude integration --RunEmptySuite --variable SERVER:https://www-dev.lupapiste.fi common/setup $target common/teardown
