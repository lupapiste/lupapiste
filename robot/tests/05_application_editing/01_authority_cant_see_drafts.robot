*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja should not see applications at this stage
  Sonja logs in
  Wait until  Number of requests on page  application  0
  Logout

Mikko goes to application page
  Mikko logs in
  Open the application
  Wait Until  Element should be visible  application

Application is in draft state
  Wait until  Applicantion state is  Luonnos

There are no comments yet
  Click by test id  application-open-conversation-tab
  Wait Until  Element should be visible  application-conversation-tab
  Comment count is  0

Mikko adds a comment
  Input text  xpath=//textarea[@data-test-id='application-new-comment-text']  foo
  Click by test id  application-new-comment-btn
  Wait Until  Comment count is  1

Application is now in stage valmisteilla
  Wait until  Applicantion state is  Valmisteilla
  Logout

Sonja should see one (Sipoo) application
  Sonja logs in
  Wait until  Number of requests on page  application  1
  Logout

Veikko should see only zero (Tampere) applications
  Veikko logs in
  Wait until  Number of requests on page  application  0
  Logout

*** Keywords ***

Comment count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='application']//table[@data-test-id='application-comments-table']//tr  ${amount}

Applicantion state is
  [Arguments]  ${state}
  Element should contain  xpath=//section[@id='application']//span[@data-test-id='application-state']  ${state}
