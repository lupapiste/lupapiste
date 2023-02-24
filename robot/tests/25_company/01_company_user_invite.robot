*** Settings ***

Documentation   Users are added to company
Suite Setup     Apply minimal fixture now
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot
Resource        ../../common_keywords/ident_helpers.robot
Resource        ../02_register/keywords.robot

*** Test Cases ***

Company admin logs in
  Kaino logs in

Kaino sees the help text if there are no applications
  Wait until  Element should be visible  //section[@id='applications']//*[@data-test-id='applications-no-application']

Kaino goes to company page
  Open company user listing

Check Kaino's status
  Check company user  0  kaino@solita.fi  Solita  Kaino  Ylläpitäjä  Kyllä
  No such test id  company-user-delete-0

Invite Duff3
  Invite existing dummy user  dummy3@example.com  Duff3  Dummy3

Check Duff3 invitation status
  Check invitation  0  dummy3@example.com  Dummy3  Duff3  Käyttäjä  Kyllä

Duff3 cannot be invited again
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Test id disabled  company-search-email
  Input text by test id  company-new-user-email  dummy3@example.com
  Click enabled by test id  company-search-email
  Wait until  Element should be visible  //div[@id="dialog-company-new-user"]//span[@data-bind="ltext: 'register.company.add-user.already-invited'"]
  Click enabled by test id  company-user-already-invited-close-dialog

Duff3 user gets invite email
  Accept invitation  dummy3@example.com

Account is linked
  Wait Until  Page Should Contain  Tilisi on liitetty onnistuneesti yrityksen tiliin.
  Click link  Siirry Lupapiste-palveluun

Duff3 user gets password reset email
  Open last email
  Wait Until  Page Should Contain  dummy3@example.com
  Page Should Contain  /app/fi/welcome#!/setpw/

Admin logs in again
  Kaino logs in
  Open company user listing

Duff3 is an active member in the company
  No such test id  invitation-firstname-0
  Check company user  0  dummy3@example.com  Dummy3  Duff3  Käyttäjä  Kyllä

Convert Duff3 user to admin and rescind submit rights
  Edit company user  0  admin  Ei
  Click by test id  company-user-save-0
  Confirm  dynamic-yes-no-confirm-dialog
  Check company user  0  dummy3@example.com  Dummy3  Duff3  Ylläpitäjä  Ei

Canceled edit does not change user information
  Edit company user  0  user  Kyllä
  Click by test id  company-user-cancel-0
  Check company user  0  dummy3@example.com  Dummy3  Duff3  Ylläpitäjä  Ei

Can not add the same user again
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  dummy3@example.com
  Click enabled by test id  company-search-email
  Wait until  Element should be visible  //div[@id="dialog-company-new-user"]//span[@data-bind="ltext: 'register.company.add-user.already-in'"]
  Click enabled by test id  company-add-user-already-in-close
  Wait until  Element should not be visible  dialog-company-new-user

Can not add the financial authority as company user
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  No such test id  user-not-applicant
  Input text by test id  company-new-user-email  financial@ara.fi
  Click enabled by test id  company-search-email
  Wait test id visible  user-not-applicant
  Click enabled by test id  close-not-applicant
  Wait until  Element should not be visible  dialog-company-new-user

Delete Duff3
  Element should be visible by test id  company-user-delete-0
  Click element  xpath=//section[@id='company']//a[@data-test-id="company-user-delete-0"]
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Page should not contain  dummy3@example.com

Invite Duff3 again
  Invite existing dummy user  dummy3@example.com  Duff3  Dummy3  False  True  True

Duff3 user gets invite email again
  Open last email
  Wait Until  Page Should Contain  dummy3@example.com
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]
  Wait until  Element should be visible  xpath=//section[@id='invite-company-user']//p[@data-test-id='invite-company-user-success']

Add new user
  Invite unregistered person  user2@solita.fi  Ulla  Ser

New user gets email
  Open last email
  Wait Until  Page Should Contain  user2@solita.fi
  Page Should Contain  /app/fi/welcome#!/new-company-user/
  ${invite} =  Get Element Attribute  //a[contains(@href,'new-company-user')]  href
  Set Suite Variable  ${ullaInvite}  ${invite}
  Click link  xpath=//a[contains(@href,'new-company-user')]

Registration page opens
  Wait until  Page should contain  Solita Oy
  Wait until  Page should contain  1060155-5

Password must be at least 8 characters
  Input text by test id  password1  lyhyt12
  Input text by test id  password2  lyhyt12
  Test id disabled  testCompanyUserSubmitPassword
  Input text by test id  password1  pitka123
  Input text by test id  password2  pitka123
  Click enabled by test id  testCompanyUserSubmitPassword
  Wait Until  Page should contain  Salasana asetettu.
  Confirm notification dialog

New user tries to use invitation again and gets directed to login
  Go To  ${ullaInvite}
  Wait until  Page should contain  Linkki on jo käytetty
  Wait until  Element should be visible  login-button

New user logs in
  Go to page  login
  Applicant logs in  user2@solita.fi  pitka123  Ulla Ser
  Confirm notification dialog

Ulla sees herself as company admin
  Open company user listing
  # Ser, Solita
  Check company user  1  user2@solita.fi  Ser  Ulla  Ylläpitäjä  Ei
  No such test id  company-user-delete-1
  Wait test id visible  company-add-user
  Wait test id visible  company-user-edit-2
  Wait test id visible  company-user-delete-2

#Ulla invites Pena into company
#  Invite existing user  pena@example.com  Pena  Panaani  False  False
#  Check invitation  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei

Ulla logs in and changes her username
  Open My Page
  Wait until  Element Should Be Enabled  xpath=//input[@data-test-id='newEmail']
  Input text  xpath=//input[@data-test-id='newEmail']  ulla.ser@solita.fi
  Click by test id  change-email
  Wait for jquery
  Open last email
  Click link  xpath=//a
  Wait until  Element should be visible  xpath=//section[@id='email']//div[@data-test-id='init-email-change']
  Wait until  Element should be visible  vetuma-init-email
  Click element  vetuma-init-email
  Wait until  Element should be visible by test id  submit-button
  Click element  xpath=//input[@data-test-id='submit-button']
  Wait until  Element should be visible by test id  login-new-email
  Click element  xpath=//a[@data-test-id='login-new-email']

Subsequent username changes must use the same person id.
  Applicant logs in  ulla.ser@solita.fi  pitka123  Ulla Ser
  Open My Page
  Wait until  Element Should Be Enabled  xpath=//input[@data-test-id='newEmail']
  Input text  xpath=//input[@data-test-id='newEmail']  res.allu@solita.fi
  Click by test id  change-email
  Wait for jquery
  Open last email
  Click link  xpath=//a
  Wait until  Element should be visible  xpath=//section[@id='email']//div[@data-test-id='init-email-change']
  Wait until  Element should be visible  vetuma-init-email
  Click element  vetuma-init-email
  Wait until  Element should be visible by test id  submit-button
  Fill test id  dummy-login-userid  240441-937H
  Click element  xpath=//input[@data-test-id='submit-button']
  Wait until  Element should be visible  jquery=section#change-email p.error-message
  Logout

Kaino logs in and removes Ulla's admin rights
  # This is needed to make sure that only Kaino receives the
  # invitation mail from the next case.
  Kaino logs in
  Open company user listing
  # Dummy3, Ser, Solita
  Check company user  0  dummy3@example.com  Dummy3  Duff  Käyttäjä  Kyllä
  Edit company user  1  user  Kyllä
  Click by test id  company-user-save-1
  Confirm  dynamic-yes-no-confirm-dialog
  Check company user  1  ulla.ser@solita.fi  Ser  Ulla  Käyttäjä  Kyllä
  Logout

Kaino invites Teemu, a new user
  Invite unregistered person  user3@solita.fi  Teemu  Testaaja
  Open last email
  ${invite} =  Get Element Attribute  xpath=//a[contains(@href,'new-company-user')]  href
  Set Suite Variable  ${invitationPage}  ${invite}

Instead of reading his email, Teemu registers via front page
  Fill registration  Rambokuja 7  33800  sipoo  +358554433221  user3@solita.fi  vetuma69
  Activate account  user3@solita.fi
  Logout

Teemu reads his email and clicks the invitation link
  Go to  ${invitationPage}

Teemu is linked as an existing user even though he registered after the invite was sent (LPK-3759)
  Wait Until  Page Should Contain  Tilisi on liitetty onnistuneesti yrityksen tiliin.
  Logout

No frontend errors
  There are no frontend errors

*** Keywords ***

Invite unregistered person
  [Arguments]  ${email}  ${firstName}  ${lastName}
  Kaino logs in
  Open company user listing
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  ${email}
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-firstname  ${firstName}
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-lastname  ${lastName}
  Test id enabled  company-user-send-invite
  Click label  company-new-user-admin
  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait until  Element text should be  testCompanyAddUserDone  Käyttäjä kutsuttu.
  Click enabled by test id  company-new-user-invited-close-dialog
  Check invitation  0  ${email}  ${lastName}  ${firstName}  Ylläpitäjä  Ei
  Logout
