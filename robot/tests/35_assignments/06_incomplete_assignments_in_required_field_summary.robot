*** Settings ***

Documentation   Authority sees incomplete assignments in the required fields summary
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Do
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo
  Submit application
  Logout


Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Tab should be visible  info

Sonja creates assignment for Ronja about paasuunnittelija
  Open tab  parties
  Create assignment  Ronja Sibbo  parties  paasuunnittelija  Katoppa tää

Sonja opens the required fields summary
  Open tab  requiredFieldSummary

Sonja can see one incomplete assignment
  Wait until  Xpath Should Match X Times  //tr[@data-test-id='incomplete-assignment']  1

Sonja cliks clicks the the assignment link and completes the assignment
  Click by test id  show-assignment
  Click by test id  mark-assignment-complete

Sonja opens the required fields summary and no longer sees incomplete assignments
  Open tab  requiredFieldSummary
  Wait until  Xpath Should Match X Times  //tr[@data-test-id='incomplete-assignment']  0
