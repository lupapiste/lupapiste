@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=lupatest.solita.fi

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://192.168.7.172:4444/wd/hub --variable SERVER:%server% %target%
