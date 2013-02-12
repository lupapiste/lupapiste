*** Settings ***

Documentation   Sonja can't submit application
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application the fast way  submit-app  753  75341600250030
  Add comment  huba huba
  Logout

Sonja can submit application
  Sonja logs in
  Open application  submit-app
  Wait until  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']
  Logout

Mikko could submit application
  Mikko logs in
  Open application  submit-app
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']

Submit date is not be visible
  Element should not be visible  xpath=//span[@data-test-id='application-submitted-date']

Mikko submits application
  Click element  xpath=//*[@data-test-id='application-submit-btn']

Mikko cant re-submit application
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']

Submit date should be visible
  Wait until  Element should be visible  xpath=//span[@data-test-id='application-submitted-date']

