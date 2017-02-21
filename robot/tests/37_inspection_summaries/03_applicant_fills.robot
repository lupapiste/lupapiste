*** Settings ***

Documentation   Applicant fills inspection summary
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        inspection_summary_resources.robot
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
  Fetch verdict
  Logout

View summary targets as applicant
  Pena logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  inspectionSummaries
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible by test id  target-0
  Element should contain  //tr[@data-test-id="target-0"]/td[@class="target-name"]  AA
  Element should contain  //tr[@data-test-id="target-1"]/td[@class="target-name"]  BB
  Element should contain  //tr[@data-test-id="target-2"]/td[@class="target-name"]  CC

Applicant can upload attachment to target
  Element should be visible  //tr[@data-test-id="target-1"]//label[@data-test-id='upload-link']
  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  0
  Upload with hidden input  tr[data-test-id='target-1'] input[data-test-id='upload-link-input']  ${PNG_TESTFILE_PATH}
  Sleep  1s
  Wait until  Xpath should match x times  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment']  1
  Wait until  Element text should be  //tr[@data-test-id="target-1"]//div[@data-test-id='target-row-attachment'][1]//a  ${PNG_TESTFILE_NAME}


Mark target as finished
  Click by test id  change-status-link-1
  Wait until  Element should be visible  //tr[@data-test-id="target-1"]//td[@class="target-finished"]//i
  Click by test id  change-status-link-2
  Wait until  Element should be visible  //tr[@data-test-id="target-2"]//td[@class="target-finished"]//i
  Test id text is  change-status-link-1  Kumoa merkintä
  Test id text is  change-status-link-2  Kumoa merkintä
  Click by test id  change-status-link-1
  Wait until  Element should not be visible  //tr[@data-test-id="target-1"]//td[@class="target-finished"]//i
  Test id text is  change-status-link-1  Merkitse tehdyksi
  Logout

Authority can not edit target name for finished target
  Jarvenpaa authority logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  inspectionSummaries
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible  //tr[@data-test-id="target-2"]//td[@class="target-finished"]//i
  Element should not be visible by test id  edit-target-2
  Logout
