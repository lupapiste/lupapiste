*** Settings ***

Documentation   Open info-request handling
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an info request to Loppi
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Ojatie 1, Loppi

  Mikko logs in
  User role should be  applicant
  Create inforequest the fast way  ${appname}  360834.385  6723358.262  433-406-3-229  kerrostalo-rivitalo  Jiihaa
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  Element should not be visible  //button[@data-test-id='inforequest-convert-to-application']
  Wait Until  Title Should Be  ${appname} - Lupapiste

Email is sent to Loppi rakennusvalvonta
  Open last email
  Wait until  Element Should Contain  xpath=//dd[@data-test-id='subject']  Lupapiste
  Wait until  Element Should Contain  xpath=//dd[@data-test-id='subject']  Neuvontapyyntö

Loppi auth clicks the link in email
  Click link  xpath=//a
  Wait until  User role is oirAuthority
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  User nav menu is not visible
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait until  Element should be visible  //h2[@data-test-id='wanna-join']
  Element should not be visible  inforequest-assignee-edit
  Element should not be visible  application-assignee-edit
  Page should contain  Jiihaa

*** Keywords ***

User role is oirAuthority
  ${user-role}=  Execute JavaScript  return window.lupapisteApp.models.currentUser.role();
  Should Be Equal  ${user-role}  oirAuthority
