*** Settings ***

Documentation  Sonja should see only applications from Sipoo
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates application to Jarvenpaa
  # Jarvenpaa has archive
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyid}  186-416-25-30
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyid}  kerrostalo-rivitalo

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
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Muu liite  Asuinkerrostalon tai rivitalon rakentaminen - Talo A
  Application state should be  draft

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Title Should Be  ${appname} - Lupapiste

Mikko can change related operation
  Element should be visible  xpath=//select[@data-test-id="attachment-operation-select"]
  Select From List  xpath=//select[@data-test-id='attachment-operation-select']  Osapuolet
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select From List  xpath=//select[@data-test-id='attachment-operation-select']  Yleisesti hankkeeseen
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select From List  xpath=//select[@data-test-id='attachment-operation-select']  Rakennuspaikka
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select From List  xpath=//select[@data-test-id='attachment-operation-select']  Muun rakennuksen rakentaminen - Talo B
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible

Mikko can change drawing number
  Element should be visible  xpath=//input[@data-test-id='attachment-drawing-number']
  Input text by test id  attachment-drawing-number  piir1.0
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible

Mikko can change contents
  Element should be visible  xpath=//input[@data-test-id='attachment-contents-input']
  Input text by test id  attachment-contents-input  PuuCee
  Positive indicator icon should be visible

Mikko goes to attachments tab
  Go Back

Mikko sees that contents metadata is visible in attachments list
  Wait Until  Element Text Should Be  xpath=//div[@id="application-attachments-tab"]//span[@data-test-id="attachment-contents"]  PuuCee

Mikko sees that attachments are grouped by operations
  Wait Until  Xpath Should Match X Times  //div[@id="application-attachments-tab"]//rollup[@data-test-level="accordion-level-0"]  3

Mikko sees that his attachment is grouped by "Muun rakennuksen rakentaminen - Talo B" operation
  Element Text Should Be  xpath=(//div[@id="application-attachments-tab"]//rollup[@data-test-level="accordion-level-0"])[last()]//span[@class="rollup-status__text"]  MUUN RAKENNUKSEN RAKENTAMINEN - TALO B

Mikko opens attachment and sees that attachment label metadata is set
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0
  Page should contain  Muun rakennuksen rakentaminen
  Textfield value should be  xpath=//input[@data-test-id='attachment-drawing-number']  piir1.0
  Textfield Value Should Be  xpath=//input[@data-test-id='attachment-contents-input']  PuuCee
  Go Back
  Tab should be visible  attachments

Mikko submits
  Submit application
  [Teardown]  Logout

Authority opens attachment details
  # Jarvenpaa has archive
  Jarvenpaa authority logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Open attachment details  muut.muu
  Assert file latest version  ${PNG_TESTFILE_NAME}  1.0

Open archive metadata editor
  Click enabled by test id  show-attachment-tos-metadata
  Click by test id  edit-metadata
  Element Should Be Disabled  xpath=//section[@id="attachment"]//button[@data-test-id='save-metadata']

Cancel editing
  Click by test id  cancel-metadata-edit
  Logout

No frontend errors
  [Tags]  non-roboto-proof
  There are no frontend errors
