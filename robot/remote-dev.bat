@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=lupadev.solita.fi

set hubert=192.168.7.223
set bianca=192.168.7.253

rem set remote=%bianca%
set remote=%hubert%

pybot -d target --exclude fail --RunEmptySuite --variable BROWSER:firefox --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:http://%server% %target%
