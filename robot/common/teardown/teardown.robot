*** Settings ***

Documentation   Kill browsers
Resource        ../../common_resource.robot

*** Test Cases ***

#Re-enable proxies
#  Set integration proxy on

Teardown
  [Tags]  ie8
  Close all browsers

Teardown (integration)
  [Tags]  integration
  #Set integration proxy on
  Close all browsers

Teardown (ajanvaraus)
  [Tags]  ajanvaraus
  Close all browsers
