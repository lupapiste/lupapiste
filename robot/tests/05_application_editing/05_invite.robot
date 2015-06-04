*** Settings ***

Documentation   Application invites
Suite setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  Create application the fast way  invite-app  753-423-2-159  kerrostalo-rivitalo

Mikko can see the general invite button and opens invite dialog with it
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-person']
  Click by test id  application-invite-person
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
  Element should be enabled  xpath=//*[@data-test-id='application-invite-submit']
  Click Element  xpath=//div[@id='dialog-valtuutus']//p[contains(@class,'close')]
  Wait until  Mask is invisible

Mikko can see invite paasuunnittelija button
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
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  invite-app, Sipoo,
  Element Text Should Be  xpath=//div[@class='invitation'][1]//p[@data-test-id='invitation-text-0']  Tervetuloa muokkaamaan hakemusta
  Click by test id  decline-invite-button
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//*[@data-test-id='decline-invite-button']
  [Teardown]  logout

Mikko reinvites Teppo
  Mikko logs in
  Open application  invite-app  753-423-2-159
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']
  Invite Teppo
  [Teardown]  logout

Teppo can view application
  Teppo logs in
  Wait Until  Element should be visible  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Click element  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Wait Until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-title']  INVITE-APP
  Go to page  applications

Teppo accepts invitation
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  invite-app, Sipoo,
  Element Text Should Be  xpath=//div[@class='invitation']//p[@data-test-id='invitation-text-0']  Tervetuloa muokkaamaan hakemusta
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Teppo can edit Mikko's application
  Open application  invite-app  753-423-2-159
  # OnChange event does not seem to get triggered. Do it manually.
  Execute Javascript  $("input[id$='kiinteisto-maaraalaTunnus']").val("1024").change();
  Textfield Value Should Be  xpath=//input[contains(@id,'kiinteisto-maaraalaTunnus')]  1024
  [Teardown]  logout

Mikko comes back and can see Teppos modification
  Mikko logs in
  Open application  invite-app  753-423-2-159
  Wait Until  Textfield Value Should Be  xpath=//input[contains(@id,'kiinteisto-maaraalaTunnus')]  1024

Mikko can see that Teppo has accepted invitation
  Open tab  parties
  # Check that invite accepted timestamp span is present
  Element should be visible  xpath=//*[@data-test-id='invite-accepted-span']

Only one unsubscribe link
  Xpath Should Match X Times  //a[contains(text(),'Peruuta sähköposti-ilmoitukset')]  1

Mikko can see invite paasuunnittelija button again
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko can't invite himself
  Click by test id  application-invite-paasuunnittelija
  Input Text  invite-email  mikko@example.com
  Input Text  invite-text  Voinko kutsua itseni?
  Click by test id  application-invite-submit
  Error message is present on invite form
  Invite count is  0

Mikko adds comment so thate application will be visible to admin
  Open to authorities  Woe to you, Oh Earth and Sea, for the Devil sends the beast with wrath, because he knows the time is short...

Mikko invites Solita
  # Click enabled by test id  company-invite
  Wait Until  Click Element  xpath=//div[@class='parties-list']//button[@data-test-id='company-invite']
  Wait Until  Element should be visible  xpath=//div[@data-test-id='modal-dialog-content']
  Element should not be visible  xpath=//div[@data-test-id='company-invite-confirm-help']
  Select From Autocomplete  Solita Oy
  Click enabled by test id  modal-dialog-submit-button
  Wait Until  Element should be visible  xpath=//div[@data-test-id='company-invite-confirm-help']
  Click enabled by test id  modal-dialog-submit-button
  Wait Until  Page should contain  1060155-5

Mikko decides to go to the desert, put on his ipod, and listen some some British hard-rock band
  Logout

Solita accepts invite
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  kaino@solita.fi
  Click Element  xpath=(//a)[2]
  Wait until  Page should contain  Hakemus on liitetty onnistuneesti yrityksen tiliin.
  [Teardown]  Go to login page

Kaino Solita logs in and opens the application
  User logs in  kaino@solita.fi  kaino123  Kaino Solita
  Open application  invite-app  753-423-2-159
  [Teardown]  logout

Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Open application  invite-app  753-423-2-159
  Element should not be visible  xpath=//*[@data-test-id='application-add-invite']
  [Teardown]  logout

Mikko invites previously unknown user Oskari as paasuunnittelija
  Mikko logs in
  Open application  invite-app  753-423-2-159
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


#  TODO: Tyonjohtaja on poistettu tavallisilta R-hakemuksilta (LUPA-1603).
#        Testataan tyonjohtajan kutsuminen erikseen omalla hakemuksellaan.
Mikko creates a new tyonjohtaja application
  #Mikko logs in
  Create application the fast way  invite-app-tyonjohtaja  753-423-2-159  tyonjohtajan-nimeaminen

Mikko can see invite tyonjohtaja button in parties tab
  Open tab  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-tyonjohtaja']

Mikko invites previously unknown user Unto as tyonjohtaja
  Element should be visible  xpath=//*[@data-test-id='application-invite-tyonjohtaja']
  Click by test id  application-invite-tyonjohtaja
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  unto@example.com
  Input Text  invite-text  Tuu mulle tyonjohtajaksi
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1

*** Keywords ***

Error message is present on invite form
  Wait until  Element should be visible  xpath=//div[@id='dialog-valtuutus']//div[@class='context-error']
  Click Element  xpath=//div[@id='dialog-valtuutus']//p[contains(@class,'close')]
  Wait until  Mask is invisible

Mask is invisible
  Element should not be visible  xpath=//div[@id='ModalDialogMask']

Invite Teppo
  Invite count is  0
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Input Text  invite-text  Tervetuloa muokkaamaan hakemusta
  Element should be disabled  xpath=//*[@data-test-id='application-invite-submit']
  Input Text  invite-email  teppo@example
  Element should be disabled  xpath=//*[@data-test-id='application-invite-submit']
  Input Text  invite-email  teppo@example.com
  Element should be enabled  xpath=//*[@data-test-id='application-invite-submit']
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1
