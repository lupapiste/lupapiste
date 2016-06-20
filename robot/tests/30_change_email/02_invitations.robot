*** Settings ***

Documentation   Mikko can accept invitations created for the old email after email change
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/vetuma_helpers.robot
Resource        ../common_keywords/email_helpers.robot

*** Test Cases ***

Authority creates an application
  [Tags]  integration
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  email-change-app${secs}
  Set Suite Variable  ${propertyId}  753-416-30-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Authority invites Mikko to application
  [Tags]  integration
  Invite Mikko
  Logout

Mikko changes his email
  [Tags]  integration
  Mikko logs in
  Mikko changes email to mikko@example.net
  Mikko receives email and clicks the email change link
  Mikko identifies himself via Vetuma
  Mikko logs in with the new email address

Mikko can open the old application
  [Tags]  integration
  Open application  ${appname}  ${propertyId}

Mikko sees the invitation modal dialog and accepts
  [Tags]  integration
  Wait Until  Page should contain  Sinut on kutsuttu tälle hakemukselle. Voit hyväksyä kutsun heti tai jatkaa hakemuksen katseluun.
  Click by test id  confirm-yes
  Wait until  Dialog is invisible
  Sleep  2s
  Wait until  Dialog is invisible

Mikko changes his email back to mikko@example.com
  [Tags]  integration
  Change email to  mikko@example.com
  Open last email and click the email change link
  Identify for email change via Vetuma
  Log in with new email address  mikko@example.com  mikko123  Mikko Intonen
  Logout

*** Keywords ***

Invite Mikko
  Open tab  parties
  Invite count is  0
  Click by test id  application-invite-person
  Wait until  Element should be visible  person-invite-email-1
  Input Text  person-invite-email-1  mikko@example.com
  Element should be enabled  xpath=//*[@data-test-id='person-invite-bubble-dialog-ok']
  Click by test id  person-invite-bubble-dialog-ok
  Wait until  Dialog is invisible
  Wait until  Invite count is  1

Mikko changes email to mikko@example.net
  Navigate to email change
  Wait Until  Textfield Value Should Be  newEmail  mikko@example.com
  Input text by test id  newEmail  mikko@example.net
  Wait Until  Element Should Be Enabled  //section[@id='mypage']//button[@data-test-id='change-email']
  Click element  //section[@id='mypage']//button[@data-test-id='change-email']
  Wait Until  Page should contain  Uuteen sähköpostiosoitteeseen on lähetetty viesti osoitteen vaihdosta

Mikko receives email and clicks the email change link
  Open last email
  Wait Until  Page Should Contain  mikko@example.net
  Page Should Contain  /app/fi/welcome#!/email/
  ## Click the first link
  Click link  xpath=//a

Mikko identifies himself via Vetuma
  Vetuma button is visible
  Authenticate via Nordea via Vetuma  vetuma-init-email
  Wait Until  Page should contain  Voit nyt kirjautua sisään uudella sähköpostiosoitteellasi.

Mikko logs in with the new email address
  Element should be visible by test id  login-new-email
  Click by test id  login-new-email
  Wait Until  Page should contain  Haluan kirjautua palveluun
  Applicant logs in  mikko@example.net  mikko123  Mikko Intonen

Navigate to email change
  Click Element  user-name
  Open accordion by test id  mypage-change-email-accordion
  Wait Until  Element Should be visible  oldEmail

Vetuma button is visible
  Wait until page contains element  vetuma-init-email
