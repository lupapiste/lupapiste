*** Settings ***

Documentation   Application gets tasks based on verdict
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        task_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko prepares the application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Taskitesti-${secs}
  Set Suite Variable  ${propertyId}  753-416-18-1
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Submit application
  Set Suite Variable  ${appname-ya}  Taskitesti-YA-${secs}
  Create application the fast way  ${appname-ya}  ${propertyId}  ya-katulupa-vesi-ja-viemarityot
  Submit application
  Logout

Sonja gives verdict
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110

Rakentaminen tab opens
  Open tab  tasks

Rakentaminen tab contains 3 foreman tasks
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  3

Rakentaminen tab contains 3 review tasks (katselmus)
  Wait until  Xpath Should Match X Times  //div[@id='application-tasks-tab']//div[@class="review-tasks"]//tbody/tr  3

Rakentaminen tab contains 3 requirements (lupamaarays)
  Wait until  Xpath Should Match X Times  //div[@id='application-tasks-tab']//table[@class="tasks"]//tbody/tr  3

Katselmukset
  Wait Until  Page should contain  Kokoukset, katselmukset ja tarkastukset
  Task count is  task-katselmus  3

Tyonjohtajat
  Wait until  Page should contain  Vaaditut työnjohtajat
  Wait until  Xpath Should Match X Times  //div[@data-test-id="tasks-foreman"]//tbody/tr  3

Muut lupamaaraykset
  Wait until  Page should contain  Muut lupamääräykset
  Task count is  task-lupamaarays  3

Add attachment to Aloituskokous
  Open task  Aloituskokous
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Scroll and click test id  add-targetted-attachment
  Select Frame     uploadFrame
  Wait until       Element should be visible  test-save-new-attachment
  Choose File      xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${TXT_TESTFILE_PATH}
  Click element    test-save-new-attachment
  Unselect Frame
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}

# FIXME check on a nother type of task
Aloituskokous requires action
  [Tags]  fail
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_user_action"]  1

Reject Aloituskokous
  [Tags]  fail
  Click enabled by test id  reject-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_user_action"]  1

Approve Aloituskokous
  [Tags]  fail
  Click enabled by test id  approve-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="ok"]  1

Aloituskokous form is still editable (LPK-494)
  Page Should Contain Element  xpath=//section[@data-doc-type="task-katselmus"]//input
  Xpath Should Match X Times  //section[@data-doc-type="task-katselmus"]//input[@readonly]  0

  Page Should Contain Element  xpath=//section[@data-doc-type="task-katselmus"]//select
  # LPK-1601 Katselmuksenlaji should be read only
  Xpath Should Match X Times  //section[@data-doc-type="task-katselmus"]//select[@disabled]  1
  Element should be disabled  //section[@data-doc-type="task-katselmus"]//select[@data-test-id='katselmuksenLaji']

Return to listing
  Click link  xpath=//section[@id="task"]//a[@data-test-id='back-to-application-from-task']
  Tab should be visible  tasks

Delete Muu tarkastus
  Wait until  Element should be visible  xpath=//div[@id="application-tasks-tab"]//table[@class="tasks"]//tbody/tr
  Open task  loppukatselmus
  Click enabled by test id  delete-task
  Confirm  dynamic-yes-no-confirm-dialog

Listing contains one less task
  Tab should be visible  tasks
  Task count is  task-katselmus  2

Three buildings, all not started
  [Tags]  fail
  Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr//span[@class="missing icon"]  3

Start constructing the first building
  [Tags]  fail
  Element text should be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]/td[@data-bind="text: util.buildingName($data)"]  1. 101 (039 muut asuinkerrostalot) - 2000 m²
  Click Element  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//a
  Wait Until  Element should be visible  modal-datepicker-date
  Input text by test id  modal-datepicker-date  1.1.2014
  ## Datepickers stays open when using Selenium
  Execute JavaScript  $("#ui-datepicker-div").hide();
  Click enabled by test id  modal-datepicker-continue
  Wait Until  Element should not be visible  modal-datepicker-date
  Confirm  dynamic-yes-no-confirm-dialog

Construction of the first building should be started
  [Tags]  fail
  Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//span[@class="ok icon"]  1
  Element Text Should Be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//*[@data-bind="dateString: $data.constructionStarted"]  1.1.2014
  Element Text Should Be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//*[@data-bind="fullName: $data.startedBy"]  Sibbo Sonja

Construction of the other buildins is not started
  [Tags]  fail
  Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr//span[@class="missing icon"]  2

Add katselmus
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-katselmus
  Wait until  Element should be visible  choose-task-subtype
  Select From List By Value  choose-task-subtype   muu tarkastus
  Input text  create-task-name  uus muu tarkastus
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task
  Task count is  task-katselmus  3

Katselmuksenlaji is set and disabled
  Open task  uus muu tarkastus
  Element should be disabled  xpath=//div[@id='taskDocgen']//select[@data-test-id='katselmuksenLaji']
  List Selection Should Be  xpath=//div[@id='taskDocgen']//select[@data-test-id='katselmuksenLaji']  muu tarkastus

Verify post-verdict attachments - Aloituskokous
  Click by test id  back-to-application-from-task
  Wait until  Element should be visible  xpath=//a[@data-test-id='application-open-attachments-tab']
  Open tab  attachments
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

Katselmus task created in an YA application does not include any Rakennus information (LPK-719)
  Open application  ${appname-ya}  ${propertyId}
  Open tab  verdict
  Fetch YA verdict
  Open tab  tasks
  Create katselmus task  task-katselmus-ya  uus muu ya-tarkastus  Muu valvontak\u00e4ynti
  Task count is  task-katselmus-ya  1
  Open task  uus muu ya-tarkastus
  Wait until  Element should not be visible  xpath=//div[@id='taskDocgen']//div[@data-repeating-id='rakennus']
  [Teardown]  Logout

Mikko is unable to edit Aloituskokous (LPK-494)
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  tasks
  Open task  Aloituskokous

  Page Should Contain Element  xpath=//section[@data-doc-type="task-katselmus"]//input
  ${inputCount} =  Get Matching Xpath Count  //section[@data-doc-type="task-katselmus"]//input
  Xpath Should Match X Times  //section[@data-doc-type="task-katselmus"]//input[@readonly]  ${inputCount}

  Page Should Contain Element  xpath=//section[@data-doc-type="task-katselmus"]//select
  ${selectCount} =  Get Matching Xpath Count  //section[@data-doc-type="task-katselmus"]//select
  Xpath Should Match X Times  //section[@data-doc-type="task-katselmus"]//select[@disabled]  ${selectCount}
  Click by test id  back-to-application-from-task

Mikko sets started past date for YA application (LPK-1054)
  Open application  ${appname-ya}  ${propertyId}
  Open tab  tasks
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  ${EMPTY}
  Set date and check  application-inform-construction-started-btn  construction-state-change-info-started  10.8.2012
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  Intonen Mikko
  [Teardown]  Logout

# TODO: Sonja sets ready past date for YA application (LPK-1054)
# This would require a well-formed application with all the required fields.

*** Keywords ***

Create katselmus task
  [Arguments]  ${taskSchemaName}  ${taskName}  ${taskSubtype}=
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   ${taskSchemaName}
  Run Keyword If  $taskSubtype  Wait until  Element should be visible  choose-task-subtype
  Run Keyword If  $taskSubtype  Select From List By Value  choose-task-subtype   ${taskSubtype}
  Input text  create-task-name  ${taskName}
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task

Set date and check
  [Arguments]  ${button}  ${span}  ${date}
  Wait Until  Element should be visible  jquery=[data-test-id=${button}]
  Click by test id  ${button}
  Wait Until  Element should be visible  modal-datepicker-date
  Input text by test id  modal-datepicker-date  ${date}
  ## Datepickers stays open when using Selenium
  Execute JavaScript  $("#ui-datepicker-div").hide();
  Click enabled by test id  modal-datepicker-continue
  Wait Until  Element should not be visible  modal-datepicker-date
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element Text Should Be  jquery=[data-test-id=${span}]  ${date}
