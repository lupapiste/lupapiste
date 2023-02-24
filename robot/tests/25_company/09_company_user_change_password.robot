*** Settings ***

Documentation   User added to company can change his email LPK-2641 & LP-365695
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

*** Test Cases ***

Company admin logs in
  Kaino logs in
  Open company user listing

Add new basic user
  Click enabled by test id  company-add-user
  Wait until  Element should be visible  dialog-company-new-user
  Input text by test id  company-new-user-email  basic@solita.fi
  Click enabled by test id  company-search-email
  Test id disabled  company-new-user-email
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-firstname  Basic
  Test id disabled  company-user-send-invite
  Input text by test id  company-new-user-lastname  User
  Test id enabled  company-user-send-invite
  Click label  company-new-user-submit
  Click enabled by test id  company-user-send-invite
  Wait until  Element text should be  testCompanyAddUserDone  Käyttäjä kutsuttu.
  Click enabled by test id  company-new-user-invited-close-dialog
  Check invitation  0  basic@solita.fi  User  Basic  Käyttäjä  Ei

New user gets email
  Open last email
  Wait Until  Page Should Contain  basic@solita.fi
  Page Should Contain  /app/fi/welcome#!/new-company-user/
  Click link  xpath=//a[contains(@href,'new-company-user')]


Registration page opens
  Wait until  Page should contain  Solita Oy
  Wait until  Page should contain  1060155-5

Input password
  Input text by test id  password1  passu123
  Input text by test id  password2  passu123
  Click enabled by test id  testCompanyUserSubmitPassword
  Wait Until  Page should contain  Salasana asetettu.
  Confirm notification dialog

Basic logs in
  Go to page  login
  Applicant logs in  basic@solita.fi  passu123  Basic User
  Confirm notification dialog

Basic can change email
  Open My Page
  Wait until  Element Should Be Enabled  xpath=//input[@data-test-id='newEmail']
  Input text  xpath=//input[@data-test-id='newEmail']  new@solita.fi
  Click by test id  change-email
  Wait for jquery
  Open last email
  Click link  xpath=//a[contains(@href,'change-email-simple')]
  # land on change-email-simple page and sees success button
  Element should be visible by test id  login-new-email-simple
  # no vetuma button as this is company user LPK-2641 & LP-365695
  Wait until  Element should not be visible  vetuma-init-email
  Click element  xpath=//a[@data-test-id='login-new-email-simple']

Basic user can't login with old email
  Login fails  basic@solita.fi  passu123

Login succeeds with new email
  Applicant logs in  new@solita.fi  passu123  Basic User

Frontend errors
  There are no frontend errors
