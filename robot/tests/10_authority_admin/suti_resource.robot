*** Settings ***

Documentation  Suti admin tasks that are needed both in Suti admin and application robots.
Resource       ../../common_resource.robot

*** Keywords ***

Suti server
  [Arguments]  ${url}  ${username}  ${password}
  Go to page  backends
  Scroll to test id  suti-password  
  Fill test id  suti-url  ${url}
  Fill test id  suti-username  ${username}
  Fill test id  suti-password  ${password}

  Positive indicator should not be visible
  Scroll and click test id  suti-send
  Positive indicator should be visible

