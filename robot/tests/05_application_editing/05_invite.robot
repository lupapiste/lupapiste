*** Settings ***

Documentation   Application invites
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application the fast way  invite-app  753  753-416-25-25  asuinrakennus
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  753-416-25-25

Mikko can see invite paasuunnittelija button
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko invites Teppo
  Invite Teppo

Mikko can't reinvite Teppo
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Sleep  1
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan taas hakemusta
  Click by test id  application-invite-submit
  Error message is present on invite form
  Invite count is  1

# TODO: cant remove auth for owner
# TODO: can remove auth for someone else
  [Teardown]  logout

Teppo declines invitation
  Teppo logs in
  Wait until  Element should be visible  xpath=//*[@data-test-id='decline-invite-button']
  Click by test id  decline-invite-button
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//*[@data-test-id='decline-invite-button']
  [Teardown]  logout

Mikko reinvites Teppo
  Mikko logs in
  Open application  invite-app  753-416-25-25
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']
  Invite Teppo
  [Teardown]  logout

Teppo accepts invitation
  Teppo logs in
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Teppo can edit Mikko's application
  Open application  invite-app  753-416-25-25
  # OnChange event does not seem to get triggered. Do it manually.
  Execute Javascript  $("input[id$='kiinteisto-maaraalaTunnus']").val("1024").change();
  Wait Until  Page Should Contain  Tiedot tallennettu
  Textfield Value Should Be  xpath=//input[contains(@id,'kiinteisto-maaraalaTunnus')]  1024
  [Teardown]  logout

Mikko comes back and can see Teppos modification
  Mikko logs in
  Open application  invite-app  753-416-25-25
  Wait Until  Textfield Value Should Be  xpath=//input[contains(@id,'kiinteisto-maaraalaTunnus')]  1024

Mikko can see invite paasuunnittelija button again
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko can't invite himself
  Click by test id  application-invite-paasuunnittelija
  Input Text  invite-email  mikko@example.com
  Input Text  invite-text  Voinko kutsua itseni?
  Click by test id  application-invite-submit
  Error message is present on invite form
  Invite count is  0

Mikko adds comment so thate application will be visible to admin
  Add comment  Woe to you, Oh Earth and Sea, for the Devil sends the beast with wrath, because he knows the time is short...

Mikko decides to go to the desert, put on his ipod, and listen some some British hard-rock band
  Logout

Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Open application  invite-app  753-416-25-25
  Element should not be visible  xpath=//*[@data-test-id='application-add-invite']
  [Teardown]  logout

Mikko invites previously unknown user Oskari as paasuunnittelija
  Mikko logs in
  Open application  invite-app  753-416-25-25
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  oskari@example.com
  Input Text  invite-text  Tuu mukaan tunkkaan lupaa
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1

# TODO: should create new user "oskari@example.com" and make sure he has access

Mikko invites previously unknown user Unto as tyonjohtaja
  Element should be visible  xpath=//*[@data-test-id='application-invite-tyonjohtaja']
  Click by test id  application-invite-tyonjohtaja
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  unto@example.com
  Input Text  invite-text  Tuu mulle tyonjohtajaksi
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  2

*** Keywords ***

Error message is present on invite form
  Wait until  Element should be visible  xpath=//div[@id='dialog-valtuutus']//div[@class='context-error']
  Click Element  xpath=//div[@id='dialog-valtuutus']//p[contains(@class,'close')]
  Wait until  Mask is invisible

Mask is invisible
  Element should not be visible  xpath=//div[@id='ModalDialogMask']

Invite count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  //*[@class='user-invite']  ${amount}

Invite Teppo
  Invite count is  0
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1
