*** Settings ***

Documentation   Application gets verdict
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko opens creates application
  Mikko logs in
  Create application  verdict-app  753  75341600250030

Application does not have verdict
  Open tab  verdict
  Element should not be visible  application-verdict
  ${ID} =  Get Element Attribute  xpath=//*[@data-test-id='application-id']@data-test-value
  Set suite variable  ${APPLICATION ID}  ${ID}

Mikko submits application
  Click by test id  application-submit-btn
  Wait until  Application state should be  submitted
  
Mikko goes for lunch
  Logout

Solita Admin can log in and gives verdict
  SolitaAdmin logs in
  Wait until  Element should be visible  admin-header
  Wait until  Element should be visible  xpath=//a[@data-test-id='${APPLICATION ID}']
  Click element  xpath=//a[@data-test-id='${APPLICATION ID}']
  Wait until  Element should contain  xpath=//*[@data-test-id='admin-verdict-result']  OK
  Logout

Mikko sees that the application has verdict
  Mikko logs in
  Open application  verdict-app
  Open tab  verdict 
  Element should contain  xpath=//span[@data-test-id='application-verdict']  onneksi olkoon!
  Logout

Application verdict is visible to authority
  Sonja logs in
  Open application  verdict-app
  Open tab  verdict
  Element should be visible  xpath=//span[@data-test-id='application-verdict']
  Logout
