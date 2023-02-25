*** Settings ***

Documentation   Login & Logout
Resource        ../../common_resource.robot

*** Variables ***

${LOGIN}        mikko@example.com
${PASSWORD}     mikko123
${USERNAME}     Mikko Intonen

*** Test Cases ***


Remember me checkbox should not be selected
  Wait Until  Element Should Be Visible  rememberme-label
  Checkbox should not be selected  rememberme

Login fails with invalid password
  [Tags]  ie8
  Login fails  ${LOGIN}  invalid

Login fails with invalid username
  Login fails  invalid  ${PASSWORD}

Login fails with invalid username and password
  Login fails  invalid  whatever

Username is locked after 3 failures
  Login fails  invalid  whatever
  Wait Until  Element should contain  login-message  Tunnus tai salasana on väärin
  Login fails  invalid  whatever
  Wait Until  Element should contain  login-message  Tunnus on lukittu

Username lock expires after 10 seconds
  Sleep  10
  Login fails  invalid  whatever
  Page Should Not Contain  Tunnus on lukittu

Login fails with empty username
  Login fails  ${EMPTY}  ${PASSWORD}

Login fails with empty password
  Login fails  ${LOGIN}  ${EMPTY}

Login fails with empty username and password
  Login fails  ${EMPTY}  ${EMPTY}

Mikko logs in and wants us to remember him
  [Tags]  ie8
  Wait Until  Element Should Be Visible  rememberme-label
  Checkbox should not be selected  rememberme
  Click label  rememberme
  Login  ${LOGIN}  ${PASSWORD}
  Run Keyword And Ignore Error  Handle alert
  User should be logged in  ${USERNAME}
  User role should be  applicant
  Applications page should be open
  Number of visible applications  0

Mikko sees the help text if there are no applications
  [Tags]  ie8
  Wait until  Element should be visible  //section[@id='applications']//*[@data-test-id='applications-no-application']

Mikko thinks he's Swedish
  [Tags]  ie8
  Language To  SV

Mikko remembers he's Finnish
  [Tags]  ie8
  Language To  FI
  [Teardown]  logout

Mikko is logged out but remembered
  [Tags]  ie8
  Wait Until  Element Should Be Visible  rememberme-label
  User should not be logged in
  Checkbox should be selected  rememberme
  Textfield Value Should Be  login-username  mikko@example.com

Mikko logs in with username that has capital letters and wants to be forgotten
  [Tags]  ie8
  Click label  rememberme
  Login  Mikko@Example.COM  ${PASSWORD}
  User should be logged in  ${USERNAME}
  Logout
  User should not be logged in
  Wait Until  Element Should Be Visible  rememberme-label
  Checkbox should not be selected  rememberme

Trying to open my page without logging in opens a help page
  [Tags]  ie8
  Go to login page
  Delete Cookie  ring-session
  Go to  ${SERVER}/app/fi/applicant#!/mypage
  Wait Until  Element Should Be Visible  hashbang

My page opens after login
  [Tags]  ie8
  Element should be visible by test id  login
  Click by test id  login
  Wait Until  Element Should Be Visible  rememberme-label
  Login  mikko@example.com  ${PASSWORD}
  Wait Until  Element Should Be Visible  mypage
  User should be logged in  ${USERNAME}
  [Teardown]  logout

Cleanup cookies
  [Tags]  ie8
  Delete Cookie  ring-session
  Delete Cookie  my-email

Authority (Veikko) logs in and dont see the help text for no applications
  Veikko logs in
  Element should be visible by test id  applications-create-new-application
  Element should not be visible  //section[@id='applications']//*[@data-test-id='applications-no-application']
  [Teardown]  logout
