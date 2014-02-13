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
  Click enabled by test id  give-verdict
  Wait Until  Element Should Be Visible  verdict-id
  Input Text  verdict-id  1234567
  Select From List  verdict-type-select  1
  Input Text  verdict-name  Sonja
  Input Text  verdict-given  06.02.2014
  Input Text  verdict-official  06.02.2014\t
  Wait Until  Element should be visible  verdict-submit
  Click enabled by test id  verdict-submit
  Wait Until  Element Should Be Visible  xpath=//div[@data-test-state='verdictGiven']

Add post-verdict attachment
  Open tab  attachments
  Add attachment  ${TXT_TESTFILE_PATH}  ${EMPTY}
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-post-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]
  