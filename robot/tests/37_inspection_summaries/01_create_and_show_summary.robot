*** Settings ***

Documentation   Auth admin creates inspection summary templates
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        inspection_summary_resources.robot
Resource        ../39_pate/pate_resource.robot

*** Test Cases ***

Create template setup in auth admin
  Jarvenpaa admin logs in
  Go to page  applications
  Add new template  Uusi pohja 1  AA\nBB\nCC\n  AA\nBB\nCC  0
  Add new template  Uusi pohja 2  AA\nDD\n\nCC\n  AA\nDD\nCC  1
  Go to page  operations
  Select From List by label   xpath=//select[@data-test-id="select-inspection-summary-template-kerrostalo-rivitalo"]  Uusi pohja 1
  Positive indicator should be visible
  Positive indicator should not be visible
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

Inspection summary should be created automatically
  Open tab  inspectionSummaries
  Wait until  Select From List by label  xpath=//select[@data-test-id="summaries-select"]  Uusi pohja 1 -
  Wait until  Element should be visible by test id  target-0
  Element should contain  //tr[@data-test-id="target-0"]/td[contains(@class, 'target-name')]  AA
  Element should contain  //tr[@data-test-id="target-1"]/td[contains(@class, 'target-name')]  BB
  Element should contain  //tr[@data-test-id="target-2"]/td[contains(@class, 'target-name')]  CC
