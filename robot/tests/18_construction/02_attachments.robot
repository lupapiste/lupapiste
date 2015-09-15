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
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo
  Submit application
  Click enabled by test id  approve-application
  Open tab  verdict
  Fetch verdict

Add post-verdict attachment
  Open tab  attachments
  Wait until  Element should not be visible  xpath=//button[@data-test-id='export-attachments-to-backing-system']
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]
  Wait until  Element should be visible  xpath=//button[@data-test-id='export-attachments-to-backing-system']
