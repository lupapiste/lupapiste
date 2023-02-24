*** Settings ***

Documentation   Authority edits summary targets
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
Resource        inspection_summary_resources.robot


*** Test Cases ***

Create template setup in auth admin
  Jarvenpaa admin logs in
  Create basic template setup
  Logout

Pena wants to build a block of flats
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Skyscraper${secs}
  Create application the fast way  ${appname}  186-401-1-2111  kerrostalo-rivitalo
  Submit application
  [Teardown]  Logout

Authority gives a verdict
  Jarvenpaa authority logs in
  Open application  ${appname}  186-401-1-2111
  Open tab  verdict
  Fetch verdict

Create a new inspection summary
  Open tab  inspectionSummaries
  Click by test id  open-create-summary-bubble
  Wait test id visible  create-summary-button
  Test id disabled  create-summary-button
  Wait until  Select From List by label  xpath=//select[@data-test-id="templates-select"]  Uusi pohja 1
  Test id disabled  create-summary-button
  Wait until  Select From List by label  xpath=//select[@data-test-id="operations-select"]  (Asuinkerrostalon tai rivitalon rakentaminen)
  Test id enabled  create-summary-button
  Click by test id  create-summary-button
  Positive indicator should be visible

View summary targets
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible by test id  target-0
  Element should contain  //tr[@data-test-id="target-0"]/td[contains(@class, 'target-name')]  AA
  Element should contain  //tr[@data-test-id="target-1"]/td[contains(@class, 'target-name')]  BB
  Element should contain  //tr[@data-test-id="target-2"]/td[contains(@class, 'target-name')]  CC

Edit targets
  Edit target name on an existing inspection summary  1  Perustukset valettu
  Element should contain  //tr[@data-test-id="target-0"]/td[contains(@class, 'target-name')]  AA
  Element should contain  //tr[@data-test-id="target-2"]/td[contains(@class, 'target-name')]  CC

Remove a target
  Click by test id  remove-icon-0
  Confirm yes no dialog
  Element should contain  //tr[@data-test-id="target-1"]/td[contains(@class, 'target-name')]  CC
  Element should not be visible by test id  target-2

Add new target
  Add a new target on an existing inspection summary  2  Postilaatikko hankittuna
