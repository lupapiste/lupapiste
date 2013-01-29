*** Settings ***

Documentation   Open browser
Resource        ../../common_resource.robot

*** Test Cases ***

The shared browser is opened
  Open browser to login page

Login page is in Finnish
  Page Should Not Contain  Suomeksi
  Page Should Contain  På svenska
