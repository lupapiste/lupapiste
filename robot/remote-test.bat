@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=https://test.lupapiste.fi

call remote-config.bat

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://%remote%:%port%/wd/hub --variable SERVER:%server% %target%
