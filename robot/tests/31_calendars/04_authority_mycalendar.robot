*** Settings ***

Documentation  Authority mycalendar page tests
Suite Setup    Apply minimal fixture and clear ajanvaraus-db
Suite Teardown  Logout
Default Tags    ajanvaraus
Resource       ../../common_resource.robot
Resource       ./calendars_resource.robot

Library  Collections

*** Variables ***

*** Test Cases ***

Admin sets up the calendar
  Sipoo logs in
  Basic calendar setup for Sonja
  Logout

Applicant opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Applicant submits application and logs out
  Submit application
  Logout

Authority logs in and allocates free calendar slots
  Sonja logs in
  Go to page  mycalendar
  Wait until  Element should be visible by test id  calendar-view-calendar-table
  Goto following week in calendar view
  Wait until  Element should be visible by test id  timeline-slot-Friday-1000
  Click by test id  timeline-slot-Friday-1000
  Wait until  Element should be visible by test id  reservation-slot-create-amount
  Fill test id  reservation-slot-create-amount  6
  Select Checkbox  xpath=//input[@data-test-id='reservation-slot-create-type-checkbox-0']
  Scroll and click test id  reservation-slot-create-bubble-dialog-ok
  Wait until  Element should not be visible by test id  reservation-slot-create-amount
  Wait Until  Page should contain  Foobar

Authority suggests an appointment
  Go to page  applications
  Request should be visible  ${appname}
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Wait until  Select From List by test id  reservation-type-select  Foobar
  Wait until  Select From List by test id  attendee-select  Mikko Intonen
  Goto following week in calendar view
  Wait Until  Element should be visible by test id  reserve-slot-Friday-1000
  Click by test id  reserve-slot-Friday-1000
  Wait Until  Element should be visible by test id  reservation-comment-textarea
  Fill test id  reservation-comment-textarea  diibadaabakommenttia
  Click by test id  reservation-slot-reserve-bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should be visible by test id  reservation-PENDING-Friday-1000

Appointment is visible in mycalendar list view
  Go to page  mycalendar
  Click by test id  view-toggle-list
  Wait until  Element should be visible  xpath=//div[@class='calendar-pending-action-item']
  Logout

