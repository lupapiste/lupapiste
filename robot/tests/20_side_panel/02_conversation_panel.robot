*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates new application
  Mikko logs in
  Go to page  applications
  Applications page should be open
  Create application the fast way  create-app  753-416-25-22  kerrostalo-rivitalo
  Go to page  applications
  Request should be visible  create-app

Mikko writes message without sending it
  Open application  create-app  753-416-25-22
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
