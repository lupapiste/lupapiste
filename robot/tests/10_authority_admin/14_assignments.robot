*** Settings ***

Documentation   Authority admin creates users
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot


*** Test Cases ***


Assignments are enabled in Sipoo
  Sonja logs in
  Create application the fast way  Tehtäväpolku  753-1-1-2  teollisuusrakennus
  Open tab  parties
  Wait until  Element should be visible  xpath=//button[@data-test-id='create-assignment-editor-button']
  Go to page  applications
  Wait until  Element should be visible  jquery=label[for=searchTypeAssignments]
  Logout

Authority admin goes to the application page
  Sipoo logs in
  Go to page  assignments

Sees assignments are enabled
  Checkbox wrapper selected by test id  assignments-enabled

Disables assignments for organization
  Toggle toggle  assignments-enabled
  Wait until  Positive indicator should be visible
  Checkbox wrapper not selected by test id  assignments-enabled
  Logout

Sonja logs in and sees assignments are not enabled
  Sonja logs in
  Wait until  Element should be visible  jquery=label[for=searchTypeApplications]
  Element should not be visible  jquery=label[for=searchTypeAssignments]
  Open application  Tehtäväpolku  753-1-1-2
  Open tab  parties
  Wait until  Element should not be visible  xpath=//button[@data-test-id='create-assignment-editor-button']
  Logout
