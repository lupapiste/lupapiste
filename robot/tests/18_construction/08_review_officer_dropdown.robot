*** Settings ***

Documentation   Review officer dropdown and list operation
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        task_resource.robot
Resource        keywords.robot
Resource        ../39_pate/pate_resource.robot
Resource        ../13_statements/statement_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Mikko prepares the application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${secs}
  Set Suite Variable  ${appname}  Taskitesti-${secs}
  Set Suite Variable  ${propertyId}  753-416-18-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Submit application
  [Teardown]  Logout

Sonja sends the application
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary
  Input text  bulletin-op-description  damdidam
  Click by test id  approve-application-summaryTab
  Wait until  Application state should be  sent

Sonja gives the application a verdict
  Open tab  verdict
  Click by test id  new-legacy-verdict
  Click by test id  toggle-all
  Wait until  Element should be enabled  kuntalupatunnus
  Input text  kuntalupatunnus  Kuntalupatunnus
  Pate autocomplete select  verdict-code  Ehdollinen
  Scroll to test id  anto
  Input text  anto  12.01.2019
  Input text  verdict-text  Example text

Sonja publishes the verdict
  Scroll to test id  publish-verdict
  Click enabled by test id  publish-verdict
  Click by test id  confirm-yes
  Scroll to test id  back
  Click by test id  back

Sonja adds a review task for the application
  Open tab  tasks
  Click by test id  application-new-task
  Select from list by label  choose-task-type  Kokoukset, katselmukset ja tarkastukset
  Select from list by label  choose-task-subtype  Pohjakatselmus
  Input text  create-task-name  Pohjanoteeraus
  Click by test id  create-task-save

Sonja sets the review officer and checks it is displayed in the previous view
  Wait until  Element should be visible by test id  katselmus.pitaja
  Input text by test id  katselmus.pitaja  Kati Katselmoija
  # Give the value time to reach the database
  Sleep  1s
  Click by test id  back-to-application-from-task
  Scroll to bottom
  Wait until  Page should contain  Kati Katselmoija
  [Teardown]  Logout

Luukas checks that the review officer field is disabled for him
  Luukas logs in
  Open application  ${appname}  ${propertyId}
  Open review  0
  Element should be disabled  xpath=//*[@data-test-id='katselmus.pitaja']
  [Teardown]  Logout

Authority admin enables review officer list
  Sipoo logs in
  Go to page  reviews
  Wait test id visible  review-officer-toggle-label
  Click by test id  review-officer-toggle-label
  Create review officer  Kalle Katselmoija  koodi-0
  Create review officer  Katariina Katselmoija  koodi-1
  [Teardown]  Logout

Sonja chooses a review officer from the dropdown
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open review  0
  Test id select texts are  katselmus.pitaja  - Valitse -  Kalle Katselmoija  Katariina Katselmoija
  Select from test id by text  'katselmus.pitaja'  Katariina Katselmoija

Sonja sees that the review officer is displayed correctly on the task page
  Click by test id  back-to-application-from-task
  Wait until  Scroll to test id  application-new-task
  Page should contain  Katariina Katselmoija

Sonja finishes the review
  Open review  0
  Input text by test id  katselmus.pitoPvm  01.01.2019
  Select from test id by text  'katselmus.tila'  Lopullinen
  Click enabled by test id  review-done
  Wait until  Click by test id  confirm-yes
  Wait until  Confirm  dynamic-ok-confirm-dialog

Sonja checks that the file was generated
  Wait until  Element text should be  xpath=//table[@data-test-id="targetted-attachments-table"]//tr/td  Katselmuksen pöytäkirja

Sonja checks that the review officer field is now disabled
  Element should be disabled  xpath=//*[@data-test-id='katselmus.pitaja']

Sonja checks that the name is displayed in the previous view
  Click by test id  back-to-application-from-task
  Wait until  Page should contain  Katariina Katselmoija
