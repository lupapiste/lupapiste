*** Settings ***

Documentation   Email is send to municipality when application is submitted
Resource       ../../common_resource.robot
*** Test Cases ***

Sipoo admin sets email address for submit notification
  ${save_indicator} =  Set Variable  xpath=//section[@id="applications"]//tr[@data-test-id="onSubmitEmails-row"]//span[contains(@class, "save-indicator")]
  Set Suite Variable  ${email}  kirjaamo@sipoo.example.com
  Sipoo logs in
  Go to page  applications
  Element should not be visible  ${save_indicator}
  Input text by test id  onSubmitEmails  ${email}
  Wait Until  Element should be visible  ${save_indicator}
  [Teardown]  Logout

Mikko submits an application
  Mikko logs in
  Create application the fast way  Jättökatu  753-1-1-10  teollisuusrakennus
  Submit application

Kirjaamo got email
  Open last email
  Wait Until  Page Should Contain  ${email}

Try to open application
  Click link  xpath=//a

Link is was for authority, need to continue to login
  Wait Until  Click by test id  login
  User logs in  mikko@example.com  mikko123  Mikko Intonen
  Wait Until  Application address should be  Jättökatu
  [Teardown]  Logout
