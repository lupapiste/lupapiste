 *** Settings ***

Documentation   User changes password
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko changes password
  Mikko logs in
  Change password  mikko123  lamepassword
  Logout

Mikko logs in with the old password
  Login fails  mikko@example.com  mikko123

Mikko logs in with the new password
  Login  mikko@example.com  lamepassword
  User should be logged in  Mikko Intonen
  Applications page should be open

Mikko changes the password back
  Change password  lamepassword  mikko123
  Logout
  Mikko logs in

*** Keywords ***

Change password
  [Arguments]  ${old}  ${new}
  Open My Page
  Open accordion by test id  mypage-change-password-accordion
  Wait Until  Element Should be visible  //*[@data-test-id='change-my-password']
  Input Text  oldPassword  ${old}
  Input Text  newPassword  ${new}
  Input Text  newPassword2  ${new}
  Click enabled by test id  change-my-password
  Wait until  Page should contain  Tallennettu
