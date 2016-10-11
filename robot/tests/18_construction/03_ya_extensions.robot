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
${kryspstart}  4.10.2016
${kryspend}    22.11.2016
${reason}      Jatkoajan perustelu
${ext4}        Extension si      

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
  Create extension  ${ext2}  1.8.2016  9.9.2016  

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
 
Pena submits extension
  Submit application
  [Teardown]  Logout
  
Sonja cannot approve application but extension
  Sonja logs in
  Open application  ${ext1}  ${propertyid}
  No such test id  approve-application
  Scroll and click test id  approve-extension
  Deny  dynamic-yes-no-confirm-dialog

The same is true in the required field summary tab
  Open tab  requiredFieldSummary  
  No such test id  approve-application-summaryTab
  Scroll and click test id  approve-extension-summaryTab
  Deny  dynamic-yes-no-confirm-dialog
  [Teardown]  Logout

Simulate reading application extension from backend
  Go to  ${SERVER}/dev/mock-ya-extension
  Wait until  Page should contain  YA extension KRYSP processed.
  Go back

Pena logs in again
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  tasks

The third row contains reason but not state
  Check row  0  1.8.2016  9.9.2016  Luonnos
  Check row  1  20.9.2016  10.10.2016  Hakemus jätetty
  Check row  2  ${kryspstart}  ${kryspend}  ${EMPTY}  ${reason}
  Check row  3  11.11.2016  12.12.2016  Luonnos

Pena creates extension with dates matching KRYSP
  Create extension  ${ext4}  ${kryspstart}  ${kryspend}

Now the third row has the draft state
  Open tab  tasks
  Check row  0  1.8.2016  9.9.2016  Luonnos
  Check row  1  20.9.2016  10.10.2016  Hakemus jätetty
  Check row  2  ${kryspstart}  ${kryspend}  Luonnos  ${reason}
  Check row  3  11.11.2016  12.12.2016  Luonnos
    

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
  [Arguments]  ${index}  ${start}  ${end}  ${state}  ${reason}=${EMPTY}
  Wait until  Test id text is  start-date-${index}  ${start}
  Test id text is  end-date-${index}  ${end}
  Run keyword unless  '${state}' == ''  Test id text is  state-link-${index}  ${state}
  Run keyword if  '${state}' == ''  No such test id  state-link-${index}
  Run keyword unless  '${reason}' == ''  Test id text is  reason-${index}  ${reason}

Set dates
  [Arguments]  ${start}  ${end}
  Open tab  info
  Open accordions  info
  Input text with jQuery  input.form-date:visible:first  ${start}
  Input text with jQuery  input.form-date:visible:last  ${end}
