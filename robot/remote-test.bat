@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=lupatest.solita.fi
# test.lupapiste.fi

set hubert=192.168.7.223
set bianca=192.168.7.253

rem set remote=%bianca%
set remote=%hubert%

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:http://%server% %target%
