@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=lupatest.solita.fi

pybot -d target --variable BROWSER:internetexplorer --variable SELENIUM:http://192.168.7.223:4444/wd/hub --variable SERVER:%server% %target%
