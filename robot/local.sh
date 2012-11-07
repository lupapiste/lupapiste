#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot --exclude integration --exclude fail --RunEmptySuite -d target $target
