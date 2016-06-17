*** Settings ***

Documentation   Mikko can change his email address to one with an existing dummy user
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/vetuma_helpers.robot
Resource        ../common_keywords/email_helpers.robot

*** Test Cases ***

Authority creates an application
  [Tags]  integration
  As Sonja
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  email-change-app${secs}
  Set Suite Variable  ${propertyId}  753-416-30-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Authority creates another application
  [Tags]  integration
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname2}  email-change-app${secs}
  Set Suite Variable  ${propertyId2}  753-416-30-1
  Create application the fast way  ${appname2}  ${propertyId2}  kerrostalo-rivitalo

Authority invites mikko@example.net to the first application
  [Tags]  integration
  Open application  ${appname}  ${propertyId}
  Invite to application with email  mikko@example.net

Authority invites Mikko and mikko@example.net to the first application
  [Tags]  integration
  Open application  ${appname2}  ${propertyId2}
  Invite to application with email  mikko@example.com
  Invite to application with email  mikko@example.net
  Wait until  Invite count is  2
  Logout

Mikko opens the second application and accepts invitation
  [Tags]  integration
  Mikko logs in
  Open application  ${appname2}  ${propertyId2}
  Accept invitation
  Wait until  Invite count is  1

Mikko changes his email to mikko@example.net
  [Tags]  integration
  Change email to  mikko@example.net
  Open last email and click the email change link
  Identify for email change via Vetuma
  Log in with new email address  mikko@example.net  mikko123  Mikko Intonen

The invitation for mikko@example.net has been removed
  [Tags]  integration
  Open application  ${appname2}  ${propertyId2}
  Wait until  Invite count is  0

Mikko now has an invitation for the first application
  [Tags]  integration
  Open application  ${appname}  ${propertyId}
  Wait until  Invite count is  1
  Accept invitation
  Wait until  Invite count is  0

*** Keywords ***

Invite to application with email
  [Arguments]  ${userEmail}
  Open tab  parties
  Click by test id  application-invite-person
  Wait until  Element should be visible  person-invite-email-1
  Input Text  person-invite-email-1  ${userEmail}
  Element should be enabled  xpath=//*[@data-test-id='person-invite-bubble-dialog-ok']
  Click by test id  person-invite-bubble-dialog-ok
  Wait until  Mask is invisible

Navigate to email change
  Click Element  user-name
  Open accordion by test id  mypage-change-email-accordion
  Wait Until  Element Should be visible  oldEmail

Mask is invisible
  Element should not be visible  xpath=//div[@id='ModalDialogMask']

Vetuma button is visible
  Wait until page contains element  vetuma-init-email

Accept invitation
  Wait Until  Page should contain  Sinut on kutsuttu tälle hakemukselle. Voit hyväksyä kutsun heti tai jatkaa hakemuksen katseluun.
  Click by test id  confirm-yes
  Wait until  Mask is invisible
