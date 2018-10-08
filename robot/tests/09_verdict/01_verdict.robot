*** Settings ***

Documentation   Application gets verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
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

Sonja fetches verdict from municipality KuntaGML service
  Open tab  verdict
  Fetch verdict
  Check verdict row  0  §1 myönnetty  1.9.2013  elmo viranomainen
  Check verdict row  1  myönnetty  1.9.2013  johtava viranomainen

Sonja opens the first verdict
  Open verdict
  No verdict attachments
  Wait test id visible  new-appeal
  Click back

Sonja opens the second verdict
  Open verdict  1
  Verdict attachment count  2
  Wait test id visible  new-appeal
  Click back

Application summary tab is visible
  Element should be visible  jquery=a[data-test-id=application-open-applicationSummary-tab]

Application info tab is hidden
  Page should not contain element  jquery=a[data-test-id=application-open-info-tab]

Check task counts
  Open tab  tasks
  # 3+3+3 tasks from backend
  Task count is  task-katselmus  3
  Foreman count is  3
  Task count is  task-lupamaarays  3

Sonja creates verdict and adds comment
  Go to give new legacy verdict
  Input legacy verdict  123567890  Kaarina Krysp III  Ehdollinen  1.5.2018
  Comment verdict  Myönnetään...

Sonja adds attachment to verdict
  Pate upload  0  ${TXT_TESTFILE_PATH}  Päätösote  Description
  Pate batch ready

Add review
  Add legacy review  0  Lopullinen loppukatselmus  loppukatselmus

Add foreman
  Add legacy foreman  0  Vastaava työnjohtaja

Add condition
  Add legacy condition  0  Bajamajoja oltava riittvästi

Return to application and check old task counts
  Click back
  Open tab  tasks
  Task count is  task-katselmus  3
  Foreman count is  3
  Task count is  task-lupamaarays  3

# Verdict has tasks
#   Open tab  verdict
#   Page Should Not Contain Element  xpath=//div[@data-test-id="given-verdict-id-2-content"]//div[@data-bind="ltext: 'verdict.lupamaaraykset.missing'"]
#   Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//span[@data-bind="text: $data.tarkastuksenTaiKatselmuksenNimi"]  Lopullinen loppukatselmus
#   Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//ul[@data-bind="foreach: lupamaaraykset.muutMaaraykset"]/li  Bajamajoja oltava riittävästi
#   Wait until  Element Text Should Be  xpath=//div[@data-test-id="given-verdict-id-2-content"]//span[@data-bind="text: lupamaaraykset.vaaditutTyonjohtajat"]  TJ0

Sonja publishes verdict
  Open tab  verdict
  Open verdict  0
  Publish verdict

Return to application and check updated task counts
  Click back
  Open tab  tasks
  Task count is  task-katselmus  4
  Foreman count is  4
  Task count is  task-lupamaarays  4

Add and delete verdict draft
  Open tab  verdict
  Verdict count is  3
  Go to give new legacy verdict
  Click back
  Verdict count is  4
  Delete verdict  0
  Verdict count is  3

Correct tab opening elements are visible
  Scroll to top
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
  Element should be visible  //div[@id='application-applicationSummary-tab']//section[@id='accordion-application-summary-statements']//div[contains(@class, 'accordion_content')]

Stamping page opens, backing system verdict details can be seen
  Open tab  attachments
  Click by test id  stamp-attachments
  Wait Until  Element should be visible  stamping-container
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-kuntalupatunnus"]  2013-01
  Should not be empty  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-section"]
  Textfield should contain  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-section"]  §
  [Teardown]  Logout

Mikko sees that the application has verdicts
  Mikko logs in
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//i[contains(@class, 'lupicon-star')]
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Verdict count is  3

*** Keywords ***

Comment verdict
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Wait Until  Element should be enabled  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Click element  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation
