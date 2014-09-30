*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753  753-416-25-30  asuinrakennus

Mikko edits operation description
  Open application  application-papplication  753-416-25-30
  Wait and click  xpath=//span[@data-test-id='edit-op-description']
  Input text by test id  op-description-editor  Talo A

Mikko can see new operation description
  logout
  Mikko logs in
  Open application  application-papplication  753-416-25-30
  Wait until  Element text should be  xpath=//span[@data-test-id='op-description']  Talo A
