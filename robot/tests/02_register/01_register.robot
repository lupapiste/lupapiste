*** Settings ***

Documentation   Mikko registers
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/vetuma_helpers.robot

*** Test Cases ***

Setup random email
  [Tags]  integration  ie8
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${email}  ${secs}@example.com

Cancelling vetuma return back to register page
  [Tags]  integration  ie8
  Go to register page
  Register button is visible
  Cancel via vetuma
  Wait until page contains element  register-cancel

VTJ-data should be populated from Osuuspankki
  [Tags]  integration  ie8
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via Osuuspankki via Vetuma  vetuma-init
  Wait until  Submit is disabled
  Textfield should contain  xpath=//input[@data-test-id='register-street']  Sepänkatu 11 A 5
  Textfield should contain  xpath=//input[@data-test-id='register-zip']  70100
  Textfield should contain  xpath=//input[@data-test-id='register-city']  KUOPIO

Filling register form2
  [Tags]  integration  ie8
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via Nordea via Vetuma
  Wait until page contains element  xpath=//input[@data-test-id='register-personid']
  Submit is disabled

  Input text by test id  register-street  Rambokuja 6
  Submit is disabled

  Input text by test id  register-zip  33800
  Submit is disabled

  Input text by test id  register-city  sipoo
  Submit is disabled

  Input text by test id  register-phone  +358554433221
  Submit is disabled

  Input text by test id  register-email  ${email}
  Submit is disabled

  Input text by test id  register-confirmEmail  ${email}
  Submit is disabled

  Input text by test id  register-password  vetuma69
  Submit is disabled

  Input text by test id  register-confirmPassword  vetuma68
  Submit is disabled

  Input text by test id  register-confirmPassword  vetuma69
  Submit is disabled

  Checkbox Should Not Be Selected  registerAllowDirectMarketing
  Select Checkbox  registerAllowDirectMarketing
  Checkbox Should Be Selected  registerAllowDirectMarketing

  Checkbox Should Not Be Selected  registerAcceptTerms
  Select Checkbox  registerAcceptTerms
  Checkbox Should Be Selected  registerAcceptTerms
  Click enabled by test id  register-submit

Submitting form gives confirmation
  [Tags]  integration  ie8
  Wait until  element should be visible  xpath=//*[@data-bind="ltext: 'register.activation-email-info1'"]
  Element text should be  activation-email  ${email}

Can not login before activation
  [Tags]  integration  ie8
  Go to login page
  Login fails  ${email}  vetuma69

Vetuma-guy activates his account
  [Tags]  integration  ie8
  Open last email
  Wait Until  Page Should Contain  ${email}
  Page Should Contain  /app/security/activate
  ## Click the first link
  Click link  xpath=//a

Vetuma-guy lands to empty applications page automaticly
  [Tags]  integration  ie8
  User should be logged in  Nordea Demo
  Confirm notification dialog
  Applications page should be open
  Number of visible applications  0

*** Keywords ***

Register button is visible
  Wait until page contains element  vetuma-init

Go to register page
  Focus  register-button
  Click Button    register-button

Submit is disabled
  ${path} =   Set Variable  xpath=//button[@data-test-id='register-submit']
  Wait Until  Element Should Be Disabled  ${path}
