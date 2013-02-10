*** Settings ***

Documentation   User changes account details
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

## For some strange reason, firstName and lastName fields are left blank.

Mikko changes his name
  Mikko logs in
  Click Element  user-name
  Change name  Mikko  Intonen  Mika  Intola

Name should have changed in Swedish page too
  Click link  På svenska
  User should be logged in  Mika Intola

Mika changes the name back to Mikko Intonen
  Change name  Mika  Intola  Mikko  Intonen

Name should have changed in Finnish page too
  Click link  Suomeksi
  User should be logged in  Mikko Intonen

*** Keywords ***

Change name
  [Arguments]  ${oldFN}  ${oldLN}  ${newFN}  ${newLN}
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  firstName  ${oldFN}
  Wait Until  Textfield Value Should Be  lastName   ${oldLN}
  Input Text  firstName  ${newFN}
  Input Text  lastName  ${newLN}
  Focus  street
  # Sanity checks
  Textfield Value Should Be  firstName  ${newFN}
  Textfield Value Should Be  lastName  ${newLN}
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']
  User should be logged in  ${newFN} ${newLN}
