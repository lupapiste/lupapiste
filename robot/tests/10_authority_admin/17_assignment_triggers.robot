*** Settings ***

Documentation   Authority admin adds handlers and task triggers
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***


Authority admin goes to the tasks page
  Sipoo logs in
  Go to page  assignments

Authority admin adds new handler role
  Click enabled by test id  add-handler-role
  Input text by test id  edit-role-2-fi  Käsittelijä robotti
  Input text by test id  edit-role-2-en  Handler robot
  Input text by test id  edit-role-2-sv  Handläggare robot
  Wait until  Positive indicator should be visible
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-fi']  Käsittelijä robotti
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-en']  Handler robot
  Wait until  Textfield value should be  //input[@data-test-id='edit-role-2-sv']  Handläggare robot

Authority admin should see triggers only when assignments are anabled
  Toggle selected  assignments-enabled
  Wait until  Element should be visible by test id  trigger-component
  Toggle toggle  assignments-enabled
  Toggle not selected  assignments-enabled
  Wait until  Element should not be visible by test id  trigger-component
  Toggle toggle  assignments-enabled

Organization already have two triggers
  Wait Until  Xpath Should Match X Times  //div[@id='triggers-table']//table/tbody/tr  2
  Click remove trigger
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Positive indicator should be visible
  Click remove trigger
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Positive indicator should be visible

Authority adds trigger
  Click enabled by test id  add-trigger
  Select from autocomplete by test id  triggers-target-component  Aitapiirustus
  Select From List by test id and label  triggers-handler-select  Käsittelijä robotti
  Test id disabled  save-trigger-dialog-ok
  Input text by test id  triggers-description-input  Description
  Click enabled by test id  save-trigger-dialog-ok
  Wait until  Positive indicator should be visible
  Wait Until  Xpath Should Match X Times  //div[@id='triggers-table']//table/tbody/tr  1
  Wait until  Element text should be  xpath=//span[@id="trigger-target"]  Aitapiirustus,
  Wait until  Element text should be  xpath=//span[@id="trigger-handler"]  Käsittelijä robotti

Authority starts adding another, but doesn't remember the target
  Click enabled by test id  add-trigger
  Select From List by test id and label  triggers-handler-select  Käsittelijä robotti
  Input text by test id  triggers-description-input  Description
  Test id disabled  save-trigger-dialog-ok
  Click element  xpath=//div[@id='dialog-add-trigger']//p[contains(@class, 'dialog-close')]

Authority should be able to edit trigger
  Click by test id  edit-trigger
  Select from autocomplete by test id  triggers-target-component  Johtokartta
  Select From List by test id and label  triggers-handler-select  Käsittelijä
  Input text by test id  triggers-description-input  Updated description
  Click enabled by test id  save-trigger-dialog-ok
  Wait until  Positive indicator should be visible
  Wait Until  Xpath Should Match X Times  //div[@id='triggers-table']//table/tbody/tr  1
  Wait until  Element text should be  xpath=//span[@id="trigger-target"]  Aitapiirustus, Johtokartta,
  Wait until  Element text should be  xpath=//span[@id="trigger-handler"]  Käsittelijä

Authority should be able to delete trigger
  Click remove trigger
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Positive indicator should be visible
  Wait Until  Xpath Should Match X Times  //div[@id='triggers-table']//table/tbody/tr  0


*** Keywords ***

Click remove trigger
  ${selector} =   Set Variable  $("[data-test-id='remove-trigger']:visible")
  Execute Javascript  ${selector}[0].click();
