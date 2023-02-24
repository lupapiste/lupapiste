*** Settings ***

Documentation  Authority admin edits organization calendars
Suite Setup    Apply minimal fixture and clear ajanvaraus-db
Suite Teardown  Logout
Default Tags    ajanvaraus
Resource       ../../common_resource.robot
Resource       ./calendars_resource.robot

*** Test Cases ***

Admin sets default reservation location
  Sipoo logs in
  Go to page  organization-calendars
  Set default reservation location  Foobarbaz

Admin adds reservation type for organization
  Add reservation type  Foobar

Admin enables calendar
  Wait until  Element should be visible by test id  calendar-checkbox-0
  Positive indicator should not be visible
  Checkbox Should Not Be Selected  xpath=//input[@data-test-id='calendar-checkbox-0']
  Select Checkbox  xpath=//input[@data-test-id='calendar-checkbox-0']
  Positive indicator should be visible

Admin disables calendar
  Wait until  Element should be visible by test id  calendar-checkbox-0
  Positive indicator should not be visible
  Checkbox Should Be Selected  xpath=//input[@data-test-id='calendar-checkbox-0']
  Unselect Checkbox  xpath=//input[@data-test-id='calendar-checkbox-0']
  Positive indicator should be visible
  Logout
