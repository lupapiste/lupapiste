@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=localhost:8000

pybot --exclude integration --exclude fail --RunEmptySuite --variable SELENIUM:http://192.168.7.253:4444/wd/hub --variable SERVER:http://%server% -d target common/setup %target% common/teardown
