@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=localhost:8000

pybot --exclude firefox --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable BROWSER:chrome --variable SERVER:http://%server% -d target common/setup %target% common/teardown
