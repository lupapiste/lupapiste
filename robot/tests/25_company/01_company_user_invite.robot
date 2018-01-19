*** Settings ***

Documentation   Users are added to company
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

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
  Input text by test id  company-new-user-email  financial@ara.fi
  Click enabled by test id  company-search-email
  Wait until  Element should be visible  //div[@id="dialog-company-new-user"]//span[@data-bind="text: loc('register.company.add-user.is-financial-authority', email())"]
  Click enabled by test id  company-user-is-financial-authority-close-dialog
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

Delete Duff3 again
  Kaino logs in
  Open company user listing
  Click by test id  company-user-delete-0
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Page should not contain  dummy3@example.com

Add new user
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  USER2@solita.fi
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-firstname  Ulla
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-lastname  Ser
  Test id enabled  company-user-send-invite
  Click label  company-new-user-admin
  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait until  Element text should be  testCompanyAddUserDone  Käyttäjä kutsuttu.
  Click enabled by test id  company-new-user-invited-close-dialog
  Check invitation  0  user2@solita.fi  Ser  Ulla  Ylläpitäjä  Ei

New user gets email
  Open last email
  Wait Until  Page Should Contain  user2@solita.fi
  Page Should Contain  /app/fi/welcome#!/new-company-user/
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

New user logs in
  Go to page  login
  Applicant logs in  user2@solita.fi  pitka123  Ulla Ser
  Confirm notification dialog

Ulla sees herself as company admin
  Open company user listing
  # Ser, Solita
  Check company user  0  user2@solita.fi  Ser  Ulla  Ylläpitäjä  Ei
  No such test id  company-user-delete-0
  Wait test id visible  company-add-user
  Wait test id visible  company-user-edit-1
  Wait test id visible  company-user-delete-1

Ulla invites Pena into company
  Invite existing user  pena@example.com  Pena  Panaani  False  False
  Check invitation  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei
  Logout

Pena accepts invitation
  Open last email
  Wait Until  Page Should Contain  pena@example.com
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]
  Wait until  Element should be visible  xpath=//section[@id='invite-company-user']//p[@data-test-id='invite-company-user-success']

Pena logs in and sees the non-admin view of the company
  Go to login page
  Pena logs in
  Open company user listing
  # Panaani, Ser, Solita
  Check company user  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei
  No such test id  company-add-user
  No such test id  company-user-edit-1
  No such test id  company-user-delete-1
  Logout

Ulla logs in and changes her username
  Go to page  login
  Applicant logs in  user2@solita.fi  pitka123  Ulla Ser
  Click Element  user-name
  Wait until  Element Should Be Enabled  xpath=//input[@data-test-id='newEmail']
  Input text  xpath=//input[@data-test-id='newEmail']  ulla.ser@solita.fi
  Click by test id  change-email
  Wait for jquery
  Open last email
  Click link  xpath=//a
  Wait until  Element should be visible  xpath=//section[@id='email']//div[@data-test-id='init-email-change']
  Wait until  Element should be visible  vetuma-init-email
  Click element  vetuma-init-email
  Element should be visible by test id  submit-button
  Click element  xpath=//input[@data-test-id='submit-button']
  Element should be visible by test id  login-new-email
  Click element  xpath=//a[@data-test-id='login-new-email']

Subsequent username changes must use the same person id.
  Applicant logs in  ulla.ser@solita.fi  pitka123  Ulla Ser
  Click Element  user-name
  Wait until  Element Should Be Enabled  xpath=//input[@data-test-id='newEmail']
  Input text  xpath=//input[@data-test-id='newEmail']  res.allu@solita.fi
  Click by test id  change-email
  Wait for jquery
  Open last email
  Click link  xpath=//a
  Wait until  Element should be visible  xpath=//section[@id='email']//div[@data-test-id='init-email-change']
  Wait until  Element should be visible  vetuma-init-email
  Click element  vetuma-init-email
  Element should be visible by test id  submit-button
  Fill test id  dummy-login-userid  240441-937H
  Click element  xpath=//input[@data-test-id='submit-button']
  Wait until  Element should be visible  jquery=section#change-email p.error-message
  Logout

Kaino logs in and removes Ulla's admin rights
  # This is needed to make sure that only Kaino receives the
  # invitation mail from the next case.
  Kaino logs in
  Open company user listing
  # Panaani, Ser, Solita
  Edit company user  1  user  Kyllä
  Click by test id  company-user-save-1
  Confirm  dynamic-yes-no-confirm-dialog
  Check company user  1  ulla.ser@solita.fi  Ser  Ulla  Käyttäjä  Kyllä
  Logout

Mikko logs in, creates application and invites Solita
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Ditiezhan${secs}
  Set Suite Variable  ${propertyId}  753-416-5-5
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Invite company to application  Solita Oy
  Logout

Solita accepts invite
  User logs in  kaino@solita.fi  kaino123  Kaino Solita
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  Yritysvaltuutus: ${appname}, Sipoo,
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Kaino Solita opens the application
  Open application  ${appname}  ${propertyId}

Kaino could submit application
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Wait until  Test id enabled  application-submit-btn
  Logout

Pena logs in and could not submit application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Test id disabled  application-submit-btn
  Submit application errors count is  1
  Logout

Mikko logs in and invites Pena directly
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Invite pena@example.com to application
  Logout

Pena logs in, accepts invitation and still cannot submit application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Confirm yes no dialog
  Open tab  requiredFieldSummary
  Test id disabled  application-submit-btn
  Logout

No frontend errors
  There are no frontend errors
