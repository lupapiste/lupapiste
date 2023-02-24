@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=localhost:8000

pybot --include integration --exclude fail --RunEmptySuite --variable SERVER:http://%server% -d target common/setup %target% common/teardown
