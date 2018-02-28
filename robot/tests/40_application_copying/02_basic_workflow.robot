*** Settings ***

Documentation   Sonja copies an application Mikko created
Resource        ../../common_resource.robot
Resource        ../25_company/company_resource.robot
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Test Cases ***

Mikko creates and submits application
  Mikko logs in
  Create application the fast way  source-application  753-416-25-30  kerrostalo-rivitalo
  Submit application
  Logout

Sonja opens Mikko's application
  Sonja logs in
  Open application  source-application  753-416-25-30
  Element should be visible by test id  copy-application-button

Sonja starts copying the application
  Click by test id  copy-application-button

Sonja enters the address for the copied application
  Input text  xpath=//input[@id='copy-search']  Latokuja 10, Sipoo
  Click by test id  copy-search-button
  Wait until  Element should be enabled  xpath=//section[@id='copy']//*[@data-test-id='create-continue']
  Click element  xpath=//section[@id='copy']//*[@data-test-id='create-continue']
  Wait until  Element should be visible by test id  copy-selected-location
  Element text should be  //section[@id='copy']//span[@data-test-id='copy-selected-location-text']  753-416-25-28, Latokuja 10, Sipoo

Sonja invites Pena to the copied application
  Xpath Should Match X Times  //section[@id='copy']//div[@id='copy-auths']//input  1
  Element text should be  //section[@id='copy']//div[@id='copy-auths']//label  Mikko Intonen, Hakija
  Click element  xpath=//section[@id='copy']//div[@id='copy-auths']//label
  Click by test id  copy-button-next
  Wait until  Xpath Should Match X Times  //section[@id='copy']//div[@id='copy-auths']/div  1
  Element text should be  //section[@id='copy']//span[@data-test-id='selected-auth-text-0']  Mikko Intonen, Hakija

Sonja clicks the button confirming that she is ready to copy the application
  Click by test id  copy-button-next

Sonja is directed to the copied application
  Wait until  Element Should Be Visible  application
  Wait until  Application state should be  open
  Wait until  Application address should be  Latokuja 10

Sonja sees an indicator informing that copying was successful
  Positive indicator should be visible
  Indicator should contain text  Hakemus kopioitiin onnistuneesti

Mikko is invited to the copied Application
  Open tab  parties
  Is authorized party  mikko@example.com

Mikko receives invitation email to the copied Application
  Open last email
  Wait until  Page should contain  Sinut halutaan valtuuttaa kirjoitusoikeudella osapuoleksi Lupapisteessä olevaan hankkeeseen sähköpostiosoitteella mikko@example.com
  Click link  xpath=//a
