*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot
Suite teardown  Logout

*** Test Cases ***

Mikko opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  conversation${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Mikko writes message without sending it
  Open application  ${appname}  ${propertyId}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  Kirjoitan viestin, mutta unohdan lähettää sen... tarkoituksella!

Mikko opens applications page
  Go to page  applications

Mikko can send unsent message via dialog
  Wait Until  Element Should Be Visible  dynamic-yes-no-confirm-dialog
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element Should Be Visible  xpath=//div[@id='conversation-panel']//button[@data-test-id='application-new-comment-btn']
  Click by test id  application-new-comment-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='Kirjoitan viestin, mutta unohdan lähettää sen... tarkoituksella!']
