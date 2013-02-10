*** Settings ***

Documentation   User changes account details
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko changes hes name
  Mikko logs in
  Click Element  user-name
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  Input Text  firstName  Mika
  Input Text  lastName  Intola
  Click enabled by test id  save-my-userinfo
  User should be logged in  Mika Intola

Name should have changed in Swedish page too
  Click link  På svenska
  User should be logged in  Mika Intola

*** Keywords ***
