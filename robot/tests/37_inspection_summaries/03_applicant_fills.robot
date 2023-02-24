*** Settings ***

Documentation   Applicant fills inspection summary
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        inspection_summary_resources.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py


*** Test Cases ***

Create template setup in auth admin
  Jarvenpaa admin logs in
  Create basic template setup
  Bind template to operation as default  Uusi pohja 1  kerrostalo-rivitalo
  Logout

Pena wants to build a block of flats
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Skyscraper${secs}
  Create application the fast way  ${appname}  186-401-1-2111  kerrostalo-rivitalo
  Submit application
  Logout

Authority gives a verdict
  Jarvenpaa authority logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  verdict
  Sleep  1s
  Fetch verdict
  Logout

View summary targets as applicant
  Pena logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  attachments
  Wait Until  Xpath Should Match X Times  //div[@id='application-attachments-tab']//tr[@data-test-type='katselmukset_ja_tarkastukset.tarkastusasiakirja']  0
  Open tab  inspectionSummaries
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible by test id  target-0
  Element should contain  //tr[@data-test-id="target-0"]/td[contains(@class, 'target-name')]  AA
  Element should contain  //tr[@data-test-id="target-1"]/td[contains(@class, 'target-name')]  BB
  Element should contain  //tr[@data-test-id="target-2"]/td[contains(@class, 'target-name')]  CC

Applicant can upload attachment to target
  Element should be visible  //tr[@data-test-id="target-1"]//label[@data-test-id='upload-link']
  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  0
  Upload with hidden input  tr[data-test-id='target-1'] input[data-test-id='upload-link-input']  ${PNG_TESTFILE_PATH}
  Sleep  1s
  Wait until  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  1
  Wait until  Element text should be  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a  ${PNG_CONVERTED_NAME}
  Wait until  Element should be visible  //tr[@data-test-id="target-1"]//label[@data-test-id='upload-link']
  # Delete button is visible
  Wait until  Element should be visible  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a[@data-test-id='delete-attachment-link']

Attachment can be deleted
  Open tab  attachments
  Wait Until  Xpath Should Match X Times  //div[@id='application-attachments-tab']//tr[@data-test-type='katselmukset_ja_tarkastukset.tarkastusasiakirja']  1
  Open tab  inspectionSummaries
  Wait until  Element should be visible  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a[@data-test-id='delete-attachment-link']
  Click element  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a[@data-test-id='delete-attachment-link']
  Confirm yes no dialog
  Positive indicator should be visible
  Wait until  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  0
  Open tab  attachments
  Wait Until  Xpath Should Match X Times  //div[@id='application-attachments-tab']//tr[@data-test-type='katselmukset_ja_tarkastukset.tarkastusasiakirja']  0

Upload attachment again
  Open tab  inspectionSummaries
  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  0
  Upload with hidden input  tr[data-test-id='target-1'] input[data-test-id='upload-link-input']  ${PNG_TESTFILE_PATH}
  Sleep  1s
  Wait until  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  1

Attachment can be found from attachment listing
  Open tab  attachments
  Wait Until  Xpath Should Match X Times  //div[@id='application-attachments-tab']//tr[@data-test-type='katselmukset_ja_tarkastukset.tarkastusasiakirja']  1
  Page should contain  ${PNG_CONVERTED_NAME}

Attachment belongs to operation and is construction time
  Open attachment details  katselmukset_ja_tarkastukset.tarkastusasiakirja
  Wait until  Autocomplete selection by test id contains  attachment-group-autocomplete  Asuinkerrostalon tai rivitalon rakentaminen
  Return to application

Mark target as finished
  Open tab  inspectionSummaries
  Click by test id  change-status-link-1
  Wait until  Element should be visible  //tr[@data-test-id="target-1"]//td[contains(@class, 'target-finished')]//i
  Click by test id  change-status-link-2
  Wait until  Element should be visible  //tr[@data-test-id="target-2"]//td[contains(@class, 'target-finished')]//i

Attachment can't be added nor deleted
  Wait until  Element text should be  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a  ${PNG_CONVERTED_NAME}
  Element should not be visible  //tr[@data-test-id="target-1"]//label[@data-test-id='upload-link']
  Element should not be visible  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a[@data-test-id='delete-attachment-link']
  # Previous target without attachment also doesn't have upload link
  Element should not be visible  //tr[@data-test-id="target-2"]//label[@data-test-id='upload-link']

Undo finished marking
  Test id text is  change-status-link-1  Kumoa merkintä
  Test id text is  change-status-link-2  Kumoa merkintä
  Click by test id  change-status-link-1
  Wait until  Element should not be visible  //tr[@data-test-id="target-1"]//td[contains(@class, 'target-finished')]//i
  Test id text is  change-status-link-1  Merkitse tehdyksi
  # Attachment actions are visible again
  Element should be visible  //tr[@data-test-id="target-1"]//label[@data-test-id='upload-link']
  Element should be visible  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a[@data-test-id='delete-attachment-link']
  Logout

Authority can not edit target name for finished target
  Jarvenpaa authority logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  inspectionSummaries
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible  //tr[@data-test-id="target-2"]//td[contains(@class, 'target-finished')]//i
  Element should not be visible by test id  edit-target-2
  Element should not be visible  //tr[@data-test-id="target-2"]//label[@data-test-id='upload-link']

Authority can unmark target but not mark it
  Click by test id  change-status-link-2
  Wait until  Element should not be visible by test id  change-status-link-2
