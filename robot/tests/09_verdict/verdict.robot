*** Settings ***

Documentation   Application gets verdict
Suite teardown  Logout
Resource        ../../common_resource.robot
Library         DateTime

*** Test Cases ***

Mikko want to build Olutteltta
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Olutteltta${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus

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
  Verdict is given  2013-01  0
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110

Sonja creates verdict with adds comment
  Go to give new verdict
  Input verdict  123567890  6  01.05.2018  01.06.2018  Kaarina Krysp III
  Comment verdict  Myönnetään...

Return to application and come back
  Click enabled by test id  return-from-verdict
  Click enabled by test id  edit-verdict

Add katselmus
  # 3 tasks from backend
  Task count is  task-katselmus  3
  Click enabled by test id  verdict-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-katselmus
  Input text  create-task-name  uus lupaehto
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task
  # One on this verdict screen and one hidden in tasks tab
  Task count is  task-katselmus  5

Sonja publishes verdict
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  verdictGiven
  Verdict is given  123567890  2
  Wait Until  Element text should be  xpath=//div[@data-test-id='given-verdict-id-2-content']//span[@data-bind='dateString: paivamaarat.anto']  1.5.2018
  Wait Until  Element text should be  xpath=//div[@data-test-id='given-verdict-id-2-content']//span[@data-bind='dateString: paivamaarat.lainvoimainen']  1.6.2018

Add and delete verdict
  Verdict count is  3
  Go to give new verdict
  Verdict count is  4
  Click enabled by test id  delete-verdict
  Confirm  dynamic-yes-no-confirm-dialog
  Verdict count is  3

Stamping dialog opens
  Open tab  attachments
  Element should be visible  xpath=//section[@id='application']//button[@data-test-id='application-stamp-btn']
  Click enabled by test id  application-stamp-btn
  Wait Until  Element should be visible  dialog-stamp-attachments

Stamp attachments
  Click enabled by test id   start-stamping
  Click enabled by test id   application-stamp-dialdog-ok
  Wait Until  Element should not be visible  dialog-stamp-attachments
  [Teardown]  Logout

Mikko sees that the application has verdicts
  Mikko logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${appname}']//div[@class='unseen-indicators']  3
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Verdict is given  2013-01  0
  Verdict is given  123567890  2

Mikko signs the verdict
  Element should be visible  xpath=//div[@data-test-id='verdict-signature-ui']
  Click Element  xpath=//button[@data-test-id='sign-verdict-button']
  Wait Until Element Is Visible  xpath=//input[@data-test-id='sign-verdict-password']
  Input Text  xpath=//input[@data-test-id='sign-verdict-password']  mikko123
  Click Element  xpath=//Button[@data-test-id='do-sign-verdict']
  Wait Until  Element should be visible  xpath=//div[@data-test-id='verdict-signature-listing']
  Element should not be visible  xpath=//div[@data-test-id='verdict-signature-ui']
  Element should Contain  xpath=//div[@data-test-id='verdict-signature-listing']  Mikko Intonen
  ${d} =  Get Current Date  result_format=%d.%m.%Y
  Element should Contain  xpath=//div[@data-test-id='verdict-signature-listing']  ${d}

*** Keywords ***

Verdict is given
  [Arguments]  ${kuntalupatunnus}  ${i}
  Wait until  Element should be visible  application-verdict-details
  Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-${i}']  ${kuntalupatunnus}

Verdict count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //div[@id="application-verdict-tab"]//h2[@class="application_section_header"]  ${amount}

Input verdict
  [Arguments]  ${backend-id}  ${verdict-type-select-value}  ${verdict-given-date}  ${verdict-official-date}  ${verdict-giver-name}
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text  backend-id  ${backend-id}
  Select From List By Value  verdict-type-select  ${verdict-type-select-value}
  Input text  verdict-given  ${verdict-given-date}
  Input text  verdict-official  ${verdict-official-date}
  Input text  verdict-name  ${verdict-giver-name}
  ## Trigger change manually
  Execute JavaScript  $("#backend-id").change();
  Execute JavaScript  $("#verdict-type-select").change();
  Execute JavaScript  $("#verdict-given").change();
  Execute JavaScript  $("#verdict-official").change();
  Execute JavaScript  $("#verdict-name").change();

Comment verdict
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Wait Until  Element should be enabled  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Click element  xpath=//div[@id='conversation-panel']//*[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

