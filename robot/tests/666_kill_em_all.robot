*** Settings ***

Documentation  Kill all browsers
Resource       ../common_resource.robot

*** Test Cases ***

#Re-enable proxies
#  Set integration proxy on

Close all browsers
  [Tags]  ie8
  Close all browsers

Close all browsers
  [Tags]  integration
  Close all browsers

Close all browsers
  [Tags]  ajanvaraus
  Close all browsers
