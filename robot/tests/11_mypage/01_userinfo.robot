*** Settings ***

Documentation   User changes account details
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko changes hes name
  Mikko logs in
  Click Element  user-name
  Wait Until  Element Should be visible  //*[@data-test-id='save-my-userinfo']
  # WIP...

*** Keywords ***
