*** Settings ***

Documentation  Authority admin creates users
Suite setup     Sipoo logs in
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Authority admin goes to admin page
  Wait until page contains element  test-authority-admin-users-table

Authority admin creates three users
  ${userCount} =  Get Matching Xpath Count  //tr[@class="user-row"]
  Create user  heikki.virtanen@example.com  Heikki  Virtanen  123456
  Create user  minni.janatuinen@example.com  Minni  Janatuinen  123456
  Create user  hessu.kesa@example.com  Hessu  Kesa  123456
  Wait Until  Element Should Be Visible  test-authority-admin-users-table
  ${userCountAfter} =  Evaluate  ${userCount} + 3
  User count is  ${userCountAfter}

Hessu Kesa can login
  Logout
  Authority logs in  hessu.kesa@example.com  123456  Hessu Kesa

Hessu Kesa can logout
  Logout

*** Keywords ***

User count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //tr[@class="user-row"]  ${amount}

Create user
  [Arguments]  ${email}  ${firstName}  ${lastName}  ${password}
  Click element  test-create-user
  Wait until  Element should be visible  user-email
  Input text       user-email  ${email}
  Input text       user-firstname  ${firstName}
  Input text       user-lastname  ${lastName}
  Input text       user-password  ${password}
  Click element    test-create-user-save
  Wait Until  Element Should Not Be Visible  dialog-add-user
