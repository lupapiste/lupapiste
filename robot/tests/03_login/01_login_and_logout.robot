*** Settings ***

Documentation   Login & Logout
Resource        ../../common_resource.robot

*** Variables ***

${LOGIN}        mikko@example.com
${PASSWORD}     mikko123
${USERNAME}     Mikko Intonen

*** Test Cases ***

Login fails with invalid username
  [Tags]  ie8
  Login fails  invalid  ${PASSWORD}

Login fails with invalid password
  Login fails  ${LOGIN}  invalid

Login fails with invalid username and password
  Login fails  invalid  whatever

Login fails with empty username
  Login fails  ${EMPTY}  ${PASSWORD}

Login fails with empty password
  Login fails  ${LOGIN}  ${EMPTY}

Login fails with empty username and password
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
  Page Should Not Contain  Suomeksi >>
  Click link  xpath=//*[@data-test-id='lang-sv']
  Wait Until  Page Should Contain  Suomeksi >>

Mikko remembers he's Finnish
  [Tags]  ie8
  Page Should Not Contain  På svenska >>
  Click link  xpath=//*[@data-test-id='lang-fi']
  Wait Until  Page Should Contain  På svenska >>

Mikko logs out
  [Tags]  ie8
  Logout
  User should not be logged in

Mikko logs in via iframe
  Go to  ${SERVER}/html/pages/login-iframe.html
  Select Frame  loginframe
  Login  ${LOGIN}  ${PASSWORD}
  User should be logged in  ${USERNAME}
  Applications page should be open
  Number of visible applications  0
  Logout

Mikko logs in with username that has capital letters
  [Tags]  ie8
  Login  Mikko@Example.COM  ${PASSWORD}
  User should be logged in  ${USERNAME}
  Logout

Solita Admin can log in
  [Tags]  ie8
  SolitaAdmin logs in
  Logout  
