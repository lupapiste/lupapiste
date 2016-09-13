*** Settings ***

Documentation  Authority suggests an appointment
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
  Go to page  organization-calendars
  Set up the calendar for authority  Sibbo Sonja
  Set default reservation location  Foobarbaz
  Add reservation type  Foobar
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

Authority opens and assigns application to herself
  Go to page  applications
  Request should be visible  ${appname}
  Open application  ${appname}  ${propertyId}
  Assign application to  Sibbo Sonja

Authority suggests an appointment
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
  Logout
  
Applicant declines appointment
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  decline-reservation-btn-0
  Click by test id  decline-reservation-btn-0
  Wait until  Element should be visible by test id  reservation-declined-ack-0
  Logout
  
Authority sees 'appointment declined' notification and marks it seen
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Wait until  Element should be visible by test id  mark-seen-reservation-btn-0
  Click by test id  mark-seen-reservation-btn-0
  Open tab  parties
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Element should not be visible by test id  mark-seen-reservation-btn-0

Authority suggests another appointment
  Wait until  Element should be visible by test id  calendar-weekday-0
  Wait until  Select From List by test id  reservation-type-select  Foobar
  Wait until  Select From List by test id  attendee-select  Mikko Intonen
  Goto following week in calendar view
  Wait Until  Element should be visible by test id  reserve-slot-Friday-1100
  Element should not be visible by test id  reservation-PENDING-Friday-1000
  Element should not be visible by test id  reservation-ACCEPTED-Friday-1000
  Click by test id  reserve-slot-Friday-1100
  Wait Until  Element should be visible by test id  reservation-comment-textarea
  Fill test id  reservation-comment-textarea  foobarcomment66
  Click by test id  reservation-slot-reserve-bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should be visible by test id  reservation-PENDING-Friday-1100
  Logout

Applicant accepts appointment
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  accept-reservation-btn-0
  Click by test id  accept-reservation-btn-0
  Wait until  Element should be visible by test id  reservation-accepted-ack-0
  Goto following week in calendar view
  Wait until  Element should be visible by test id  reservation-ACCEPTED-Friday-1100
  Logout
  
Authority sees 'appointment accepted' notification and marks it seen
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  mark-seen-reservation-btn-0
  Click by test id  mark-seen-reservation-btn-0
  Wait until  Element should be visible by test id  reservation-seen-ack-0
  Open tab  parties
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Element should not be visible by test id  mark-seen-reservation-btn-0
  Logout
  
Applicant reserves slot from authority's calendar
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Select From List by test id  reservation-type-select  Foobar
  Wait until  Select From List by test id  attendee-select  Sonja Sibbo
  Goto following week in calendar view
  Wait Until  Element should be visible by test id  reserve-slot-Friday-1200
  Click by test id  reserve-slot-Friday-1200
  Wait Until  Element should be visible by test id  reservation-comment-textarea
  Fill test id  reservation-comment-textarea  diibadaabakommenttia
  Click by test id  reservation-slot-reserve-bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should be visible by test id  reservation-ACCEPTED-Friday-1200
  Logout
  
Authority marks reservation seen
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Wait until  Element should be visible by test id  mark-seen-reservation-btn-0
  Click by test id  mark-seen-reservation-btn-0
  Wait until  Element should be visible by test id  reservation-seen-ack-0
  Goto following week in calendar view
  Wait Until  Element should be visible by test id  reservation-ACCEPTED-Friday-1200
  Logout

Applicant reserves an appointment via new appointment page
  Mikko logs in
  Go to page  new-appointment
  Wait until  Element should be visible by test id  calendar-weekday-0
  Wait until  Select From List by test id and index  application-select  1
  Goto following week in calendar view
  Wait until  Element should be visible  xpath=//*[@data-test-id='reservation-type-select']/option[contains(.,'Foobar')]
  Wait until  Select From List by test id  reservation-type-select  Foobar
  Wait until  Select From List by test id  attendee-select  Sonja Sibbo
  Wait Until  Element should be visible by test id  reserve-slot-Friday-1500
  Click by test id  reserve-slot-Friday-1500
  Wait Until  Element should be visible by test id  reservation-comment-textarea
  Fill test id  reservation-comment-textarea  diibadaabakommenttia
  Click by test id  reservation-slot-reserve-bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should be visible by test id  reservation-ACCEPTED-Friday-1500
  Logout
  
Authority cancels most recent reservation
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  calendar
  Wait until  Element should be visible by test id  calendar-weekday-0
  Goto following week in calendar view
  Wait until  Element should be visible  xpath=//*[@data-test-id='reservation-type-select']/option[contains(.,'Foobar')]
  Wait until  Element should be visible by test id  reservation-ACCEPTED-Friday-1500
  Click by test id  reservation-ACCEPTED-Friday-1500
  Wait Until  Element should be visible by test id  reservation-comment-textarea
  Wait until  Element should be visible by test id  reserved-slot-bubble-dialog-remove
  Click by test id  reserved-slot-bubble-dialog-remove
  #Confirm yes no dialog
  Click by test id  confirm-yes
  Positive indicator should be visible
  Wait until  Element should not be visible by test id  reservation-ACCEPTED-Friday-1500
  Logout