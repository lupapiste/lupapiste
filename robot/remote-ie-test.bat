@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=lupadev.solita.fi

set hubert=192.168.7.223

set remote=%hubert%

pybot -d target --exclude integration --exclude fail --variable BROWSER:internetexplorer --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:http://%server% %target%
