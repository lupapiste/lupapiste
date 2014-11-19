*** Settings ***

Documentation  Stamping functionality for authority
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko creates application and goes to empty attachments tab
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  stamping${secs}
  Mikko logs in
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Open tab  attachments

Mikko adds PDF attachment without comment
  Add attachment  ${TXT_TESTFILE_PATH}  ${EMPTY}  Uusi asuinrakennus
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${TXT_TESTFILE_NAME}')]
  
Mikko does not see stamping button
  Element should not be visible  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="stamp-attachments-btn"]
  
Mikko submits application for authority
  Submit application
  
Sonja logs in
  Logout
  Sonja logs in
  
Sonja goes to attachments tab
  Open application  ${appname}  753-416-25-30
  Open tab  attachments

Sonja sees stamping button
  Element should be visible  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="stamp-attachments-btn"]
  
Sonja clicks stamp button, stamping page opens
  Click element  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="stamp-attachments-btn"]
  Wait Until  Element should be visible  stamping-container
