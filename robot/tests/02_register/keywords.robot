*** Keywords ***

Register button is visible
  Wait until page contains element  vetuma-init

Go to register page
  Focus  register-button
  Click Button    register-button

Submit is disabled
  ${path} =   Set Variable  xpath=//button[@data-test-id='register-submit']
  Wait Until  Element Should Be Disabled  ${path}

Fill registration
  [Arguments]  ${street}  ${zip}  ${city}  ${phone}  ${mail}  ${password}  ${lang}=fi
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via dummy page  vetuma-init
  Wait until page contains element  xpath=//input[@data-test-id='register-personid']
  Submit is disabled

  Input text by test id  register-street  ${street}
  Submit is disabled

  Input text by test id  register-zip  ${zip}
  Submit is disabled

  Input text by test id  register-city  ${city}
  Submit is disabled

  Input text by test id  register-phone  ${phone}
  Submit is disabled

  Select from test id  register-language  ${lang}
  Submit is disabled

  Input text by test id  register-email  ${mail}
  Submit is disabled

  Input text by test id  register-confirmEmail  ${mail}
  Submit is disabled

  Input text by test id  register-password  ${password}
  Submit is disabled

  Input text by test id  register-confirmPassword  wrong
  Submit is disabled

  Input text by test id  register-confirmPassword  ${password}
  Submit is disabled

  Checkbox Should Not Be Selected  registerAllowDirectMarketing
  Select Checkbox  registerAllowDirectMarketing
  Checkbox Should Be Selected  registerAllowDirectMarketing

  Checkbox Should Not Be Selected  registerAcceptTerms
  Select Checkbox  registerAcceptTerms
  Checkbox Should Be Selected  registerAcceptTerms
  Click enabled by test id  register-submit
  Wait until  element should be visible  xpath=//*[@data-bind="ltext: 'register.activation-email-info1'"]
  Element text should be  activation-email  ${mail}

Activate account
  [Arguments]  ${mail}
  Open last email
  Wait Until  Page Should Contain  ${mail}
  Page Should Contain  /app/security/activate
  Click link  xpath=//a
  User should be logged in  Teemu Testaaja
  Confirm notification dialog
  Applications page should be open
  Number of visible applications  0
