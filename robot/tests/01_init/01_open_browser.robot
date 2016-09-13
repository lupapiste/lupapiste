*** Settings ***

Documentation   Open browser
Resource        ../../common_resource.robot

*** Test Cases ***

The shared browser is opened
  [Tags]  ie8
  Open browser to login page

The shared browser is opened for integration tests
  [Tags]  integration  ajanvaraus
  Open browser to login page

#Disable proxy
#  Set integration proxy off

Login page is in Finnish
  [Tags]  ie8
  Element Should Not Contain  language-select  SV
  Element Should Contain  language-select  FI
