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

Mikko can see invite paasuunnittelija button
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko invites Teppo
  Invite count is  0
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Sleep  1
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
  Click by test id  application-invite-submit
  Wait until  Element should not be visible  invite-email
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-remove-invite']
  Invite count is  1

Mikko can't reinvite Teppo
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Sleep  1
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan taas hakemusta
  Click by test id  application-invite-submit
  Invite count is  1

# TODO: cant remove auth for owner
# TODO: can remove auth for someone else

Mikko leaves and Teppo logs in
  Logout
  Teppo logs in
  
Teppo can see the invite
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Teppo can edit Mikko's application
  Open application  invite-app
  Input text  //label[text()='Rakennuksen kokonaisala']/following-sibling::*/input  1024
  Logout

Mikko comes back and can see Teppos modification
  Mikko logs in
  Open application  invite-app
  Wait Until  Textfield Value Should Be  xpath=//label[text()='Rakennuksen kokonaisala']/following-sibling::*/input  1024

Mikko can see invite paasuunnittelija button again
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko can't invite himself
  Click by test id  application-invite-paasuunnittelija
  Input Text  invite-email  mikko@example.com
  Input Text  invite-text  Voinko kutsua itseni?
  Click by test id  application-invite-submit
  Invite count is  0

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

*** Keywords ***

Invite count is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //li[@class='user-invite']  ${amount}
