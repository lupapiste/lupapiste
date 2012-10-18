#!/bin/bash

target=$1

if [ -z $target ] ; then
	target="tests"
fi

pybot --exclude integration --RunEmptySuite -d target $target