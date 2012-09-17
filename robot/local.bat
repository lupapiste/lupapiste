@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=localhost:8000

pybot -d target --variable SERVER:%server%  %target%
