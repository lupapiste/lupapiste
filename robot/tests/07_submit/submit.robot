*** Settings ***

Documentation   Sonja can't submit application
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application the fast way  submit-app  753  753-416-25-30  asuinrakennus
  Open to authorities  huba huba
  Logout

Sonja can not submit application
  Sonja logs in
  Open application  submit-app  753-416-25-30
  Wait until  Element should not be visible  application-requiredFieldSummary-tab
  Logout

Mikko could submit application
  Mikko logs in
  Open application  submit-app  753-416-25-30
  Open tab  requiredFieldSummary
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']

Submit date is not be visible
  Element should not be visible  xpath=//span[@data-test-id='application-submitted-date']

Mikko submits application
  Submit application

Mikko cant re-submit application
  Wait Until  Element should not be visible  xpath=//*[@data-test-id='application-open-requiredFieldSummary-tab']
  Wait Until  Element should not be visible  xpath=//*[@data-test-id='application-submit-btn']

Submit date should be visible
  Wait until  Element should be visible  xpath=//span[@data-test-id='application-submitted-date']

