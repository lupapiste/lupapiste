*** Settings ***

Documentation   Application gets verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        inspection_summary_resources.robot

*** Test Cases ***

Create template setup in auth admin
  Jarvenpaa admin logs in
  Go to page  applications
  Add new template  Uusi pohja 1  AA\nBB\nCC\n  AA\nBB\nCC  0
  Add new template  Uusi pohja 2  AA\nDD\n\nCC\n  AA\nDD\nCC  1
  Go to page  operations
  Select From List by test id and index  select-inspection-summary-template-kerrostalo-rivitalo  1
  Wait Until   Positive indicator should be visible
  Select From List by test id and index  select-inspection-summary-template-kerrostalo-rivitalo  0
  Wait Until   Positive indicator should be visible
  Select From List by test id and index  select-inspection-summary-template-kerrostalo-rivitalo  1
  Wait Until   Positive indicator should be visible
  Go to page  users
  Authority-admin front page should be open
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
  Select From List by test id and index  summaries-select  1
  Wait until  Element should be visible by test id  target-name-AA