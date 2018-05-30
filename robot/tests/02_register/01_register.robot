*** Settings ***

Documentation   Mikko registers
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/ident_helpers.robot
Resource        keywords.robot

*** Test Cases ***

Setup random email
  [Tags]  integration  ie8
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${email}  ${secs}@example.com

Cancelling identification causes return back to register page
  [Tags]  integration  ie8
  Go to register page
  Register button is visible
  Click by test id  vetuma-init
  Click by test id  cancel-button
  Wait until page contains element  register-cancel

VTJ-data for Teemu should be populated from identification
  [Tags]  integration  ie8
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via dummy page  vetuma-init
  Wait until  Submit is disabled
  Textfield should contain  xpath=//input[@data-test-id='register-street']  Testikatu 23
  Textfield should contain  xpath=//input[@data-test-id='register-zip']  90909
  Textfield should contain  xpath=//input[@data-test-id='register-city']  Testikylä

Filling register form2
  [Tags]  integration  ie8
  Fill registration  Rambokuja 7  33800  sipoo  +358554433221  ${email}  vetuma69

Can not login before activation
  [Tags]  integration  ie8
  Go to login page
  Login fails  ${email}  vetuma69

Teemu activates his account
  [Tags]  integration  ie8
  Activate account  ${email}
  [Teardown]  Logout

Benny registers and sets the user language to Swedish
  [Tags]  integration  ie8
  Fill registration  Benny Lane  12345  sipoo  +358500400  Benny@example.com  benny123  sv

Benny's mail link refers to the Swedish applications page
  [Tags]  integration  ie8
  Activate account  benny@example.com
  Language is  SV
  [Teardown]  Logout
