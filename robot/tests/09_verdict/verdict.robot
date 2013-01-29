*** Settings ***

Documentation   Application gets verdict
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko opens application to see verdict
  Mikko logs in
  Open the application

Application does not have verdict
  Open verdict tab
  Element should not be visible  test-application-verdict
  ${ID} =  Get Element Attribute  xpath=//*[@data-bind-test-id='application-id']@data-test-value
  Set suite variable  ${APPLICATION ID}  ${ID}
  Logout

Solita Admin can log in and gives verdict
  SolitaAdmin logs in
  Wait until page contains element  admin-header
  Log  ${APPLICATION ID}
  Wait until  page should contain link  ${APPLICATION ID}
  Click link  ${APPLICATION ID}
  Logout

Mikko opens application
  Mikko logs in
  Open the application

Application verdict is visible to applicant
  Open verdict tab
  Wait Until  Element should be visible  test-application-verdict
  Logout

Sonja opens application
  Sonja logs in
  Open the application

Application verdict is visible to authority
  Open verdict tab
  Wait Until  Element should be visible  test-application-verdict

*** Keywords ***

Open verdict tab
  Click by test id  application-open-verdict-tab
