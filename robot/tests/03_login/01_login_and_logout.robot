*** Settings ***

Documentation   Login & Logout
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Variables ***

${LOGIN}        mikko@example.com
${PASSWORD}     mikko123
${USERNAME}     Mikko Intonen

*** Test Cases ***


Login fails with invalid password
  [Tags]  ie8
  Login fails  ${LOGIN}  invalid

Login fails with invalid username
  [Tags]  ie8
  Login fails  invalid  ${PASSWORD}

Login fails with invalid username and password
  [Tags]  ie8
  Login fails  invalid  whatever

Username is locked after 3 failures
  [Tags]  ie8
  Login fails  invalid  whatever
  Wait Until  Page Should Contain  Tunnus tai salasana on väärin
  Login fails  invalid  whatever
  Wait Until  Page Should Contain  Tunnus on lukittu
  
Username lock expires after 10 seconds
  [Tags]  ie8
  Sleep  10
  Login fails  invalid  whatever
  Page Should Not Contain  Tunnus on lukittu

Login fails with empty username
  [Tags]  ie8
  Login fails  ${EMPTY}  ${PASSWORD}

Login fails with empty password
  [Tags]  ie8
  Login fails  ${LOGIN}  ${EMPTY}

Login fails with empty username and password
  [Tags]  ie8
  Login fails  ${EMPTY}  ${EMPTY}

Mikko logs in
  [Tags]  ie8
  Login  ${LOGIN}  ${PASSWORD}
  User should be logged in  ${USERNAME}
  User role should be  applicant
  Applications page should be open
  Number of visible applications  0

Mikko thinks he's Swedish
  [Tags]  ie8
  Page Should Not Contain  Suomeksi
  Click link  xpath=//*[@data-test-id='lang-sv']
  Wait Until  Page Should Contain  Suomeksi

Mikko remembers he's Finnish
  [Tags]  ie8
  Page Should Not Contain  På svenska
  Click link  xpath=//*[@data-test-id='lang-fi']
  Wait Until  Page Should Contain  På svenska
  [Teardown]  logout

Mikko is logged out
  [Tags]  ie8
  User should not be logged in

Mikko logs in via iframe
  Go to  ${SERVER}/html/pages/login-iframe.html
  Select Frame  loginframe
  Element Should Not Be Visible  xpath=//nav
  Login  ${LOGIN}  ${PASSWORD}
  User should be logged in  ${USERNAME}
  Applications page should be open
  Number of visible applications  0
  [Teardown]  logout

Mikko logs in with username that has capital letters
  [Tags]  ie8
  Login  Mikko@Example.COM  ${PASSWORD}
  User should be logged in  ${USERNAME}
  [Teardown]  logout

Solita Admin can log in
  [Tags]  ie8
  SolitaAdmin logs in
  [Teardown]  logout
