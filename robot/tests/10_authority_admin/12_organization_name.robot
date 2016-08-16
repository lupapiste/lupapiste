*** Settings ***

Documentation   Authority admin creates users
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Authority admin goes to the authority admin page
  Sipoo logs in
  Go to page  applications

Sipoo sees Finnish and Swedish name for organization
  Wait until  Textfield value should be  //input[@data-test-id='organization-name.fi']  Sipoon rakennusvalvonta
  Textfield value should be  //input[@data-test-id='organization-name.sv']  Sipoon rakennusvalvonta

Sipoo types empty name
  Input text by test id  organization-name.fi  ${EMPTY}
  Wait until  Negative indicator should be visible

Sipoo types new Finnish name
  Input text by test id  organization-name.fi  Sipoon uusi nimi
  Wait until  Positive indicator should be visible

Name remains changed after reload
  Reload page
  Wait until  Textfield value should be  //input[@data-test-id='organization-name.fi']  Sipoon uusi nimi
