*** Settings ***

Documentation   Login
Resource        ../../common_resource.robot

*** Test Cases ***

Login via WordPress site
  [Tags]  integration  ie8
  Go to  ${SERVER}
  Run Keyword Unless  '${SERVER}'=='http://localhost:8000'  Wait Until  Page should contain  luvan?
  Login  pena  pena
  User should be logged in  Pena Panaani
  Logout
