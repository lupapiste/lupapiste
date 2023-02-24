@set target=%1
@if [%target%]==[] set target=tests

pybot -d target --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable BROWSER:chrome %target%
