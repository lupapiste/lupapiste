@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=http://lupadev.solita.fi

pybot -d target --exclude integration --exclude fail --RunEmptySuite --variable BROWSER:chrome --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:%server% %target%
