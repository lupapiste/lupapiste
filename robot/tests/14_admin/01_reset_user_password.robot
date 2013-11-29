*** Settings ***

Documentation   Admin edits authority admin users
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Solita admin logs in and goes to 'users' page
  SolitaAdmin logs in
  Wait until  Element should be visible     xpath=//a[@data-test-id='users']
  Click element                             xpath=//a[@data-test-id='users']
  Wait until  Element should be visible     xpath=//section[@id='users']
  
Solita admin search Ronja and resets her password
  Wait until     Element should be visible  xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']
  Input text                                xpath=//section[@id='users']//input[@data-test-id='users-list-input-search']  ronj
  Wait until     Element should be visible  xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/a[@data-op='resetPassword']
  Click element                             xpath=//section[@id='users']//tr[@data-user-email='ronja.sibbo@sipoo.fi']//td/a[@data-op='resetPassword']
  Wait until     Element should be visible  xpath=//div[@id='dynamic-yes-no-confirm-dialog']//button[@data-test-id='confirm-yes']
  Click element                             xpath=//div[@id='dynamic-yes-no-confirm-dialog']//button[@data-test-id='confirm-yes']
  
Ronja receives an email
  Go to          ${SERVER}/api/last-email
  Wait until     Element should contain  id=to  ronja.sibbo@sipoo.fi
  Click element  xpath=//a
  Wait until     Element should be visible  xpath=//section[@id='setpw']
