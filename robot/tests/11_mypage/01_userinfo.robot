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
  
Mikko changes his experience 
  Change Textfield Value  architect.degree  Tutkinto  Arkkitehti
  Change Textfield Value  architect.graduatingYear  2000  2001
  Change Textfield Value  architect.fise  f  fise
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']

Name should have changed in Swedish page too
  Click link  xpath=//*[@data-test-id='lang-sv']
  User should be logged in  Mika Intola

Mika changes the name back to Mikko Intonen
  Change name  Mika  Intola  Mikko  Intonen
  
Mikko changes his experience back
  Change Textfield Value  architect.degree  Arkkitehti  Tutkinto
  Change Textfield Value  architect.graduatingYear  2001  2000
  Change Textfield Value  architect.fise  fise  f
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']

Name should have changed in Finnish page too
  Click link  xpath=//*[@data-test-id='lang-fi']
  User should be logged in  Mikko Intonen

Experience should have changed in Finnish back to original
  Textfield Value Should Be  architect.degree  Tutkinto
  Textfield Value Should Be  architect.graduatingYear  2000
  Textfield Value Should Be  architect.fise  f

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

Change Textfield Value
  [Arguments]  ${field}  ${old}  ${new}
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  ${field}  ${old}
  Input Text  ${field}  ${new}
  Textfield Value Should Be  ${field}  ${new}
  Focus  street
