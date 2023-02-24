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
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application
  Wait until  Page should contain element  xpath=//section[@data-doc-type="uusiRakennus"][2]

Mikko edits operation B description
  Open accordions  info
  Edit operation description  uusiRakennus  Talo B  2
  Wait for jQuery

Mikko adds txt attachment without comment
  [Tags]  attachments
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Muu liite  Muu liite  Talo A - Asuinkerrostalon tai rivitalon rakentaminen
  Application state should be  draft

Mikko opens attachment details
  [Tags]  attachments
  Open attachment details  muut.muu
  Assert file latest version  ${TXT_CONVERTED_NAME}  1.0
  Title Should Be  ${appname} - Lupapiste

Mikko can change related operation
  Element should be visible  jquery=[data-test-id=attachment-group-autocomplete]
  Click element  jquery=[data-test-id=attachment-group-autocomplete] .tag-remove
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select from autocomplete  [data-test-id=attachment-group-autocomplete]  Osapuolet
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select from autocomplete  [data-test-id=attachment-group-autocomplete]  Rakennuspaikka
  Wait until  Positive indicator icon should be visible
  Wait until  Positive indicator icon should not be visible
  Select from autocomplete  [data-test-id=attachment-group-autocomplete]  Talo B - Muun rakennuksen rakentaminen
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
  Element Text Should Be  xpath=(//div[@id="application-attachments-tab"]//rollup[@data-test-level="accordion-level-0"])[last()]//span[contains(@class, 'rollup-status__text')]  MUUN RAKENNUKSEN RAKENTAMINEN - Talo B

Mikko opens attachment and sees that attachment label metadata is set
  Open attachment details  muut.muu
  Assert file latest version  ${TXT_CONVERTED_NAME}  1.0
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
  Assert file latest version  ${TXT_CONVERTED_NAME}  1.0

Authority rotates PDF
  Click by test id  file-preview
  Wait until  Element should not be visible  jquery=iframe#file-preview-iframe[src="/lp-static/img/ajax-loader.gif"]
  Click element  jquery=i.lupicon-rotate-left
  Wait until  Element should be visible  jquery=iframe#file-preview-iframe[src="/lp-static/img/ajax-loader.gif"]
  Wait until  Element should not be visible  jquery=iframe#file-preview-iframe[src="/lp-static/img/ajax-loader.gif"]

Open archive metadata editor
  Click enabled by test id  show-attachment-tos-metadata
  Click by test id  edit-metadata
  Element Should Be Disabled  xpath=//section[@id="attachment"]//button[@data-test-id='save-metadata']

Cancel editing
  Click by test id  cancel-metadata-edit
  Logout

No frontend errors
  There are no frontend errors
