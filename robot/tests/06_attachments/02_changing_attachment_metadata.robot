*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Mikko logs in
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

Mikko edits operation description
  Open accordions  info
  Edit operation description  uusiRakennus  Talo A

Mikko adds an operation
  Set animations off
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application
  Wait until  Page should contain element  xpath=//section[@data-doc-type="uusiRakennus"][2]

Mikko edits operation B description
  Open accordions  info
  Edit operation description  uusiRakennus  Talo B  2
  Wait for jQuery

Mikko adds txt attachment without comment
  [Tags]  attachments
  Open tab  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen - Talo A
  Application state should be  draft
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PNG_TESTFILE_NAME}')]

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Title Should Be  ${appname} - Lupapiste

Mikko can change related operation
  Element should be visible  xpath=//select[@data-test-id="attachment-operation-select"]
  Select From List  xpath=//select[@data-test-id='attachment-operation-select']  Muun rakennuksen rakentaminen - Talo B
  Sleep  1
  Negative indicator icon should not be visible

Mikko can change size
  Element should be visible  xpath=//select[@data-test-id='attachment-size-select']
  Select From List  xpath=//select[@data-test-id='attachment-size-select']  B0
  Sleep  1
  Negative indicator icon should not be visible

Mikko can change scale
  Element should be visible  xpath=//select[@data-test-id='attachment-scale-select']
  Select From List  xpath=//select[@data-test-id='attachment-scale-select']  1:200
  Sleep  1
  Negative indicator icon should not be visible

Mikko can change contents
  Element should be visible  xpath=//input[@data-test-id='attachment-contents-input']
  Input text by test id  attachment-contents-input  PuuCee
  Positive indicator icon should be visible

Mikko goes to fresh attachments tab
  Go Back
  Reload Page

Mikko sees that contents metadata is visible in attachments list
  Wait Until  Element Text Should Be  xpath=//div[@id="application-attachments-tab"]//span[@data-test-id="attachment-contents"]  PuuCee

Mikko sees that attachments are grouped by operations
  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//tr[@class="attachment-group-header"]  3

Mikko sees that his attachment is grouped by "Muun rakennuksen rakentaminen - Talo B" operation
  Element Text Should Be  xpath=(//div[@id="application-attachments-tab"]//tr[@class="attachment-group-header"])[last()]//td[@data-test-id="attachment-group-header-text"]  Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus) - Talo B

Mikko opens attachment and sees that attachment label metadata is set
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Page should contain  Muun rakennuksen rakentaminen
  Page should contain  B0
  Textfield Value Should Be  xpath=//input[@data-test-id='attachment-contents-input']  PuuCee
  Page should contain  1:200
