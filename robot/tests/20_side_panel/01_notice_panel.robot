*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Mikko can't see notice button
  Open application  ${appname}  ${propertyId}
  Wait until  Element should not be visible  //div[@id='side-panel']//button[@id='open-notice-side-panel']

Mikko opens application to authorities
  Open to authorities  let me entertain you
  Logout

Sonja can see notice button
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Wait until  Element should be visible  //div[@id='side-panel']//button[@id='open-notice-side-panel']

Sonja can add tags
  Open side panel  notice
  Select From Autocomplete  div[@id="notice-panel"]  ylämaa

Sonja can leave notice
  Open side panel  notice
  Input text  xpath=//div[@id='notice-panel']//textarea[@data-test-id='application-authority-notice']  Hakmuss on tosi kiirreeliene!

Sonja can set application urgency to urgent
  Open side panel  notice
  Select From List by test id  notice-urgency-select  Kiireellinen
  # wait for debounce
  Sleep  1
  Logout

Ronja can see urgent application
  Ronja logs in
  Wait until  Element should be visible  //td[@data-test-col-name='urgent']//div[@class='urgency urgent']
  Logout

Sonja can set application urgency to pending
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open side panel  notice
  Select From List by test id  notice-urgency-select  Odottaa
  # wait for debounce
  Sleep  1
  Logout

Ronja can see pending application
  Ronja logs in
  Request should be visible  ${appname}
  Wait until  Element should be visible  //td[@data-test-col-name='urgent']//div[@class='urgency pending']
  Logout
