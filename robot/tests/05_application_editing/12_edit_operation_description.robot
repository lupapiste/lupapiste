*** Settings ***

Documentation  Mikko set description for an operation
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753-416-25-30  kerrostalo-rivitalo

Mikko edits operation description
  Open application  application-papplication  753-416-25-30
  Wait and click  xpath=//div[@id='application-info-tab']//span[@data-test-id='edit-op-description']
  Input text by test id  op-description-editor  Talo A
  Wait until  Page should contain  Tallennettu
  Wait until  Element should not be visible  xpath=//div[@id='application-info-tab']//input[@data-test-id="edit-op-description"]

  [Teardown]  logout

Mikko can see new operation description
  Mikko logs in
  Open application  application-papplication  753-416-25-30
  Wait until  Element text should be  xpath=//div[@id='application-info-tab']//span[@data-test-id='op-description']  Talo A
  [Teardown]  logout
