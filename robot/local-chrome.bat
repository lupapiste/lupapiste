@set target=%1
@if [%target%]==[] set target=tests

pybot -d target --exclude integration --RunEmptySuite --variable BROWSER:chrome %target%
