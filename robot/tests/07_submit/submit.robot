*** Settings ***

Documentation   Sonja can't submit application
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja can't submit application
  [Tags]  fail
  Sonja logs in
  Open some application
  Wait until  Element should be disabled  test-submit-application
  Logout

Mikko could submit application
  [Tags]  fail
  Mikko logs in
  Open some application
  Wait Until  Element should be enabled  test-submit-application

Submit date is not be visible
  [Tags]  fail
  Element should not be visible  test-application-submitted

Mikko submits application
  [Tags]  fail
  Click element  test-submit-application

Mikko cant re-submit application
  [Tags]  fail
  Wait Until  Element should be disabled  test-submit-application

Submit date should be visible
  [Tags]  fail
  Wait until  Element should be visible  test-application-submitted