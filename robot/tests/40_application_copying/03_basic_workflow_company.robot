*** Settings ***

Documentation   Kaino copies an application she created
Resource        ../../common_resource.robot
Resource        ../25_company/company_resource.robot
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Test Cases ***

Kaino creates application
  Kaino logs in
  Create application the fast way  source-application  753-416-25-30  kerrostalo-rivitalo

Kaino invites Mikko to the application
  Invite mikko@example.com to application

Kaino submits application
  Submit application

Kaino starts copying the application
  Element should be visible by test id  copy-application-button
  Click by test id  copy-application-button

Kaino enters the address for the copied application
  Input text  xpath=//input[@id='copy-search']  Latokuja 10, Sipoo
  Click by test id  copy-search-button
  Wait until  Element should be enabled  xpath=//section[@id='copy']//*[@data-test-id='create-continue']
  Click element  xpath=//section[@id='copy']//*[@data-test-id='create-continue']
  Wait until  Element should be visible by test id  copy-selected-location
  Element text should be  //section[@id='copy']//span[@data-test-id='copy-selected-location-text']  753-416-25-28, Latokuja 10, Sipoo

Solita Oy is not listed in the invite candidates
  Xpath Should Match X Times  //section[@id='copy']//div[@id='copy-auths']//input  1
  Element text should be  //section[@id='copy']//div[@id='copy-auths']/div[1]//label  Mikko Intonen, Kirjoitusoikeus

Kaino does not invite Mikko to the copied application
  Click by test id  copy-button-next
  Wait until  Xpath Should Match X Times  //section[@id='copy']//div[@id='copy-auths']/div  0

Kaino clicks the button confirming that she is ready to copy the application
  Click by test id  copy-button-next

Kaino is directed to the copied application
  Wait until  Element Should Be Visible  application
  Wait until  Application state should be  draft
  Wait until  Application address should be  Latokuja 10

Kaino sees an indicator informing that copying was successful
  Positive indicator should be visible
  Indicator should contain text  Hakemus kopioitiin onnistuneesti

Solita Oy is invited to the application even though Kaino did not invite the company
  Open tab  parties
  Is authorized party  Solita Oy
  Is not authorized party  mikko@example.com

## Todo empty emails

*** Keywords ***
