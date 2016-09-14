*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot

*** Variables ***
  
${notice}  Hakmuss on tosi kiirreeliene!

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
  #  Fill test id  application-authority-notice  
  Press key  application-authority-notice  ${notice}
  Sleep  1s
  # The following letter is missed by Selenium
  Press key  application-authority-notice  a    
  

Sonja can set application urgency to urgent
  Select From List by id  application-authority-urgency  urgent
  # wait for debounce
  Sleep  2
  Wait for jquery
  Check notice  ylämaa  urgent  ${notice}a
  Close side panel  notice
  Logout

Ronja can see urgent application
  Ronja logs in
  Wait until  Element should be visible  //div[contains(@class, 'urgent')]

Ronja can click notice icon -> application page is opened with notice panel open
  Click element  xpath=//td[@data-test-col-name='urgent']/div
  Wait until  Element should be visible  notice-panel
  Check notice  ylämaa  urgent  ${notice}
  Logout

Sonja can set application urgency to pending
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open side panel  notice
  Select From List by id  application-authority-urgency  pending
  # wait for debounce
  Sleep  1
  Logout

Ronja can see pending application
  Ronja logs in
  Request should be visible  ${appname}
  Wait until  Element should be visible  //div[contains(@class, 'pending')]
  Logout


*** Keywords ***

Check notice
  [Arguments]  ${tag}  ${urgency}  ${note}
  # Tags do not work yet with Selenium
  #Wait Until  Element text should be  jquery=li.tag span.tag-label  ${tag}  
  Wait Until  List Selection Should Be  application-authority-urgency  ${urgency}
  Textarea value should be  application-authority-notice  ${note}
  
