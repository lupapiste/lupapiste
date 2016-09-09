*** Settings ***

Documentation   Application gets verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py

*** Test Cases ***

Mikko wants to build Olutteltta
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Olutteltta${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

The verdict tab is not visible
  Element should not be visible  application-verdict-tab

Mikko submits application & goes for lunch
  Submit application
  [Teardown]  logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  Page Should Contain Element  //div[@data-test-id="given-verdict-id-0-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]
  Page Should Not Contain Element  //div[@data-test-id="given-verdict-id-1-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]

Check task counts
  Open tab  tasks
  # 3+3+3 tasks from backend
  Task count is  task-katselmus  3
  Foreman count is  3
  Task count is  task-lupamaarays  3

Sonja creates verdict with adds comment
  Go to give new verdict
  Title Should Be  ${appname} - Lupapiste
  Input verdict  123567890  6  01.05.2018  01.06.2018  Kaarina Krysp III
  Comment verdict  Myönnetään...

Sonja adds attachment to verdict
  Add attachment  verdict  ${TXT_TESTFILE_PATH}  ${EMPTY}  ${EMPTY}
  Wait test id visible  targetted-attachments-table

Add katselmus
  Create task  task-katselmus  Lopullinen loppukatselmus  loppukatselmus
  # One on this verdict screen (and 3 hidden in tasks tab)
  Task count is  task-katselmus  1

Add foreman
  Create task  task-vaadittu-tyonjohtaja  TJ0
  # One on this verdict screen (and 3 hidden in tasks tab)
  Task count is  task-vaadittu-tyonjohtaja  1

Add other task
  Create task  task-lupamaarays  Bajamajoja oltava riittävästi
  # One on this verdict screen (and 3 hidden in tasks tab)
  Task count is  task-lupamaarays  1

Return to application and check total task numbers
  Click by test id  return-from-verdict
  Open tab  tasks
  Task count is  task-katselmus  4
  Foreman count is  4
  Task count is  task-lupamaarays  4

Verdict has tasks
  Open tab  verdict
  Page Should Not Contain Element  xpath=//div[@data-test-id="given-verdict-id-2-content"]//div[@data-bind="ltext: 'verdict.lupamaaraukset.missing'"]
  Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//span[@data-bind="text: $data.tarkastuksenTaiKatselmuksenNimi"]  Lopullinen loppukatselmus
  Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//ul[@data-bind="foreach: lupamaaraykset.muutMaaraykset"]/li  Bajamajoja oltava riittävästi
  Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//span[@data-bind="text: lupamaaraykset.vaaditutTyonjohtajat"]  TJ0

Sonja publishes verdict
  Click enabled by test id  edit-verdict
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  verdictGiven
  Verdict is given  123567890  2
  Wait Until  Element text should be  xpath=//div[@data-test-id='given-verdict-id-2-content']//span[@data-bind='dateString: paivamaarat.anto']  1.5.2018

Add and delete verdict
  Verdict count is  3
  Go to give new verdict
  Verdict count is  4
  Click enabled by test id  delete-verdict
  Confirm  dynamic-yes-no-confirm-dialog
  Verdict count is  3

Correct tab opening elements are visible
  Element should not be visible  //*[@data-test-id='application-open-info-tab']
  Element should be visible  //*[@data-test-id='application-open-parties-tab']
  Element should be visible  //*[@data-test-id='application-open-tasks-tab']
  Element should be visible  //*[@data-test-id='application-open-attachments-tab']
  Element should not be visible  //*[@data-test-id='application-open-requiredFieldSummary-tab']
  Element should not be visible  //*[@data-test-id='application-open-statement-tab']
  Element should be visible  //*[@data-test-id='application-open-verdict-tab']
  Element should be visible  //*[@data-test-id='application-open-applicationSummary-tab']

Accordions in the Application Summary tab are closed
  Open tab  applicationSummary
  Xpath Should Match X Times  //div[@id='application-applicationSummary-tab']//section[contains(@class, 'accordion')]//div[@data-accordion-state='closed']  7
  Xpath Should Match X Times  //div[@id='application-applicationSummary-tab']//section[contains(@class, 'accordion')]//div[@data-accordion-state='open']  0
  Click by test id  accordion-application-summary-statements-header
  Xpath Should Match X Times  //div[@id='application-applicationSummary-tab']//section[contains(@class, 'accordion')]//div[@data-accordion-state='closed']  6
  Xpath Should Match X Times  //div[@id='application-applicationSummary-tab']//section[contains(@class, 'accordion')]//div[@data-accordion-state='open']  1
  Element should be visible  //div[@id='application-applicationSummary-tab']//section[@id='accordion-application-summary-statements']//div[@class='accordion_content']

Stamping page opens, verdict details can be seen
  Open tab  attachments
  Click by test id  stamp-attachments
  Wait Until  Element should be visible  stamping-container
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-kuntalupatunnus"]  2013-01
  Page should contain element  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@id="stamp-info-include-buildings"]
  Should not be empty  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-section"]
  [Teardown]  Logout

Mikko sees that the application has verdicts
  Mikko logs in
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[@class='lupicon-star']
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Verdict is given  2013-01  0
  Verdict is given  123567890  2

Mikko tries to sign the verdict with wrong password
  Click Element  xpath=//div[@data-test-id='given-verdict-id-2-content']//button[@data-test-id='sign-verdict-button']
  Wait Until Element Is Visible  xpath=//input[@data-test-id='sign-verdict-password']
  Input Text  xpath=//div[@id='dialog-sign-verdict']//input[@data-test-id='sign-verdict-password']  wrong_password
  Click Element  xpath=//div[@id='dialog-sign-verdict']//button[@data-test-id='do-sign-verdict']
  Wait Until  Element should be visible  xpath=//div[@id='dialog-sign-verdict']//div[@data-test-id='verdict-signature-error-message']
  Click Element  xpath=//div[@id='dialog-sign-verdict']//p[@data-test-id='verdict-signature-close-dialog']

Mikko signs the verdict
  Wait until  Element should be visible  xpath=//div[@data-test-id='given-verdict-id-2-content']//div[@data-test-id='verdict-signature-ui']
  Element should not be visible  xpath=//div[@data-test-id='given-verdict-id-0-content']//div[@data-test-id='verdict-signature-ui']
  Click Element  xpath=//div[@data-test-id='given-verdict-id-2-content']//button[@data-test-id='sign-verdict-button']
  Wait Until Element Is Visible  xpath=//div[@id='dialog-sign-verdict']//input[@data-test-id='sign-verdict-password']
  Input Text  xpath=//div[@id='dialog-sign-verdict']//input[@data-test-id='sign-verdict-password']  mikko123
  Click Element  xpath=//div[@id='dialog-sign-verdict']//button[@data-test-id='do-sign-verdict']
  Wait Until  Element should be visible  xpath=//div[@data-test-id='given-verdict-id-2-content']//div[@data-test-id='verdict-signature-listing']
  Element should not be visible  xpath=//div[@data-test-id='given-verdict-id-2-content']//div[@data-test-id='verdict-signature-ui']
  Element should Contain  xpath=//div[@data-test-id='given-verdict-id-2-content']//div[@data-test-id='verdict-signature-listing']  Intonen Mikko
  Element should Contain  xpath=//div[@data-test-id='given-verdict-id-2-content']//div[@data-test-id='verdict-signature-listing']  ${CURRENT_DATE}

*** Keywords ***

Verdict count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id="application-verdict-tab"]//h2[@class="application_section_header"]  ${amount}

Comment verdict
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Wait Until  Element should be enabled  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Click element  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

Create task
  [Arguments]  ${taskType}  ${message}  ${taskSubtype}=
  Click enabled by test id  verdict-new-task
  Wait until  Element should be visible  dialog-create-task
  Wait until  Select From List By Value  choose-task-type   ${taskType}
  Run Keyword If  $taskSubtype  Wait until  Element should be visible  choose-task-subtype
  Run Keyword If  $taskSubtype  Select From List By Value  choose-task-subtype   ${taskSubtype}
  Input text  create-task-name  ${message}
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task
