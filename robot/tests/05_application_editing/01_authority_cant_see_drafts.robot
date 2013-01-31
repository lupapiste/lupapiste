*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  authority-cant-see-drafts  753  75341600250030
  Logout

Sonja should not see applications at this stage
  Sonja logs in
  Request should not be visible  authority-cant-see-drafts
  Logout

Mikko goes to application page
  Mikko logs in
  Open application  authority-cant-see-drafts

Application is in draft state
  Applicantion state is  Luonnos

There are no comments yet
  Open tab  conversation
  Comment count is  0

Mikko adds a comment
  Add comment  huba huba

Application is now in stage valmisteilla
  Applicantion state is  Valmisteilla
  Logout

Sonja should see one (Sipoo) application
  Sonja logs in
  Request should be visible  authority-cant-see-drafts
  Logout

Veikko should see only zero (Tampere) applications
  Veikko logs in
  Request should not be visible  authority-cant-see-drafts
  Logout

*** Keywords ***

Comment count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='application']//table[@data-test-id='application-comments-table']//tr  ${amount}

Applicantion state is
  [Arguments]  ${state}
  Element should contain  xpath=//section[@id='application']//span[@data-test-id='application-state']  ${state}
