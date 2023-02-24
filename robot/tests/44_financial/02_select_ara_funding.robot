*** Settings ***

Documentation   Authority selects ARA funding for application
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja creates application with ARA funding
  Sonja logs in  False
  Create application the fast way  fundig-app-1  753-416-25-31  kerrostalo-rivitalo
  Open application  fundig-app-1  753-416-25-31
  Open tab  info
  Open accordions  info
  Select checkbox  select-funding-checkbox
  Confirm  dynamic-yes-no-confirm-dialog

Financial authority is added to parties table
  Open tab  parties
  Wait until  Xpath Should Match X Times  //table//tr[contains(@class, 'party')]  2
  Is authorized party  ARA-käsittelijä

Financial authority gets mail about new ARA application
  Open last email
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='to']  financial@ara.fi
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='subject']  Lupapiste: Ilmoitus uudesta ARA-hankkeesta Lupapisteessä

Sipoo admin adds housing office email
  Go to login page
  Sipoo logs in
  Go to page  applications
  Input text by test id  fundingEmails  housing.office@sipoo.com
  Wait until  Positive indicator should be visible

Check that email will be properly initialized
  Reload page
  Test id input is  fundingEmails  housing.office@sipoo.com
  [Teardown]  Logout

Sonja submits application
  Sonja logs in
  Open application  fundig-app-1  753-416-25-31
  Submit application

Housing office get email notification about ARA funding application
  Open last email
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='to']  housing.office@sipoo.com
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='subject']  Lupapiste: ARA-hanke Lupapisteessä on jätetty vireille

Sonja removes ARA funding from application
  Go to login page
  Sonja logs in
  Open application  fundig-app-1  753-416-25-31
  Open tab  info
  Open accordions  info
  Wait until  Element should be visible  select-funding-checkbox
  Scroll to xpath  //*[@id="select-funding-checkbox"]
  Unselect checkbox  select-funding-checkbox
  Confirm  dynamic-yes-no-confirm-dialog

Financial authority is removed from parties
  Open tab  parties
  Wait until  Xpath Should Match X Times  //table//tr[contains(@class, 'party')]  1
  Is not authorized party  ARA-käsittelijä

Financial authority gets notification email
  Open last email
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='to']  financial@ara.fi
  Wait until  Element Text Should Be  xpath=//dd[@data-test-id='subject']  Lupapiste: Ilmoitus ARA-hankkeesta Lupapisteessä peruttu
