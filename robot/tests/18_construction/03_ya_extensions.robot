*** Settings ***

Documentation   YA extension applications table
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Variables ***
${appname}     Roadcrew
${propertyid}  753-416-88-88
${ext1}        Extension yi
${ext2}        Extension er
${ext3}        Extension san

*** Test Cases ***

Pena logs in creates the main YA application
  Pena logs in
  Create application with state  ${appname}  ${propertyid}  ya-katulupa-vesi-ja-viemarityot  verdictGiven

Extensions table is not visible
  Open tab  tasks
  No such test id  extensions-table

Pena requests first extension
  Create extension  ${ext1}  20.09.2016  10.10.2016

Pena now sees extensions table
  Open tab  tasks
  Check row  0  20.9.2016  10.10.2016  Luonnos  

Pena creates second extension
  Create extension  ${ext2}  01.08.2016  09.09.2016  

Pena creates third extension
  Create extension  ${ext3}  11.11.2016  12.12.2016

The extensions are listed according to the start date
  Open tab  tasks
  Check row  0  1.8.2016  9.9.2016  Luonnos
  Check row  1  20.9.2016  10.10.2016  Luonnos  
  Check row  2  11.11.2016  12.12.2016  Luonnos

Second link leads to the first extension
  Scroll and click test id  state-link-1
  Test id text is  application-title  EXTENSION YI
  [Teardown]  Logout

*** Keywords ***

Create extension
  [Arguments]  ${address}  ${start}  ${end}
  Scroll and click test id  continuation-period-create-btn
  Confirm  dynamic-yes-no-confirm-dialog
  Change address  ${address}
  Set dates  ${start}  ${end}
  Follow link permit
  

Follow link permit
  Scroll and click test id  test-application-link-permit-lupapistetunnus
  
Change address
  [Arguments]  ${address}
  Click by test id  change-location-link
  Input text by test id  application-new-address  ${address}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${address}

Check row
  [Arguments]  ${index}  ${start}  ${end}  ${state}
  Wait until  Test id text is  start-date-${index}  ${start}
  Test id text is  end-date-${index}  ${end}
  Test id text is  state-link-${index}  ${state}
  
Set dates
  [Arguments]  ${start}  ${end}
  Open tab  info
  Open accordions  info
  Input text with jQuery  input.form-date:visible:first  ${start}
  Input text with jQuery  input.form-date:visible:last  ${end}
