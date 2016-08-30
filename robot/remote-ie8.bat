@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=https://www-dev.lupapiste.fi

call remote-config.bat

pybot -d target --include ie8 --exclude integration --exclude ajanvaraus --exclude fail --variable BROWSER:internetexplorer --variable WAIT_DELAY:15 --variable SELENIUM:http://%remote%:%ieport%/wd/hub --variable SERVER:%server% %target%

