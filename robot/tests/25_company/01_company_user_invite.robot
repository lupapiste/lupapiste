*** Settings ***

Documentation   Users are added to company
Resource        ../../common_resource.robot
Suite teardown  Logout

*** Test Cases ***

Company admin logs in
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita
  Open company user listing

Add existing dummy user
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  dummy@example.com
  Click enabled by test id  company-search-email
  Wait until  Element text should be  xpath=//h3[@data-test-id='company-old-user-invited']  Käyttäjä kutsuttu.
  Click enabled by test id  company-old-user-invited-close-dialog
  Wait until  Element text should be  xpath=//td[@data-test-id='company-invited-user-email']  dummy@example.com

New user is invited as a regular user
  Element text should be  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']/td[@data-test-id='company-user-role']  Käyttäjä
  Element text should be  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']/td[@data-test-id='company-user-invited']  Kutsuttu

Dummy user gets invite email
  Open last email
  Wait Until  Page Should Contain  dummy@example.com
  Page Should Contain  /app/fi/welcome#!/invite-company-user/ok/
  Click link  xpath=//a[contains(@href,'invite-company-user')]

Account is linked
  Wait Until  Page Should Contain  Tilisi on liitetty onnistuneesti yrityksen tiliin.
  Click link  Siirry Lupapiste-palveluun

Dummy user gets password reset email
  Open last email
  Wait Until  Page Should Contain  dummy@example.com
  Page Should Contain  /app/fi/welcome#!/reset-password

Admin logs in again
  Go to login page
  Login  kaino@solita.fi  kaino123
  User should be logged in  Kaino Solita

Dummy user is active in company
  Open company user listing
  Wait Until  Element text should be  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']/td[@data-test-id='company-user-invited']  Käyttäjä

Conver dummy user to admin
  Click link  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']//a[@data-test-id='company-user-toggle-admin']
  Confirm  dialog-company-user-op
  Element text should be  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']/td[@data-test-id='company-user-role']  Ylläpitäjä

Can not add the same user again
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  dummy@example.com
  Click enabled by test id  company-search-email
  Wait until  Element should be visible  //div[@id="dialog-company-new-user"]//span[@data-bind="ltext: 'register.company.add-user.already-in'"]
  Click enabled by test id  company-add-user-already-in-close

Delete dummy user
  Click link  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']//a[@data-test-id='company-user-toggle-delete']
  Confirm  dialog-company-user-op
  Wait Until  Page should not contain element  //table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-dummy@example.com']//a[@data-test-id='company-user-toggle-delete']

Add new user
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  USER2@solita.fi
  Click enabled by test id  company-search-email
  Wait until  Element should be visible  companyUserDetails
  Input text by test id  company-new-user-firstname  Ulla
  Input text by test id  company-new-user-lastname  Ser
  Select Checkbox  register.company.add-user.admin
  Click enabled by test id  company-user-submit
  Wait until  Element text should be  testCompanyAddUserDone  Käyttäjä kutsuttu.
  Click enabled by test id  company-new-user-invited-close-dialog
  Wait until  Element text should be  xpath=//td[@data-test-id='company-invited-user-email']  user2@solita.fi

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

User sees herself as company admin
  Open company user listing
  Wait Until  Element text should be  xpath=//table[@data-test-id='company-users-table']//tr[@data-test-id='company-user-user2@solita.fi']/td[@data-test-id='company-user-role']  Ylläpitäjä
  [Teardown]  Logout

# Custom account

Solita admin sets custom account for company 'Solita Oy', max users 2
  SolitaAdmin logs in
  Wait until  Click element  xpath=//li/a[contains(text(), "Yritykset")]
  Wait until  Click element  xpath=//table[@data-test-id="corporations-table"]//tr[@data-test-id="company-row-solita"]//a[@data-test-id="company-edit"]
  Wait until  Element text should be  xpath=//div[@data-test-id="modal-dialog-content"]/div[@class="header"]/span[@class="title"]  Muokkaa yritysta
  Select from list by value  xpath=//select[@name="account-type"]  custom
  Input text  xpath=//input[@name="customAccountLimit"]  2
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

