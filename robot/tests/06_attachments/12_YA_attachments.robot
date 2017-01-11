*** Settings ***

Documentation  Mikko adds an attachment to YA application
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates a kaivulupa
  [Tags]  firefox
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Set Suite Variable  ${propertyId}  753-423-2-162
  Create application  ${appname}  753  ${propertyId}  YA-kaivulupa
  Open tab  attachments

Mikko uploads an attachment, only grouping option is 'Yleisesti hankkeeseen'
  Test id visible  add-attachments-label
  Scroll to top
  Upload batch file and check for grouping  0  ${TXT_TESTFILE_PATH}  Muu liite  Muu  Yleisesti hankkeeseen
  Click enabled by test id  batch-ready
  Wait until  No such test id  batch-ready

Mikko navigates to attachment page
  Open attachment details  muut.muu

Mikko cannot change the attachment grouping
  Only one attachment grouping is available  Yleisesti hankkeeseen

Mikko submits the application and logs out
  Return to application
  Submit application
  Log out

Sonja opens the application
  As Sonja
  Open application  ${appname}  ${propertyId}

Sonja opens the attachments tab
  Open tab  attachments

Sonja adds an attachment template, only grouping option is 'Yleisesti hankkeeseen'
  Click by test id  add-attachment-templates
  Select From Autocomplete  div[data-test-id=attachment-type-group-autocomplete]  Muut liitteet
  Select From Autocomplete  div[data-test-id=attachment-type-autocomplete]  Lupaehto
  Only one attachment grouping is available  Yleisesti hankkeeseen
  Click by test id  require-attachments-bubble-dialog-ok

*** Keywords ***

Upload batch file and check for grouping
  [Arguments]  ${index}  ${path}  ${type}  ${contents}  ${grouping}
  Expose file input  input[data-test-id=add-attachments-input]
  Choose file  jquery=input[data-test-id=add-attachments-input]  ${path}
  Hide file input  input[data-test-id=add-attachments-input]
  Wait Until  Element should be visible  jquery=div.upload-progress--finished
  Only one attachment grouping is available  ${grouping}
  Select From Autocomplete  div.batch-autocomplete[data-test-id=batch-type-${index}]  ${type}
  Run keyword unless  '${contents}' == '${EMPTY}'  Fill test id  batch-contents-${index}  ${contents}
  Select from list by label  jquery=[data-test-id=batch-grouping-${index}] select  ${grouping}

Only one attachment grouping is available
  [Arguments]  ${grouping}
  Wait until  Xpath Should Match X Times  //select[@data-test-id='attachment-operation-select']/option  1
  Element text should be  xpath=//select[@data-test-id='attachment-operation-select']/option  ${grouping}
