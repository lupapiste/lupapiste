*** Settings ***

Documentation   Open info-request handling
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates an info request to Loppi
  Mikko logs in
  User role should be  applicant
  Create inforequest the fast way  Ojatie 1, Loppi  433  433-406-3-229  asuinrakennus  Jiihaa
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Mikko Intonen
  Element should not be visible  //button[@data-test-id='inforequest-convert-to-application']

Email is sent to Loppi rakennusvalvonta
  Execute Javascript  window.location = "/api/last-email";
  Wait until  Element should be visible  //*[@id='subject']
  ${subject}=  Execute Javascript  return document.getElementById("subject").innerHTML
  Should Be Equal  ${subject}  Lupapiste.fi: Ojatie 1, Loppi - Neuvontapyyntö

Loppi auth clicks the link in email
  Execute Javascript  document.getElementsByTagName("a")[0].click()
  Wait until  User role should be  authority
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Mikko Intonen
  User nav menu is not visible
  Element should be visible  //h2[@data-test-id='wanna-join']
  Element should not be visible  //select[@id='inforequest-assignee-select']
  Element should not be visible  //select[@data-test-id='application-assigneed-authority']
