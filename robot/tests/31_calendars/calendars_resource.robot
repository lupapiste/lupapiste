*** Settings ***

Documentation   Calendar utils
Library         DateTime
Resource        ../../common_resource.robot

*** Keywords ***

Clear ajanvaraus-db
  Go to  ${SERVER}/dev/ajanvaraus/clear
  Wait until  Page should contain  true
  Go to login page

Basic calendar setup for Sonja
  Go to page  organization-calendars
  Set up the calendar for authority  Sibbo Sonja
  Set default reservation location  Foobarbaz
  Add reservation type  Foobar

Apply minimal fixture and clear ajanvaraus-db
  Apply minimal fixture now
  Clear ajanvaraus-db

Set up the calendar for authority
  [Arguments]  ${authority}
  Wait Until  Element should be visible  xpath=//tr[@data-test-authority-name='${authority}']
  Select Checkbox  xpath=//tr [@data-test-authority-name='${authority}']//td//input[@type='checkbox']
  Positive indicator should be visible

Set default reservation location
  [Arguments]  ${location}
  ${save_indicator} =  Set Variable  xpath=//section[@id="organization-calendars"]//table[@data-test-id="reservation-properties-table"]//span[contains(@class, "save-indicator")]
  Element should not be visible  ${save_indicator}
  Input text by test id  organization-default-location  ${location}
  Wait Until  Element should be visible  ${save_indicator}

Add reservation type
  [Arguments]  ${name}
  Click by test id  add-reservation-type
  Wait until  Element should be visible  dialog-edit-reservation-type
  Input text by test id  reservation-type-name  ${name}
  Confirm   dialog-edit-reservation-type
  Positive indicator should be visible
  Wait until  Element should be visible  //table[@data-test-id='organization-reservation-types']//td[text()='${name}']

Goto following week in calendar view
  Wait for jQuery
  ${timestampStr}=  Get Element Attribute  xpath=//td[@data-test-id='calendar-weekday-0']@data-test-timestamp
  ${monday}=  Convert To Integer  ${timestampStr}
  Click by test id  calendar-view-following-week
  ${nextMonday}=  Add Time To Date  ${monday}  7 days  epoch
  ${nextMondayEpoch}=  Convert To Integer  ${nextMonday}
  Wait Until Page Contains Element  xpath=//td[@data-test-timestamp='${nextMondayEpoch}']
  Wait for jQuery

Assign application to
  [Arguments]  ${to}
  Wait Until  Element Should Be Visible  jquery=[data-test-id=assignee-select]:visible
  Select From List By Label  jquery=[data-test-id=assignee-select]:visible  ${to}
