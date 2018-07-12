*** Settings ***

Documentation  Add RAM attachment
Resource        ../../common_resource.robot
Resource        ../06_attachments/attachment_resource.robot
Variables      ../06_attachments/variables.py

*** Keywords ***

Sonja adds RAM attachment
  [Arguments]  ${type}
  Open tab  attachments
  Scroll and click test id  toggle-all-filters-checkbox
  Open attachment details  ${type}
  Click button  id=test-attachment-approve
  Click by test id  add-ram-attachment
  # Attachment page for new ram-attachment opens...
  Element should be visible by test id  ram-prefix
  Element should be visible by test id  ram-links-table
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait until  Scroll and click test id  back-to-application-from-attachment
