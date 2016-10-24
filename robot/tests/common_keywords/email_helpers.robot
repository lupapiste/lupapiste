*** Settings ***
Resource        ../common_keywords/vetuma_helpers.robot

*** Keywords ***

Ident button is visible
  Wait until page contains element  vetuma-init

Authenticate via dummy page
  Click by test id  vetuma-init
  Fill test id  dummy-login-userid  210281-9988
  Wait test id visible  submit-button
  Click by test id  submit-button

Navigate to email change
  Click Element  user-name
  Open accordion by test id  mypage-change-email-accordion
  Wait Until  Element Should be visible  oldEmail

Change email to
  [Arguments]  ${newEmail}
  Navigate to email change
  Input text by test id  newEmail  ${newEmail}
  Wait Until  Element Should Be Enabled  //section[@id='mypage']//button[@data-test-id='change-email']
  Click element  //section[@id='mypage']//button[@data-test-id='change-email']
  Wait Until  Page should contain  Uuteen sähköpostiosoitteeseen on lähetetty viesti osoitteen vaihdosta

Open last email and click the email change link
  Open last email
  Wait Until  Page Should Contain  mikko@example.net
  Page Should Contain  /app/fi/welcome#!/email/
  ## Click the first link
  Click link  xpath=//a

Identify for email change via dummy page
  Ident button is visible
  Click by test id  vetuma-init
  Fill test id  dummy-login-userid  210281-9988
  Wait test id visible  submit-button
  Click by test id  submit-button
  Wait Until  Page should contain  Voit nyt kirjautua sisään uudella sähköpostiosoitteellasi.

Log in with new email address
  [Arguments]  ${newEmail}  ${password}  ${name}
  Element should be visible by test id  login-new-email
  Click by test id  login-new-email
  Wait Until  Page should contain  Haluan kirjautua palveluun
  Applicant logs in  ${newEmail}  ${password}  ${name}

Dialog is invisible
  Element should not be visible  xpath=//div[@id='modal-dialog-content']
