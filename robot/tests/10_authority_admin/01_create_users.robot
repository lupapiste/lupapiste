*** Settings ***

Documentation   Authority admin creates users
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Authority admin goes to the authority admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Authority admin creates two users
  Set Suite Variable  ${userRowXpath}  //div[@class='admin-users-table']//table/tbody/tr
  Wait Until  Element Should Be Visible  ${userRowXpath}
  ${userCount} =  Get Matching Xpath Count  ${userRowXpath}
  Create user  heikki.virtanen@example.com  Heikki  Virtanen
  Create user  hessu.kesa@example.com  Hessu  Kesa
  ${userCountAfter} =  Evaluate  ${userCount} + 2
  User count is  ${userCountAfter}
  Logout

Created user can login
  [Tags]  fail
  Login  heikki.virtanen@example.com  12345678
  User should be logged in  Heikki Virtanen
  Logout

Activation link is not visible, because new authority user is actived by default
  [Tags]  fail
  Wait until  page should not contain link  heikki.virtanen@example.com
  Logout

Hessu can login, too
  [Tags]  fail
  Login  hessu.kesa@example.com  12345678
  User should be logged in  Hessu Kesa
  Logout

*** Keywords ***

User count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  ${userRowXpath}  ${amount}

Create user
  [Arguments]  ${email}  ${firstName}  ${lastName}
  Click enabled by test id  authadmin-add-authority
  Wait until  Element should be visible  //label[@for='auth-admin.admins.add.email']
  Input text  auth-admin.admins.add.email  ${email}
  Input text  auth-admin.admins.add.firstName  ${firstName}
  Input text  auth-admin.admins.add.lastName  ${lastName}
  Click enabled by test id  authadmin-add-authority-continue
  Click enabled by test id  authadmin-add-authority-ok
  Wait Until  Element Should Not Be Visible  add-user-to-organization-dialog
  Wait Until  Page Should Contain  ${email}
