*** Settings ***

Documentation   Authority admin creates users
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Authority admin goes to the authority admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Authority admin creates two users
  Set Suite Variable  ${userRowXpath}  //div[contains(@class, 'admin-users-table')]//table/tbody/tr
  Wait Until  Element Should Be Visible  ${userRowXpath}
  ${userCount} =  Get Matching Xpath Count  ${userRowXpath}
  Create user  heikki.virtanen@example.com  Heikki  Virtanen  Lukuoikeus
  Create user  hessu.kesa@example.com  Hessu  Kesa  Muutosoikeus
  ${userCountAfter} =  Evaluate  ${userCount} + 2
  User count is  ${userCountAfter}

Authority admin removes Heikki
  ${userCount} =  Get Matching Xpath Count  ${userRowXpath}
  Element should be visible  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='heikki.virtanen@example.com']//button[@data-op='removeFromOrg']
  Click element  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='heikki.virtanen@example.com']//button[@data-op='removeFromOrg']
  Confirm  dynamic-yes-no-confirm-dialog
  ${userCountAfter} =  Evaluate  ${userCount} - 1
  User count is  ${userCountAfter}
  Page should not contain  heikki.virtanen@example.com
  Logout

Hessu activates account via email
  Open last email
  Page Should Contain  hessu.kesa@example.com
  ## First link
  Click link  xpath=//a
  Fill in new password  setpw  hessu123

Hessu can login
  User logs in  hessu.kesa@example.com  hessu123  Hessu Kesa
  [Teardown]  Logout


*** Keywords ***

User count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  ${userRowXpath}  ${amount}

Statement person count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  ${statementGiverRowXpath}  ${amount}

Create user
  [Arguments]  ${email}  ${firstName}  ${lastName}  ${role}
  ${authority} =   Set Variable  xpath=//div[@id="add-user-to-organization-dialog"]//input[@value="authority"]
  ${reader} =   Set Variable  xpath=//div[@id="add-user-to-organization-dialog"]//input[@value="reader"]

  Click enabled by test id  authadmin-add-authority
  Wait until  Element should be visible  //label[@for='auth-admin.admins.add.email']
  # By default, only authority is selected
  Checkbox Should Be Selected  ${authority}
  Checkbox Should Not Be Selected  ${reader}

  Input text  auth-admin.admins.add.email  ${email}
  Input text  auth-admin.admins.add.firstName  ${firstName}
  Input text  auth-admin.admins.add.lastName  ${lastName}

  Run Keyword If      '${role}' == 'Muutosoikeus'  Select Checkbox  ${authority}
  Run Keyword Unless  '${role}' == 'Muutosoikeus'  Unselect Checkbox  ${authority}
  Run Keyword If      '${role}' == 'Lukuoikeus'  Select Checkbox  ${reader}
  Run Keyword Unless  '${role}' == 'Lukuoikeus'  Unselect Checkbox  ${reader}

  Click enabled by test id  authadmin-add-authority-continue
  Wait test id visible  authadmin-add-authority-ok
  Click enabled by test id  authadmin-add-authority-ok
  Wait Until  Element Should Not Be Visible  add-user-to-organization-dialog
  Wait Until  Page Should Contain  ${email}
  Element Should Contain  //div[contains(@class, 'users-table')]//table/tbody/tr[@data-user-email="${email}"]/td[3]  ${role}

