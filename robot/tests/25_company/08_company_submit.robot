*** Settings ***

Documentation   Company can block submission
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

*** Test Cases ***

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

Kaino adds Pena
  Open company user listing
  Invite existing user  pena@example.com  Pena  Panaani  False  False
  Check invitation  0  pena@example.com  Panaani  Pena  Käyttäjä  Ei

Kaino Solita opens the application
  Open application  ${appname}  ${propertyId}

Kaino could submit application
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Wait until  Test id enabled  application-submit-btn
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

Pena opens application and could not submit application
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Wait until  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//h1
  Test id disabled  application-submit-btn
  # After permission :application/submit it's no longer viable to show errors in case of company non-submitting user
  #Submit application errors count is  1
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


Frontend errors
  There are no frontend errors
