*** Settings ***

Documentation   Application gets tasks based on verdict
Suite teardown  Logout
Resource        ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Sonja prepares the application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Taskitesti${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Submit application
  Open tab  verdict
  Click enabled by test id  fetch-verdict
  Wait Until  Element Should Be Visible  dynamic-ok-confirm-dialog
  Element Text Should Be  xpath=//div[@id='dynamic-ok-confirm-dialog']//div[@class='dialog-user-content']/p  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 9 uutta vaatimusta Rakentaminen-välilehdelle.
  Confirm  dynamic-ok-confirm-dialog
  Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-0']  2013-01
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110

Rakentaminen tab opens
  Open tab  tasks

Rakentaminen tab contains 9 tasks
  Wait until  Xpath Should Match X Times  //div[@id='application-tasks-tab']//table[@data-bind="foreach: taskGroups"]/tbody/tr  9

Katselmukset
  Wait Until  Page should contain  Kokoukset, katselmukset ja tarkastukset
  Task count is  task-katselmus  3

Työnjohtajat
  Wait until  Page should contain  Työnjohtajat
  Task count is  task-vaadittu-tyonjohtaja  3

Muut lupamaaraykset
  Wait until  Page should contain  Muut lupamääräykset
  Task count is  task-lupamaarays  3

Add attachment to Aloituskokous
  Open task  Aloituskokous
  Click enabled by test id  add-targetted-attachment
  Select Frame     uploadFrame
  Wait until       Element should be visible  test-save-new-attachment
  Choose File      xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${TXT_TESTFILE_PATH}
  Click element    test-save-new-attachment
  Unselect Frame
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}

Aloituskokous requires action
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_authority_action"]  1

Reject Aloituskokous
  Click enabled by test id  reject-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="requires_user_action"]  1

Approve Aloituskokous
  Click enabled by test id  approve-task
  Wait until  Xpath Should Match X Times  //section[@id='task']/h1/span[@data-test-state="ok"]  1

Return to listing
  Click link  xpath=//section[@id="task"]//a[@data-test-id='back-to-application-from-task']
  Tab should be visible  tasks

Delete Muu tarkastus
  Open task  loppukatselmus
  Click enabled by test id  delete-task
  Confirm  dynamic-yes-no-confirm-dialog

Listing contains one less task
  Tab should be visible  tasks
  Task count is  task-katselmus  2

Three buildings, all not started
  Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr//span[@class="missing icon"]  3

#Start constructing the first building
#  Element text should be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]/td[@data-bind="text: util.buildingName($data)"]  1. 101 (039 muut asuinkerrostalot) - 2000 m²
#  Click Element  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//a
#  Wait Until  Element should be visible  modal-datepicker-date
#  Input text by test id  modal-datepicker-date  1.1.2014
#  ## Datepickers stays open when using Selenium
#  Execute JavaScript  $("#ui-datepicker-div").hide();
#  Click enabled by test id  modal-datepicker-continue
#  Wait Until  Element should not be visible  modal-datepicker-date
#  Confirm  dynamic-yes-no-confirm-dialog

#Construction of the first building should be started
#  Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//span[@class="ok icon"]  1
#  Element Text Should Be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//*[@data-bind="dateString: $data.constructionStarted"]  1.1.2014
#  Element Text Should Be  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr[1]//*[@data-bind="fullName: $data.startedBy"]  Sonja Sibbo

#Construction of the other buildins is not started
# Wait until  Xpath Should Match X Times  //div[@id="application-tasks-tab"]//tbody[@data-bind="foreach: buildings"]/tr//span[@class="missing icon"]  2

Add katselmus
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-katselmus
  Input text  create-task-name  uus muu tarkastus
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task
  Task count is  task-katselmus  3
  Open task  uus muu tarkastus

Verify post-verdict attachmens - Aloituskokous
  Click by test id  back-to-application-from-task
  Wait until  Element should be visible  xpath=//a[@data-test-id='application-open-attachments-tab']
  Open tab  attachments
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]

*** Keywords ***

Task count is
  [Arguments]  ${type}  ${amount}
  Wait until  Xpath Should Match X Times  //table[@data-bind="foreach: taskGroups"]/tbody/tr[@data-test-type="${type}"]  ${amount}

Open task
  [Arguments]  ${name}
  Click Element  //div[@id='application-tasks-tab']//table//td/a[text()='${name}']
  Wait Until  Element should be visible  taskAttachments
  Wait until  Element should be visible  taskDocgen
