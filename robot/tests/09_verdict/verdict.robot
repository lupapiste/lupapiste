*** Settings ***

Documentation   Application gets verdict
Suite teardown  Logout
Resource        ../../common_resource.txt

*** Test Cases ***

Application does not have verdict
  Mikko logs in
  Wait until page contains element  test-application-link
  Click element  test-application-link
  Wait until page contains element  application-page-is-ready
  Click element  test-verdict-tab
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

Application verdict is visible to applicant
  Mikko logs in
  Click element  test-application-link
  Wait and click  test-verdict-tab
  Wait until  Element should be visible  test-application-verdict
  Logout
  
Application verdict is visible to authority
  Sonja logs in
  Click element  test-application-link
  Wait until page contains element  application-page-is-ready
  Click element  test-verdict-tab
  Wait until  Element should be visible  test-application-verdict