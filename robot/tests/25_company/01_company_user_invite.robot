*** Settings ***

Documentation   Users are added to company
Resource        ../../common_resource.robot
Suite Teardown  Logout
Default Tags    company

*** Test Cases ***

Company admin logs in
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
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
  Open last email
  Wait Until  Page Should Contain  dummy3@example.com
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]

Account is linked
  Wait Until  Page Should Contain  Tilisi on liitetty onnistuneesti yrityksen tiliin.
  Click link  Siirry Lupapiste-palveluun

Duff3 user gets password reset email
  Open last email
  Wait Until  Page Should Contain  dummy3@example.com
  Page Should Contain  /app/fi/welcome#!/setpw/

Admin logs in again
  Go to login page
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
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

Delete Duff3
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
  Input text by test id  company-user-password1  lyhyt12
  Input text by test id  company-user-password2  lyhyt12
  Element should be disabled  testCompanyUserSubmitPassword
  Input text by test id  company-user-password1  pitka123
  Input text by test id  company-user-password2  pitka123
  Element should be enabled  testCompanyUserSubmitPassword
  Click element  testCompanyUserSubmitPassword
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
  Invite existing user  pena@example.com  Pena  Panaani  false  false
  Check invitation  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei
  [Teardown]  Logout

Pena accepts invitation
  Open last email
  Wait Until  Page Should Contain  pena@example.com
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]

Pena logs in and sees the non-admin view of the company
  Go to login page
  Pena logs in
  Open company user listing
  # Panaani, Ser, Solita
  Check company user  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei
  No such test id  company-add-user
  No such test id  company-user-edit-1
  No such test id  company-user-delete-1
  [Teardown]  Logout

Kaino logs in and removes Ulla's admin rights
  # This is needed to make sure that only Kaino receives the
  # invitation mail from the next case.
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
  Open company user listing
  # Panaani, Ser, Solita
  Edit company user  1  user  Kyllä
  Click by test id  company-user-save-1
  Confirm  dynamic-yes-no-confirm-dialog
  Check company user  1  user2@solita.fi  Ser  Ulla  Käyttäjä  Kyllä
  [Teardown]  Logout

Mikko logs in, creates application and invites Solita
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Ditiezhan${secs}
  Set Suite Variable  ${propertyId}  753-416-5-5
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Invite company to application  Solita Oy
  [Teardown]  Logout

Solita accepts invite
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  kaino@solita.fi
  Click Element  xpath=(//a)[2]
  Wait until  Page should contain  Hakemus on liitetty onnistuneesti yrityksen tiliin.
  [Teardown]  Go to login page

Kaino logs in and could submit application
  Login  kaino@solita.fi  kaino123
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Test id enabled  application-submit-btn
  [Teardown]  Logout

Pena logs in and could not submit application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Test id disabled  application-submit-btn
  [Teardown]  Logout

Mikko logs in and invites Pena directly
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Invite pena@example.com to application
  [Teardown]  Logout

Pena logs in, accepts invitation and still cannot submit application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Confirm yes no dialog
  Open tab  requiredFieldSummary
  Test id disabled  application-submit-btn
  [Teardown]  Logout

# Custom account

Solita admin sets custom account for company 'Solita Oy', max users 3
  SolitaAdmin logs in
  Wait until  Click element  xpath=//li/a[contains(text(), "Yritykset")]
  Wait until  Click element  xpath=//table[@data-test-id="corporations-table"]//tr[@data-test-id="company-row-solita"]//a[@data-test-id="company-edit"]
  Wait until  Element text should be  xpath=//div[@data-test-id="modal-dialog-content"]/div[@class="header"]/span[@class="title"]  Muokkaa yritysta
  Select from list by value  xpath=//select[@name="account-type"]  custom
  Input text with jQuery   input[name="customAccountLimit"]  3
  Focus  xpath=//button[@data-test-id="modal-dialog-submit-button"]
  Click by test id  modal-dialog-submit-button
  Wait Until  Element should not be visible  xpath=//div[@data-test-id="modal-dialog-content"]
  Logout

Kaino logs in and sees account is custom, and it can't be changed by Kaino
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
  Open company details
  Wait until  Element should be visible  xpath=//span[@data-test-id="company-custom-account"]
  Element should not be visible  xpath=//select[@data-test-id="company-account-select"]

Kaino wants to invite new users, but can't because account limit is reached
  Open company user listing
  Element should be disabled  xpath=//button[@data-test-id="company-add-user"]
  Element should be visible  xpath=//span[@class="user-limit-reached"]

*** Keywords ***

Invite existing dummy user
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${admin}=false  ${submit}=true
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Test id disabled  company-search-email
  Input text by test id  company-new-user-email  ${email}
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email

  Textfield value should be  jquery=[data-test-id=company-new-user-firstname]  ${EMPTY}
  Test id enabled  company-new-user-firstname
  Textfield value should be  jquery=[data-test-id=company-new-user-lastname]  ${EMPTY}
  Test id enabled  company-new-user-lastname
  Input text by test id  company-new-user-firstname  ${firstname}
  Input text by test id  company-new-user-lastname  ${lastname}
  Run keyword if  '${admin}' == 'true'  Click label  company-new-user-admin
  Run keyword unless  '${submit}' == 'true'  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait Test id visible  company-add-user-done
  Click by test id  company-new-user-invited-close-dialog

Invite existing user
  [Arguments]  ${email}  ${firstname}  ${lastname}  ${admin}=false  ${submit}=true
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Test id disabled  company-search-email
  Input text by test id  company-new-user-email  ${email}
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email

  Textfield should contain  jquery=[data-test-id=company-new-user-firstname]  ${firstname}
  Test id disabled  company-new-user-firstname
  Textfield should contain  jquery=[data-test-id=company-new-user-lastname]  ${lastname}
  Test id disabled  company-new-user-lastname

  Run keyword if  '${admin}' == 'true'  Click label  company-new-user-admin
  Run keyword unless  '${submit}' == 'true'  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait Test id visible  company-add-user-done
  Click by test id  company-new-user-invited-close-dialog

Check invitation
  [Arguments]  ${index}  ${email}  ${lastname}  ${firstname}  ${role}  ${submit}
  Test id should contain  invitation-lastname-${index}  ${lastname}
  Test id should contain  invitation-firstname-${index}  ${firstname}
  Test id should contain  invitation-email-${index}  ${email}
  Test id should contain  invitation-invited-${index}  Kutsuttu
  Test id should contain  invitation-role-${index}  ${role}
  Test id should contain  invitation-submit-${index}  ${submit}

Check company user
  [Arguments]  ${index}  ${email}  ${lastname}  ${firstname}  ${role}  ${submit}
  Test id should contain  company-user-lastname-${index}  ${lastname}
  Test id should contain  company-user-firstname-${index}  ${firstname}
  Test id should contain  company-user-email-${index}  ${email}
  Test id should contain  company-user-enabled-${index}  Käytössä
  Test id should contain  company-user-role-${index}  ${role}
  Test id should contain  company-user-submit-${index}  ${submit}

Edit company user
  [Arguments]  ${index}  ${role}  ${submit}
  Click by test id  company-user-edit-${index}
  Select from test id  company-user-edit-role-${index}  ${role}
  Select from test id  company-user-edit-submit-${index}  ${submit}

