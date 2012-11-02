@set target=%1
@if [%target%]==[] set target=tests

pybot -d target --exclude integration --exclude fail --RunEmptySuite --variable BROWSER:chrome %target%
