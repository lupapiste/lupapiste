@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=localhost:8000

@set startTime=%time%
@echo Start Time: %startTime%
call pybot --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable SERVER:http://%server% -d target common/setup %target% common/teardown
@echo Done
@echo Start Time:  %startTime%
@echo Finish Time: %time%
