*** Settings ***

Documentation   User changes account details
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

## For some strange reason, firstName and lastName fields are left blank.

Mikko changes his name and experience
  Mikko logs in
  Click Element  user-name
  Wait for Page to Load  Mikko  Intonen
  Change Textfield Value  firstName  Mikko  Mika
  Change Textfield Value  lastName  Intonen  Intola
  Change Textfield Value  architect.degree  Tutkinto  Arkkitehti
  Change Textfield Value  architect.graduatingYear  2000  2001
  Change Textfield Value  architect.fise  f  fise
  Save User Data
  User should be logged in  Mika Intola

Name should have changed in Swedish page too
  Click link  xpath=//*[@data-test-id='lang-sv']
  Wait for Page to Load  Mika  Intola
  User should be logged in  Mika Intola

Experience should have changed in Swedish page to
  Wait Until  Textfield Value Should Be  architect.fise  fise
  Textfield Value Should Be  architect.degree  Arkkitehti
  Textfield Value Should Be  architect.graduatingYear  2001
  Textfield Value Should Be  architect.fise  fise

Mika changes the name and experice back
  Change Textfield Value  firstName  Mika  Mikko
  Change Textfield Value  lastName  Intola  Intonen
  Change Textfield Value  architect.degree  Arkkitehti  Tutkinto
  Change Textfield Value  architect.graduatingYear  2001  2000
  Change Textfield Value  architect.fise  fise  f
  Save User Data

Name should have changed in Finnish page too
  Click link  xpath=//*[@data-test-id='lang-fi']
  Wait for Page to Load  Mikko  Intonen
  User should be logged in  Mikko Intonen

Experience should have changed in Finnish back to original
  Wait Until  Textfield Value Should Be  architect.fise  f
  Textfield Value Should Be  architect.degree  Tutkinto
  Textfield Value Should Be  architect.graduatingYear  2000
  Textfield Value Should Be  architect.fise  f

*** Keywords ***

Save User Data
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']

Wait for Page to Load
  [Arguments]  ${firstName}  ${lastName}
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  firstName  ${firstName}
  Wait Until  Textfield Value Should Be  lastName   ${lastName}

Change Textfield Value
  [Arguments]  ${field}  ${old}  ${new}
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  ${field}  ${old}
  Input Text  ${field}  ${new}
  Textfield Value Should Be  ${field}  ${new}
  Focus  street
