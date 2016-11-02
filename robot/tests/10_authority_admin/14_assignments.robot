*** Settings ***

Documentation   Authority admin creates users
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot


*** Test Cases ***


Assignments are enabled in Sipoo
  Sonja logs in
  Create application the fast way  Tehtäväpolku  753-1-1-2  teollisuusrakennus
  Go to page  applications
  Wait until  Element should be visible  jquery=label[for=searchTypeAssignments]
  Logout

Authority admin goes to the application page
  Sipoo logs in
  Go to page  applications

Sees assignments are enabled
  Checkbox should be selected  assignments-enabled

Disables assignments for orgainzation
  Unselect checkbox  assignments-enabled
  Wait until  Positive indicator should be visible
  Checkbox should not be selected  assignments-enabled
  Logout

Sonja logs in and sees assignments are not enabled
  Sonja logs in
  Wait until  Element should be visible  jquery=label[for=searchTypeApplications]
  Element should not be visible  jquery=label[for=searchTypeAssignments]
  Logout
