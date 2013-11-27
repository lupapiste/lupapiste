*** Settings ***

Documentation   Application gets tasks based on verdict
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Taskitesti${secs}
  Create application the fast way  ${appname}  753  753-416-25-30
  Submit application
  Open tab  verdict
  Click enabled by test id  fetch-verdict
  Wait Until  Element Should Be Visible  dynamic-ok-confirm-dialog
  Element Text Should Be  xpath=//div[@id='dynamic-ok-confirm-dialog']//div[@class='dialog-user-content']/p  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot.
  Confirm  dynamic-ok-confirm-dialog
  Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-0']  2013-01
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110

Rakentaminen tab opens
  Open tab  tasks

Rakentaminen tab contains 9 tasks
  Wait until  Xpath Should Match X Times  //table[@data-bind="foreach: taskGroups"]/tbody/tr  9

Katselmukset
  Wait Until  Page should contain  Kokoukset, katselmukset ja tarkastukset
  Task count is  task-katselmus  3

Työnjohtajat
  Wait until  Page should contain  Työnjohtajat
  Task count is  task-vaadittu-tyonjohtaja  3

Muut lupamaaraykset
  Wait until  Page should contain  Muut lupamääräykset
  Task count is  task-lupamaarays  3

*** Keywords ***

Task count is
  [Arguments]  ${type}  ${amount}
  Wait until  Xpath Should Match X Times  //table[@data-bind="foreach: taskGroups"]/tbody/tr[@data-test-type="${type}"]  ${amount}