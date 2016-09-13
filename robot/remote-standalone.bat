@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=http://localhost:8000

call remote-config.bat

pybot --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:%server% -d target common/setup %target% common/teardown
