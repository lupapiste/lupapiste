*** Settings ***

Documentation   User changes account details
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko changes his name
  Mikko logs in
  Click Element  user-name
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  firstName  Mikko
  Wait Until  Textfield Value Should Be  lastName  Intonen
  Input Text  firstName  Mika
  Input Text  lastName  Intola
  Focus  street
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']
  User should be logged in  Mika Intola

Name should have changed in Swedish page too
  Click link  På svenska
  User should be logged in  Mika Intola

Mika changes the name back to Mikko Intonen
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Wait Until  Textfield Value Should Be  firstName  Mika
  Wait Until  Textfield Value Should Be  lastName  Intola
  Input Text  firstName  Mikko
  Input Text  lastName  Intonen
  Focus  street
  Click enabled by test id  save-my-userinfo
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo-ok']
  User should be logged in  Mikko Intonen

Name should have changed in Finnish page too
  Click link  Suomeksi
  User should be logged in  Mikko Intonen
