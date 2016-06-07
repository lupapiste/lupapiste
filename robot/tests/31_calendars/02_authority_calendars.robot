*** Settings ***

Documentation  Authority edits their own calendar
Suite Setup    Apply minimal fixture and clear ajanvaraus-db
Suite Teardown  Logout
Default Tags    ajanvaraus
Resource       ../../common_resource.robot
Resource       ./calendars_resource.robot

*** Test Cases ***

Admin enables calendar fo Ronja
  Sipoo logs in
  Go to page  organization-calendars
  Wait until  Element should be visible by test id  calendar-checkbox-0
  Select Checkbox  xpath=//input[@data-test-id='calendar-checkbox-0']
  Positive indicator should be visible
  Logout

Sonja tries to look at her own calendar but gets an error message
  Sonja logs in
  Go to page  mycalendar
  Wait until  Element should be visible by test id  mycalendar-no-active-calendar-error
  Logout

Ronja looks at her own calendar
  Ronja logs in
  Go to page  mycalendar
  Wait until  Element should be visible by test id  calendar-view-calendar-table
  Wait until  Element should be visible by test id  timeline-slot-Friday-1000





