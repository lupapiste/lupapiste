*** Settings ***

Documentation   On application, construction is set started and ready
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Start_ready_app_${secs}
  Create application  ${appname}  753  753-416-25-23  YA-kaivulupa  # Vaaditut kentät tarvii täyttää!

Sonja submits the application, approves it and gives it a verdict
  Submit application
  Click enabled by test id  approve-application
  Throw in a verdict
  Wait Until  Element should not be visible  application-inform-construction-ready-btn

Sonja goes to the Rakentaminen tab and sets construction started via a dialog
  Open tab  tasks
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  02.06.2018
  Wait until  Application state should be  constructionStarted
  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']

Sonja goes to the Rakentaminen tab and sets construction ready via a dialog
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-ready-btn  02.07.2018
  #
  # TODO: The obligatory values in application would need to be filled before these would work.
  #       Currently, krysp sending is failing on application approval, and thus e.g. app states are not correct.
  #       Would need e.g. a new fixture that creates a ready-filled application.
  #       How much should be done in ftest, versus itest?
  #
#  Wait until  Element should not be visible  //*[@data-test-id='application-inform-construction-ready-btn']
#  Wait until  Application state should be  closed


*** Keywords ***

Sets construction started/ready via modal datepicker dialog
  [Arguments]  ${openDialogButtonId}  ${date}
  Click enabled by test id  ${openDialogButtonId}
  Wait until  element should be visible  dialog-modal-datepicker
  Wait Until  Element Should Be Enabled  modal-datepicker-date
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  modal-datepicker-date  ${date}
  Click enabled by test id  modal-datepicker-continue
  Confirm  dynamic-yes-no-confirm-dialog
