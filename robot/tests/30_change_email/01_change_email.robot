*** Settings ***

Documentation   Mikko changes email address
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/vetuma_helpers.robot

*** Test Cases ***

# TODO tag integration

Create an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  email-change-app${secs}
  Set Suite Variable  ${propertyId}  753-416-30-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Navigate to email change
  Click Element  user-name
  Open accordion by test id  mypage-change-email-accordion
  Wait Until  Element Should be visible  newEmail

Old email is preset
  Textfield Value Should Be  newEmail  mikko@example.com

Can not submit yet
  Element Should Be Disabled  //section[@id='mypage']//button[@data-test-id='change-email']

Change to .net address
  Input text by test id  newEmail  mikko@example.net
  Wait Until  Element Should Be Enabled  //section[@id='mypage']//button[@data-test-id='change-email']
  Click element  //section[@id='mypage']//button[@data-test-id='change-email']
  # TODO implement and test that a help popup is present
  # instead of Sleeping
  Sleep  1

Got email
  Open last email
  Wait Until  Page Should Contain  mikko@example.net
  Page Should Contain  /app/fi/welcome#!/email/
  ## Click the first link
  Click link  xpath=//a

Go through vetuma
  Vetuma button is visible
  Authenticate via Nordea via Vetuma  vetuma-init-email

Got info that email is changed
  # TODO check info text
  Element should be visible by test id  login-new-email
  Click by test id  login-new-email
  Wait Until  Page should contain  Haluan kirjautua palveluun

Got email to old address
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  mikko@example.com

Email should mention the new email address
  Page Should Contain  mikko@example.net

Login with the old email fails
  Go to login page
  Login fails  mikko@example.com  mikko123

Login with the new email succeeds
  Applicant logs in  mikko@example.net  mikko123  Mikko Intonen

Mikko can open the old application
  Open application  ${appname}  ${propertyId}

Party table has been updated
  Open tab  parties
  Page Should Not Contain  mikko@example.com
  Page Should Contain  mikko@example.net

*** Keywords ***

Vetuma button is visible
  Wait until page contains element  vetuma-init-email
