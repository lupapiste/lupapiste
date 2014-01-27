*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  authority-cant-see-drafts  753  753-416-25-30  asuinrakennus
  [Teardown]  logout

Sonja should not see applications at this stage
  Sonja logs in
  Request should not be visible  authority-cant-see-drafts
  [Teardown]  logout

Mikko goes to application page
  Mikko logs in
  Open application  authority-cant-see-drafts  753-416-25-30

Application is in draft state
  Application state should be  draft

There are no comments yet
  Open tab  conversation
  Comment count is  0

Mikko adds a comment
  Add comment  huba huba

Application is now in stage valmisteilla
  Application state should be  open
  It is possible to add operation
  [Teardown]  logout

Sonja should see one (Sipoo) application
  Sonja logs in
  Request should be visible  authority-cant-see-drafts
  [Teardown]  logout

Veikko should see only zero (Tampere) applications
  Veikko logs in
  Request should not be visible  authority-cant-see-drafts
  [Teardown]  logout

*** Keywords ***

Comment count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='application']//div[@data-test-id='comments-table']//div[contains(@class, 'comment')]  ${amount}
