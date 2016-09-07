*** Settings ***

Documentation   Application invites
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  invite${secs}
  Set Suite Variable  ${appnameUC}  INVITE${secs}
  Set Suite Variable  ${propertyId}  753-416-5-5
  Create application with state  ${appname}  ${propertyId}  kerrostalo-rivitalo  open

Mikko can see the general invite button and opens invite dialog with it
  Open tab  parties
  Open accordions  parties
  Fill application person invite bubble  teppo@example.com  Tervetuloa muokkaamaan hakemusta
  Scroll and click test id  person-invite-bubble-dialog-cancel

Mikko can see invite paasuunnittelija button
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko invites Teppo
  Invite Teppo

Mikko can't reinvite Teppo
  Open accordions  parties
  Click by test id  application-invite-paasuunnittelija
  Wait until  Element should be visible  invite-email
  Sleep  1
  Input Text  invite-email  teppo@example.com
  Input Text  invite-text  Tervetuloa muokkaamaan taas hakemusta
  Click by test id  application-invite-submit
  Error message is present on invite form
  Invite count is  1
  [Teardown]  logout

Teppo declines invitation
  Teppo logs in
  Wait until  Element should be visible  xpath=//*[@data-test-id='decline-invite-button']
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  ${appname}, Sipoo,
  Element Text Should Be  xpath=//div[@class='invitation'][1]//p[@data-test-id='invitation-text-0']  Tervetuloa muokkaamaan hakemusta
  Click by test id  decline-invite-button
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//*[@data-test-id='decline-invite-button']
  [Teardown]  logout

Mikko reinvites Teppo
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  parties
  Open accordions  parties
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']
  Invite Teppo
  [Teardown]  logout

Teppo can view application
  Teppo logs in
  Wait Until  Element should be visible  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Click element  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Deny yes no dialog
  Application address should be  ${appnameUC}
  Go to page  applications

Teppo accepts invitation
  Wait until  Element should be visible  xpath=//*[@data-test-id='accept-invite-button']
  Element Should Contain  xpath=//div[@class='invitation'][1]//h3  ${appname}, Sipoo,
  Element Text Should Be  xpath=//div[@class='invitation']//p[@data-test-id='invitation-text-0']  Tervetuloa muokkaamaan hakemusta
  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//*[@data-test-id='accept-invite-button']

Teppo can edit Mikko's application
  Open application  ${appname}  ${propertyId}
  Open accordions  info
  # OnChange event does not seem to get triggered. Do it manually.
  Click by test id  maaraala-checkbox
  Input text with jQuery  input[data-test-id="kiinteisto.maaraalaTunnus"]  1024
  Textfield Value Should Be  xpath=//input[@data-test-id='kiinteisto.maaraalaTunnus']  1024
  Wait Until  Element should be visible  //*[@data-test-id='save-indicator']
  [Teardown]  logout

Mikko comes back and can see Teppos modification
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Wait Until  Textfield Value Should Be  xpath=//input[contains(@data-test-id,'kiinteisto.maaraalaTunnus')]  1024

Mikko can see that Teppo has accepted invitation
  Open tab  parties
  Open accordions  parties
  # Check that invite accepted timestamp span is present
  Element should be visible  xpath=//*[@data-test-id='invite-accepted-span']

Only one unsubscribe button
  Xpath Should Match X Times  //*[@data-test-id='unsubscribeNotifications']  1

Mikko can see invite paasuunnittelija button again
  Element should be visible  xpath=//*[@data-test-id='application-invite-paasuunnittelija']

Mikko can't invite himself
  Click by test id  application-invite-paasuunnittelija
  Input Text  invite-email  mikko@example.com
  Input Text  invite-text  Voinko kutsua itseni?
  Click by test id  application-invite-submit
  Error message is present on invite form
  Invite count is  0

Mikko invites Solita
  Invite company to application  Solita Oy

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
  Open application  ${appname}  ${propertyId}
  [Teardown]  logout

Sonja (the Authority) is not allowed to invite people
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Element should not be visible  xpath=//*[@data-test-id='application-add-invite']
  [Teardown]  logout

Mikko invites previously unknown user Oskari as paasuunnittelija
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  parties
  Open accordions  parties
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


# Tyonjohtaja on poistettu tavallisilta R-hakemuksilta (LUPA-1603).
# Testataan tyonjohtajan kutsuminen erikseen omalla hakemuksellaan.
Mikko creates a new tyonjohtaja application
  Create application the fast way  ${appname}-tj  ${propertyId}  tyonjohtajan-nimeaminen

Mikko invites previously unknown user Unto as tyonjohtaja
  Open tab  parties
  Open accordions  parties
  Wait until  Element should be visible  xpath=//div[@id="application-parties-tab"]//*[@data-test-id='application-invite-tyonjohtaja']
  Click by test id  application-invite-tyonjohtaja
  Wait until  Element should be visible  invite-email
  Input Text  invite-email  unto@example.com
  Input Text  invite-text  Tuu mulle tyonjohtajaksi
  Click by test id  application-invite-submit
  Wait until  Mask is invisible
  Wait until  Element should not be visible  invite-email
  Wait until  Invite count is  1

Unto hasn't accepted auth, so it can't be upgreded
  Element should not be visible by test id  change-auth-unto@example.com

Mikko deletes auhtorzation from Unto
  Click by test id  remove-auth-unto@example.com
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Invite count is  0

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
