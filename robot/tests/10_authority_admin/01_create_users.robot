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
  Create user  hessu.kesa@example.com  Hessu  Kesa  123456
  Wait Until  Element Should Be Visible  test-authority-admin-users-table
  ${userCountAfter} =  Evaluate  ${userCount} + 2
  User count is  ${userCountAfter}
  Logout

Created user cant login
  Login  heikki.virtanen@example.com  123456
  User should not be logged in

Admin activates Heikki (to simulate activating via email)
  As Solitaadmin
  Wait until  page should contain link  heikki.virtanen@example.com
  Click link  heikki.virtanen@example.com

Activating Heikki removes activation link
  Wait until  page should not contain link  heikki.virtanen@example.com
  Logout

Heikki user can now login
  Authority logs in  heikki.virtanen@example.com  123456  Heikki Virtanen
  Logout

Hessu can't still login
  Login  hessu.kesa@example.com  123456
  User should not be logged in

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
