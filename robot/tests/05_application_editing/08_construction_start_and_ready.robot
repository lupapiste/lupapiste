*** Settings ***

Documentation   On application, construction is set started and ready
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Start_ready_app_${secs}
  Create application the fast way   ${appname}  753-423-2-162  ya-katulupa-vesi-ja-viemarityot

Sonja fills in the application fields required by krysp
  Open accordions  info
  Fill tyoaika fields
  Open tab  parties
  Open accordions  parties
  Fill required fields for the parties

Sonja submits the application, approves it and gives it a verdict
  Submit application
  Click enabled by test id  approve-application
  Open tab  verdict
  Submit empty verdict  verdictGiven  1

Sonja goes to the Rakentaminen tab and sets construction started via a dialog
  Open tab  tasks

  Element should be visible  //*[@data-test-id='application-inform-construction-started-btn']
  Element should not be visible  //*[@data-test-id='application-inform-construction-ready-btn']
  Element should not be visible  //*[@data-test-id='construction-state-change-info-started']
  Element should not be visible  //*[@data-test-id='construction-state-change-info-closed']

  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  02.06.2018
  Wait until  Application state should be  constructionStarted

  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']
  Element should not be visible  //*[@data-test-id='application-inform-construction-started-btn']
  Wait until  Element should be visible  //*[@data-test-id='construction-state-change-info-started']
  Element should not be visible  //*[@data-test-id='construction-state-change-info-closed']
  Element should not be visible  //*[@data-test-id='warranty-start-date-edit']
  Element should not be visible  //*[@data-test-id='warranty-end-date-edit']
  Element should be visible  //*[@data-test-id='application-open-tasks-tab']
  Tab should be visible  tasks

# Datepicker can fail if Firefox is not the active application.
Sonja goes to the Rakentaminen tab and sets construction ready via a dialog
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-ready-btn  02.07.2018
  Wait until  Application state should be  closed

  Wait until  Element should not be visible  //*[@data-test-id='application-inform-construction-ready-btn']
  Element should not be visible  //*[@data-test-id='application-inform-construction-started-btn']
  Wait until  Element should be visible  //*[@data-test-id='construction-state-change-info-started']
  Element should not be visible  //*[@data-test-id='warranty-start-date-edit']
  Element should not be visible  //*[@data-test-id='warranty-end-date-edit']
  Element should be visible  //*[@data-test-id='construction-state-change-info-closed']
  Element should be visible  //*[@data-test-id='application-open-tasks-tab']
  Tab should be visible  tasks

*** Keywords ***

Sets construction started/ready via modal datepicker dialog
  [Arguments]  ${openDialogButtonId}  ${date}
  Click enabled by test id  ${openDialogButtonId}
  Wait until  element should be visible  modal-datepicker-date
  Element Should Be Enabled  modal-datepicker-date
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  modal-datepicker-date  ${date}
  Click enabled by test id  modal-datepicker-continue
  Confirm  dynamic-yes-no-confirm-dialog

