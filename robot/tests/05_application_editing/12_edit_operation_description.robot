*** Settings ***

Documentation  Mikko set description for an operation
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753-416-25-30  kerrostalo-rivitalo

Mikko edits operation description
  Open application  application-papplication  753-416-25-30
  Wait and click  xpath=//div[@id='application-info-tab']//button[@data-test-id='edit-op-description']
  Input text by test id  op-description-editor  Talo A
  Wait for jQuery
  Wait until  Page should contain  Tallennettu

Mikko can see new operation description
  Reload Page
  Wait until  Element text should be  xpath=//div[@id='application-info-tab']//span[@data-test-id='op-description']  - Talo A
  [Teardown]  logout
