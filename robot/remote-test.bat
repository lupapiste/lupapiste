@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=https://test.lupapiste.fi

set hubert=hubert.solita.fi
set bianca=192.168.7.253

rem set remote=%bianca%
set remote=%hubert%

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:%server% %target%
