*** Settings ***

Documentation   Application invites
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application the fast way  invite-app  753  75341600250025
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-title']  invite-app
  
Mikko can see invitation button
  Element should be visible  xpath=//*[@data-test-id='application-add-invite']

Mikko adds comment so thate application will be visible to admin
  Add comment  Woe to you, Oh Earth and Sea, for the Devil sends the beast with wrath, because he knows the time is short...

Mikko decides to go to the desert, put on his ipod, and listen some some British hard-rock band 
  Logout
  
Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Wait until  Element should be visible  xpath=//section[@id='applications']//td[text()='invite-app']
  Click element  xpath=//section[@id='applications']//td[text()='invite-app']
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-title']  invite-app
  Element should not be visible  xpath=//*[@data-test-id='application-add-invite']
  Logout
