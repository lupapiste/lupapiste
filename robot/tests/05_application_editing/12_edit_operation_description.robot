*** Settings ***

Documentation  Mikko set description for an operation
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753-416-25-30  kerrostalo-rivitalo

Mikko edits operation description
  Open application  application-papplication  753-416-25-30
  Edit operation description  uusiRakennus  Talo A

Mikko can see new operation description
  Reload Page
  Operation description is  uusiRakennus  Talo A
  #Wait until  Element text should be  xpath=//div[@id='application-info-tab']//span[@data-test-id='op-description-uusiRakennus']  - Talo A
  [Teardown]  logout
