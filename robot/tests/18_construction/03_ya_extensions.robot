*** Settings ***

Documentation   YA extension applications table
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Variables ***
${appname}     Roadcrew
${propertyid}  753-416-88-88
${ext1}        Extension 1

*** Test Cases ***

Pena logs in creates the main YA application
  Pena logs in
  Create application with state  ${appname}  ${propertyid}  ya-katulupa-vesi-ja-viemarityot  verdictGiven

Extensions table is not visible
  Open tab  tasks
  No such test id  extensions-table

Pena requests extension
  Scroll and click test id  continuation-period-create-btn
  Confirm  dynamic-yes-no-confirm-dialog
  Change address  ${ext1}

Pena sets dates for the extension
  Set dates  20.09.2016  10.10.2016
  
The extensions table is still not shown in the original application
  Follow link permit
  Open tab  tasks
  No such test id  extensions-table
  [Teardown]  Logout

Sipoo-ya logs in and removes YA KRYSP endpoint
  Sipoo-ya logs in
  Go to page  backends
  Click link  edit-wfs-for-YA
  Input text  edit-wfs  ${EMPTY}
  Click button  wfs-submit
  [Teardown]  Logout

Pena now sees extensions table
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  tasks
  Check row  0  20.9.2016  10.10.2016  Luonnos  
    


*** Keywords ***

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
