*** Settings ***

Documentation   Inforequest state handling
Suite teardown  Logout
Resource        ../../common_resource.txt

*** Test Cases ***

Applicant marks inforequest answered
  Mikko logs in
  Wait and click  test-from-applications-to-inforequests-tab
  Wait until  Number of visible applications on page  inforequests  2
  Open inforequest
  Wait and click  test-mark-inforequest-answered
  Wait until  Inforequest state is  Vastattu
  Logout

*** Keywords ***

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element should contain  test-inforequest-state  ${state}
 
  