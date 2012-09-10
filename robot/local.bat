@set target=%1
@if [%target%]==[] set target=tests

pybot -d target %target%
