*** Settings ***

Documentation   Open info-request handling
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an info request to Loppi
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Ojatie 1, Loppi

  Mikko logs in
  User role should be  applicant
  Create inforequest the fast way  ${appname}  360834.385  6723358.262  433  433-406-3-229  kerrostalo-rivitalo  Jiihaa
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  Element should not be visible  //button[@data-test-id='inforequest-convert-to-application']
  Wait Until  Title Should Be  ${appname} - Lupapiste

Email is sent to Loppi rakennusvalvonta
  Go to last email
  ${subject}=  Execute Javascript  return document.getElementById("subject").innerHTML
  Should Be Equal  ${subject}  Lupapiste.fi: ${appname} - Neuvontapyyntö

Loppi auth clicks the link in email
  Execute Javascript  document.getElementsByTagName("a")[0].click()
  Wait until  User role should be  authority
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  User nav menu is not visible
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Element should be visible  //h2[@data-test-id='wanna-join']
  Element should not be visible  //select[@id='inforequest-assignee-select']
  Element should not be visible  //select[@data-test-id='application-assigneed-authority']
