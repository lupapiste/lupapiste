*** Settings ***

Documentation   Suti settings
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo admin sets suti server address
  Sipoo logs in
  Go to page  backends
  Element should not be visible  ${save_indicator}

  [Teardown]  Logout
