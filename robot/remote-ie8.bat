@set target=%1
@if [%target%]==[] set target=tests

@set server=%2
@if [%server%]==[] set server=https://www-dev.lupapiste.fi

set hubert=hubert.solita.fi
set hubertxp=192.168.7.122

set remote=%hubertxp%

pybot -d target --include ie8 --exclude integration --exclude fail --variable BROWSER:internetexplorer --variable WAIT_DELAY:15 --variable SELENIUM:http://%remote%:4444/wd/hub --variable SERVER:%server% %target%
