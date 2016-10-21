*** Settings ***

Documentation   Identity federation
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Setup random email
  [Tags]  integration  ie8
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${email}  ${secs}@example.com

Send mock identity to server
  [Tags]  integration  ie8
  Go to  ${SERVER}/dev-pages/idf.html
  Execute Javascript  $("input[name='email']").val("${email}").change();
  Wait until  Page should contain  READY
  Click element  submit

Got email
  [Tags]  integration  ie8
  Open last email
  Wait Until  Page Should Contain  ${email}
  Page Should Contain  /app/fi/welcome#!/link-account
  ## Click the first link
  Click link  xpath=//a
  Register button is visible

Federated user activates account
  [Tags]  integration  ie8
  Authenticate via dummy page
  Wait until  Submit is disabled
  Wait until  Textfield should contain  xpath=//input[@data-test-id='link-account-street']  Testikatu 23
  Textfield should contain  xpath=//input[@data-test-id='link-account-street']  Testikatu 23
  Textfield should contain  xpath=//input[@data-test-id='link-account-zip']  90909
  Textfield should contain  xpath=//input[@data-test-id='link-account-city']  Testikylä

  Input text by test id  link-account-password  vetuma69
  Submit is disabled

  Input text by test id  link-account-confirmPassword  vetuma68
  Submit is disabled

  Input text by test id  link-account-confirmPassword  vetuma69
  Submit is disabled

  Checkbox Should Not Be Selected  linkAccountAllowDirectMarketing
  Select Checkbox  linkAccountAllowDirectMarketing
  Checkbox Should Be Selected  linkAccountAllowDirectMarketing

  Checkbox Should Not Be Selected  linkAccountAcceptTerms
  Select Checkbox  linkAccountAcceptTerms
  Checkbox Should Be Selected  linkAccountAcceptTerms
  Click enabled by test id  link-account-submit

Federated user lands to empty applications page
  [Tags]  integration  ie8
  User should be logged in  Teemu Testaaja
  Confirm notification dialog
  Applications page should be open
  Number of visible applications  0


*** Keywords ***

Authenticate via dummy page
  Wait Until  Element should be visible  vetuma-linking-init
  Wait Until  Click element  vetuma-linking-init
  Wait test id visible  submit-button
  Click by test id  submit-button

Submit is disabled
  ${path} =   Set Variable  xpath=//button[@data-test-id='link-account-submit']
  Wait Until  Element Should Be Disabled  ${path}

Register button is visible
  Wait until page contains element  vetuma-linking-init
