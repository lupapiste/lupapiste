*** Settings ***

Documentation   Init tests
Resource        ../../common_resource.robot

*** Test Cases ***

#Re-enable proxies
#  Set integration proxy on

Kill all browsers
  Close all browsers

Teardown (integration)
  [Tags]  integration
  #Set integration proxy on
  Close all browsers
