*** Settings ***

Documentation  Mikko adds an attachment to inforequest
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates an inforequests
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest}  ir-attach${secs}
  Set Suite Variable  ${propertyId}  753-416-6-3
  Create inforequest the fast way  ${inforequest}  360603.153  6734222.95  ${propertyId}  kerrostalo-rivitalo  Liitetesti

Mikko adds an attachment
  Add attachment  inforequest  ${PNG_TESTFILE_PATH}  ${PNG_TESTFILE_DESCRIPTION}

Attachment table has one row
  Element should be visible by test id  inforequest-attachments-table
  XPath should match X times  //table[@data-test-id='inforequest-attachments-table']/tbody/tr  1

Mikko open attachment details
  Click link  xpath=//div[@data-test-id="comments-table"]//a
  Wait Until  Element Should Be Visible  xpath=//section[@id="attachment"]//a[@data-test-id="back-to-application-from-attachment"]
  Wait Until  Page Should Contain  ${PNG_TESTFILE_NAME}

Mikko returns to inforequest
  Click link  xpath=//section[@id="attachment"]//a[@data-test-id="back-to-application-from-attachment"]
  Wait Until  Element Should Not Be Visible  //section[@id="application"]
  Wait Until  Element Should Be Visible  //section[@id="inforequest"]

Mikko deletes inforequest attachment
  Click element  xpath=//table[@data-test-id='inforequest-attachments-table']/tbody//span[@title='Poista liite']
  Confirm yes no dialog
  XPath should match X times  //table[@data-test-id='inforequest-attachments-table']/tbody/tr  0
  Element should not be visible by test id  inforequest-attachments-table
