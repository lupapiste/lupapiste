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
  Create user  heikki.virtanen@example.com  Heikki  Virtanen  lukuoikeus
  Create user  hessu.kesa@example.com  Hessu  Kesa  Viranomainen
  ${userCountAfter} =  Evaluate  ${userCount} + 2
  User count is  ${userCountAfter}

Authority admin removes Heikki
  ${userCount} =  Get Matching Xpath Count  ${userRowXpath}
  Element should be visible  xpath=//div[@class='admin-users-table']//tr[@data-user-email='heikki.virtanen@example.com']//a[@data-op='removeFromOrg']
  Click element  xpath=//div[@class='admin-users-table']//tr[@data-user-email='heikki.virtanen@example.com']//a[@data-op='removeFromOrg']
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

## TODO muutetaan testi sellaiseksi etta lisataan henkilo joka ei ole organsisaatiossa
## (feature/statementGiver)
Authority admin adds existing authority as a statement person
  Sipoo logs in
  Set Suite Variable  ${statementGiverRowXpath}  //tr[@class='statement-giver-row']
  ${userCount} =  Get Matching Xpath Count  ${statementGiverRowXpath}
  Create statement person  ronja.sibbo@sipoo.fi  Asiantuntija
  ${userCountAfter} =  Evaluate  ${userCount} + 1
  Statement person count is  ${userCountAfter}
  Wait Until  Page should contain  Asiantuntija

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

  Run Keyword If      '${role}' == 'Viranomainen'  Select Checkbox  ${authority}
  Run Keyword Unless  '${role}' == 'Viranomainen'  Unselect Checkbox  ${authority}
  Run Keyword If      '${role}' == 'lukuoikeus'  Select Checkbox  ${reader}
  Run Keyword Unless  '${role}' == 'lukuoikeus'  Unselect Checkbox  ${reader}

  Click enabled by test id  authadmin-add-authority-continue
  Click enabled by test id  authadmin-add-authority-ok
  Wait Until  Element Should Not Be Visible  add-user-to-organization-dialog
  Wait Until  Page Should Contain  ${email}
  Element Should Contain  //div[@class="users-table"]//table/tbody/tr[@data-user-email="${email}"]/td[3]  ${role}

Create statement person
  [Arguments]  ${email}  ${text}
  Click enabled by test id  create-statement-giver
  Wait until  Element should be visible  //label[@for='statement-giver-email']
  Input text  statement-giver-email  ${email}
  Input text  statement-giver-email2  ${email}
  Input text  statement-giver-text  ${text}
  Click enabled by test id  create-statement-giver-save
