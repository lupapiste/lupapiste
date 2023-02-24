*** Settings ***

Documentation  (Building) identifier can be added to uusiRakennus documents
Resource       ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  Create application the fast way  application-papplication  753-416-25-30  kerrostalo-rivitalo

Mikko sets identifier for uusiRakennus document
  Open application  application-papplication  753-416-25-30
  Input building identifier  uusiRakennus  ABC
  Wait until  Element should contain  //section[@id='application']//section[@data-doc-type='uusiRakennus']//span[@data-test-id='uusiRakennus-accordion-description-text']  ABC
  [Teardown]  Logout
