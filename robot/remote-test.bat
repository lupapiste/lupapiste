@set target=%1
@if [%target%]==[] set target=tests

pybot -d target --variable BROWSER:firefox --variable SELENIUM:http://192.168.7.172:4444/wd/hub --variable SERVER:lupatest.solita.fi %target%
