*** Settings ***

Documentation   Resources for Filebank robots
Resource       ../../common_resource.robot
Variables       ../../common_variables.py
Variables       ../06_attachments/variables.py

*** Keywords ***

Admin enables filebank for organization
  [Arguments]  ${organization-id}
  SolitaAdmin logs in
  Go to page  organizations
  Fill test id  organization-search-term  ${organization-id}
  Scroll and click test id  edit-organization-${organization-id}
  Wait until  Select checkbox  filebankEnabled
  [Teardown]  Logout

Upload filebank file
  [Arguments]  ${path}
  Set test variable  ${test-id}  filebank-upload-input
  Expose file input  input[data-test-id=${test-id}]
  Choose file  jquery=input[data-test-id=${test-id}]  ${path}
  Hide file input  input[data-test-id=${test-id}]

Add keyword
  [Arguments]  ${row}  ${keyword}
  Click by test id  filebank-upload-${row}-add
  Fill test id  filebank-upload-${row}-new  ${keyword}
  Sleep  0.2s

Upload filebank file with keywords
  [Arguments]  ${path}  @{keywords}
  Upload filebank file  ${path}
  FOR   ${keyword}   IN   @{keywords}
    Add keyword  0  ${keyword}
  END
  Click by test id  finish-upload


Add keyword after upload
  [Arguments]  ${row}  ${keyword}
  Click by test id  filebank-row-${row}-keywords-add
  Fill test id  filebank-row-${row}-keywords-new   ${keyword}

Check that text of a keyword is same
  [Arguments]  ${row}  ${nth-keyword}  ${text}
  Test id text is  filebank-row-${row}-keywords-${nth-keyword}-edit  ${text}

Check that text of a keyword is same before uploading
  [Arguments]  ${row}  ${nth-keyword}  ${text}
  Test id text is  filebank-upload-${row}-${nth-keyword}-edit  ${text}

Remove keyword after upload
  [Arguments]  ${row}  ${nth-keyword}
  Click by test id  filebank-row-${row}-keywords-${nth-keyword}-remove

Remove file
  [Arguments]  ${row}
  Click by test id  filebank-row-${row}-remove-file


