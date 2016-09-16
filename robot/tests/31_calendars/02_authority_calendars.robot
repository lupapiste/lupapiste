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
  Set up the calendar for authority  Sibbo Ronja
  Set default reservation location  Foobarbaz
  Add reservation type  Foobar
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

Goto following week view
  Goto following week in calendar view

Create reservation slots for Friday next week
  Element should not be visible  xpath=//div[@class='calendar-slot-bubble']//h3
  Click by test id  timeline-slot-Friday-1000
  Wait until  Element should be visible by test id  reservation-slot-create-amount
  Fill test id  reservation-slot-create-amount  6
  Select Checkbox  xpath=//input[@data-test-id='reservation-slot-create-type-checkbox-0']
  Scroll and click test id  reservation-slot-create-bubble-dialog-ok
  Wait until  Element should not be visible by test id  reservation-slot-create-amount
  Wait Until  Page should contain  Foobar
