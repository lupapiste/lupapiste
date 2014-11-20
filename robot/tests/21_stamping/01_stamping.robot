*** Settings ***

Documentation  Stamping functionality for authority
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Set stamping info variables
  Set Suite Variable  ${STAMP_TEXT}  Hyvaksyn
  Set Suite Variable  ${STAMP_DATE}  12.12.2012
  Set Suite Variable  ${STAMP_ORGANIZATION}  LupaRobot
  Set Suite Variable  ${STAMP_XMARGIN}  12
  Set Suite Variable  ${STAMP_YMARGIN}  88
  Set Suite Variable  ${STAMP_TRANSPARENCY_IDX}  2

Mikko creates application and goes to empty attachments tab
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  stamping${secs}
  Mikko logs in
  Create application the fast way  ${appname}  753  753-416-25-30  asuinrakennus
  Open tab  attachments

Mikko adds PDF attachment without comment
  Add attachment  ${PDF_TESTFILE_PATH}  ${EMPTY}  Uusi asuinrakennus
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//a[contains(., '${PDF_TESTFILE_NAME}')]
  
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

Sonja sees stamping info fields
  Element should be visible  stamp-info
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-text"]
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-date"]
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-organization"]
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-xmargin"]
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-ymargin"]
  Element should be visible  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//select[@data-test-id="stamp-info-transparency"]

Sonja inputs new stamping info values
  Input text by test id  stamp-info-text  ${STAMP_TEXT}
  Input text by test id  stamp-info-date  ${STAMP_DATE}
  Input text by test id  stamp-info-organization  ${STAMP_ORGANIZATION}
  Input text by test id  stamp-info-xmargin  ${STAMP_XMARGIN}
  Input text by test id  stamp-info-ymargin  ${STAMP_YMARGIN}

Sonja can go to attachments tab. When she returns, stamp info fields are persistent.
  Click element  xpath=//div[@id="stamping-container"]//a[@data-test-id="back-to-application-from-stamping"]
  Element should be visible  application-attachments-tab
  Click element  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="stamp-attachments-btn"]
  Element should be visible  stamp-info  
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-text"]  ${STAMP_TEXT}
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-date"]  ${STAMP_DATE}
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-organization"]  ${STAMP_ORGANIZATION}
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-xmargin"]  ${STAMP_XMARGIN}
  Textfield value should be  xpath=//div[@id="stamping-container"]//form[@id="stamp-info"]//input[@data-test-id="stamp-info-ymargin"]  ${STAMP_YMARGIN}
  
