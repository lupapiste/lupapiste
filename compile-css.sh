#!/bin/sh
COMPILE_ENV=production
if [ "$1" = dev ]; then 
	COMPILE_ENV=development
fi

compass compile resources/private/common-html -e $COMPILE_ENV --force
