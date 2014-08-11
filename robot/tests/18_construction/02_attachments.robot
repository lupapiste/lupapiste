*** Settings ***

Documentation   Post-verdict attachments
Suite teardown  Logout
Resource        ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Sonja prepares the application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  pre-verdict-attachment-test${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Submit application
  Open tab  verdict
  Fetch verdict

Add post-verdict attachment
  Open tab  attachments
  Add attachment  ${TXT_TESTFILE_PATH}  ${EMPTY}
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]
