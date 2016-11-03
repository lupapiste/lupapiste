*** Settings ***

Documentation   On application, construction is set started and ready
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Olli-ya prepares the application
  Olli-ya logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Warranty_app_${secs}
  Create application the fast way   ${appname}  564-423-2-162  ya-katulupa-vesi-ja-viemarityot

Olli-ya submits the application, approves it and gives it a verdict
  Submit application
  Click enabled by test id  approve-application
  Confirm notification dialog
  Open tab  verdict
  Submit empty verdict  verdictGiven  1

Olli-ya goes to the Rakentaminen tab and sets construction started and closed
  Open tab  tasks

  Element should be visible  //*[@data-test-id='application-inform-construction-started-btn']
  Element should not be visible  //*[@data-test-id='application-inform-construction-ready-btn']
  Element should not be visible  //*[@data-test-id='construction-state-change-info-started']
  Element should not be visible  //*[@data-test-id='construction-state-change-info-closed']

  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  01.01.2018
  Wait until  Application state should be  constructionStarted

  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']

  Sets construction started/ready via modal datepicker dialog  application-inform-construction-ready-btn  01.02.2018
  Wait until  Application state should be  closed

  Element should be visible  //*[@data-test-id='warranty-start-date-edit']
  Element should be visible  //*[@data-test-id='warranty-end-date-edit']
  warranty start date should be  01.02.2018
  Warranty end date should be  01.02.2020
  Tab should be visible  tasks

Olli-ya changes warranty period and logout
  Tab should be visible  tasks

  Sets warranty start/end date  warranty-start-date-edit  01.03.2018
  Sets warranty start/end date  warranty-end-date-edit  01.03.2019

  warranty start date should be  01.03.2018
  Warranty end date should be  01.03.2019

  Logout

Olli-ya logs back in and open application
  Olli-ya logs in
  Open application  ${appname}  564-423-2-162

  Open tab  tasks

  Element should be visible  //*[@data-test-id='warranty-start-date-edit']
  Element should be visible  //*[@data-test-id='warranty-end-date-edit']

  warranty start date should be  01.03.2018
  Warranty end date should be  01.03.2019


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

Sets warranty start/end date
  [Arguments]  ${dateInputId}  ${date}
  Input text by test id  ${dateInputId}  ${date}

Warranty start date should be
  [Arguments]  ${date}
  ${value} =  Get Element Attribute  xpath=//input[@data-test-id='warranty-start-date-edit']@value
  Should be equal  ${value}  ${date}

Warranty end date should be
  [Arguments]  ${date}
  ${value} =  Get Element Attribute  xpath=//input[@data-test-id='warranty-end-date-edit']@value
  Should be equal  ${value}  ${date}
