@echo off
rem NOTE: requires Selenium > 2.25 version of selenium\webdriver\ie\service.py
rem (see http://code.google.com/p/selenium/issues/detail?id=4310).
rem Download: http://selenium.googlecode.com/svn-history/r17705/trunk/py/selenium/webdriver/ie/service.py
set target=%1
if [%target%]==[] set target=tests

set server=%2
if [%server%]==[] set server=localhost:8000

pybot -d target --exclude integration --exclude ajanvaraus --exclude fail --RunEmptySuite --variable BROWSER:internetexplorer --variable SERVER:http://%server%  %target%
