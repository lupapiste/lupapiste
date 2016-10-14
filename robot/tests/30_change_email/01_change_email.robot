*** Settings ***

Documentation   Mikko changes email address
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/email_helpers.robot

*** Test Cases ***

Auhtority can not change email
  [Tags]  integration
  Sonja logs in
  Navigate to email change
  Wait Until  Textfield Value Should Be  oldEmail  sonja.sibbo@sipoo.fi
  Page Should Contain  Sähköpostiosoitteen vaihtaminen ei onnistu
  Logout

Applicant creates an application
  [Tags]  integration
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  email-change-app${secs}
  Set Suite Variable  ${propertyId}  753-416-30-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Old email is preset when changing it
  [Tags]  integration
  Navigate to email change
  Wait Until  Textfield Value Should Be  newEmail  mikko@example.com

Can not submit yet
  [Tags]  integration
  Element Should Be Disabled  //section[@id='mypage']//button[@data-test-id='change-email']

Change to .net address
  [Tags]  integration
  Input text by test id  newEmail  mikko@example.net
  Wait Until  Element Should Be Enabled  //section[@id='mypage']//button[@data-test-id='change-email']
  Click element  //section[@id='mypage']//button[@data-test-id='change-email']
  Wait Until  Page should contain  Uuteen sähköpostiosoitteeseen on lähetetty viesti osoitteen vaihdosta

Got email
  [Tags]  integration
  Open last email
  Wait Until  Page Should Contain  mikko@example.net
  Page Should Contain  /app/fi/welcome#!/email/
  ## Click the first link
  Click link  xpath=//a

Go through vetuma
  [Tags]  integration
  Ident button is visible
  Authenticate via dummy page

Got info that email is changed
  [Tags]  integration
  Wait Until  Page should contain  Voit nyt kirjautua sisään uudella sähköpostiosoitteellasi.
  Element should be visible by test id  login-new-email
  Click by test id  login-new-email
  Wait Until  Page should contain  Haluan kirjautua palveluun

Got email to old address
  [Tags]  integration
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  mikko@example.com

Email should mention the new email address
  [Tags]  integration
  Page Should Contain  mikko@example.net

Login with the old email fails
  [Tags]  integration
  Go to login page
  Login fails  mikko@example.com  mikko123

Login with the new email succeeds
  [Tags]  integration
  Applicant logs in  mikko@example.net  mikko123  Mikko Intonen

Mikko can open the old application
  [Tags]  integration
  Open application  ${appname}  ${propertyId}

Party table has been updated
  [Tags]  integration
  Open tab  parties
  Page Should Not Contain  mikko@example.com
  Page Should Contain  mikko@example.net

Mikko changes his email back to mikko@example.com
  [Tags]  integration
  Change email to  mikko@example.com
  Open last email and click the email change link
  Identify for email change via dummy page
  Log in with new email address  mikko@example.com  mikko123  Mikko Intonen
  Logout

*** Keywords ***

Navigate to email change
  Click Element  user-name
  Open accordion by test id  mypage-change-email-accordion
  Wait Until  Element Should be visible  oldEmail
