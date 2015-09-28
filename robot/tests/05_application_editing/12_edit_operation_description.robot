*** Settings ***

Documentation  Mikko set description for an operation
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753-416-25-30  kerrostalo-rivitalo

Mikko edits operation description
  Open application  application-papplication  753-416-25-30
  Wait until element is visible  jquery=div#application-info-tab button[data-test-id=edit-op-description-uusiRakennus]
  Mouse Down  jquery=div#application-info-tab [data-test-id=edit-op-description-uusiRakennus]
  Input text by test id  op-description-editor-uusiRakennus  Talo A
  # Close the input bubble
  Press Key  jquery=div#application-info-tab input[data-test-id=op-description-editor-uusiRakennus]  \\13
  Wait for jQuery
  Wait until  Page should contain  Tallennettu
  Wait until element is not visible  jquery=div#application-info-tab input[data-test-id=op-description-editor-uusiRakennus]


Mikko can see new operation description
  Reload Page
  Wait until  Element text should be  xpath=//div[@id='application-info-tab']//span[@data-test-id='op-description-uusiRakennus']  - Talo A
  [Teardown]  logout
