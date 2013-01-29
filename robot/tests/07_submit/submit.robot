*** Settings ***

Documentation   Sonja can't submit application
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja can't submit application
  Sonja logs in
  Open the application
  Wait until  Element should be disabled  test-submit-application
  Logout

Mikko could submit application
  Mikko logs in
  Open the application
  Wait Until  Element should be enabled  test-submit-application

Submit date is not be visible
  Element should not be visible  test-application-submitted

Mikko submits application
  Click element  test-submit-application

Mikko cant re-submit application
  Wait Until  Element should be disabled  test-submit-application

Submit date should be visible
  Wait until  Element should be visible  test-application-submitted
