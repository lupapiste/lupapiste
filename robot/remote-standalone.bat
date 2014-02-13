@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=http://localhost:8000

set hubert=192.168.7.223
set bianca=192.168.7.253

rem set remote=%bianca%
set remote=%hubert%

pybot --exclude integration --exclude fail --RunEmptySuite --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:%server% -d target common/setup %target% common/teardown
