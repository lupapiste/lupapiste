*** Settings ***

Documentation   Sonja should see only applications from Sipoo
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko opens an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

# Testing the case that was fixed with hotfix/repeating-element-saving
# and later regression LUPA-1784
# Note: The test browser must be active or the case will fail.
Mikko adds three owners to the Uusirakennus document
  Open accordions  info
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  1

  Input text with jQuery  \#application-info-tab section[data-doc-type="uusiRakennus"] input[data-docgen-path="rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi"]  pikku

  Wait Until  Element Should Be Visible  //button[@id="rakennuksenOmistajat_append"]
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi']
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi']
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi']
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  4

  Input text with jQuery  \#application-info-tab section[data-doc-type="uusiRakennus"] input[data-docgen-path="rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi"]  ISO1
  Input text with jQuery  \#application-info-tab section[data-doc-type="uusiRakennus"] input[data-docgen-path="rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi"]  ISO2
  Input text with jQuery  \#application-info-tab section[data-doc-type="uusiRakennus"] input[data-docgen-path="rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi"]  ISO3
  Wait for jQuery

Owners are visible after page refresh
  Reload Page
  Open accordions  info
  Wait Until  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  4
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi']  pikku
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi']  ISO1
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi']  ISO2
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi']  ISO3

Huoneistot info for Uusirakennus is correct
  Xpath Should Match X Times  //div[@id='application-info-tab']//tr[@data-repeating-id="huoneistot"]  1
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.0.huoneistonumero']  000
  List Selection Should Be  xpath=//select[@data-test-id="huoneistot.0.muutostapa"]  lis\u00e4ys

  Open accordions  info
  Click by id  huoneistot_append
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.muutostapa"]
  Element Should Be Enabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.muutostapa"]
  List Selection Should Be  xpath=//select[@data-test-id="huoneistot.1.muutostapa"]  lis\u00e4ys

  Select From List By Index  xpath=//select[@data-test-id="huoneistot.1.muutostapa"]  0
  Huoneisto row items disabled except muutostapa
  Reload Page
  Wait Until  Element should be visible  //div[@id="application-info-tab"]
  Open accordions  info
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.muutostapa"]
  Xpath Should Match X Times  //div[@id='application-info-tab']//tr[@data-repeating-id="huoneistot"]  2
  Huoneisto row items disabled except muutostapa

Mikko removes apartment
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//i[@data-test-class="delete-schemas.huoneistot"]
  Wait Until  Element Should Be Visible  xpath=//tr[@data-repeating-id-huoneistot='0']
  Execute Javascript  $("tr[data-repeating-id-huoneistot='0']").find("i[data-test-class='delete-schemas.huoneistot']").click();
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element Should Not Be Visible  xpath=//tr[@data-repeating-id-huoneistot='0']
  Xpath Should Match X Times  //div[@id='application-info-tab']//tr[@data-repeating-id="huoneistot"]  1

Mikko can't set "other" building material
  Open accordions  info
  Wait Until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']//select[@data-docgen-path='rakenne.kantavaRakennusaine']
  Wait Until  Element should not be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']

Mikko selects building material 'other':
  Select From List  //section[@id='application']//div[@id='application-info-tab']//select[@name='rakenne.kantavaRakennusaine']  other
  Wait Until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']
  Input text  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']  Purukumilla ajattelin

On the second thought, set material to 'puu':
  Select From List  //section[@id='application']//div[@id='application-info-tab']//select[@name='rakenne.kantavaRakennusaine']  puu
  Wait Until  Element should not be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']

Mikko goes to parties tab of an application
  Open tab  parties
  Open accordions  parties

Mikko unsubscribes notifications
  Xpath Should Match X Times  //div[@id='application-parties-tab']//a[@data-test-id='unsubscribeNotifications']  1
  Wait Until  Element should be visible  xpath=//div[@id='application-parties-tab']//a[@data-test-id='unsubscribeNotifications']
  Click by test id  unsubscribeNotifications
  Wait Until  Element should not be visible  xpath=//div[@id='application-parties-tab']//a[@data-test-id='unsubscribeNotifications']
  Wait Until  Element should be visible  xpath=//div[@id='application-parties-tab']//a[@data-test-id='subscribeNotifications']

Mikko subscribes notifications
  Click by test id  subscribeNotifications
  Wait Until  Element should not be visible  xpath=//div[@id='application-parties-tab']//a[@data-test-id='subscribeNotifications']
  Wait Until  Element should be visible  xpath=//div[@id='application-parties-tab']//a[@data-test-id='unsubscribeNotifications']

Mikko decides to delete maksaja
  Set Suite Variable  ${maksajaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='maksaja']
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  1
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//button[@data-test-class='delete-schemas.maksaja']
  Execute Javascript  $("button[data-test-class='delete-schemas.maksaja']").click();
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  0

Mikko adds party maksaja using dialog
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//select[@data-test-id='select-party-document']
  Wait Until  Select From List By Value  xpath=//select[@data-test-id="select-party-document"]  maksaja
  List Selection Should Be  xpath=//select[@data-test-id="select-party-document"]  maksaja
  Click enabled by test id  add-party-button
  Wait Until  Element Should Not Be Visible  dialog-add-party
  Open accordions  parties
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//button[@data-test-class='delete-schemas.maksaja']
  Wait until  Xpath Should Match X Times  ${maksajaXpath}  1

Mikko adds party hakija-r using button
  Set Suite Variable  ${hakijaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  1
  Click enabled by test id  hakija-r_append_btn
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  2

Mikko changes application address
  Page should not contain  ${newName}
  Element should be visible  xpath=//section[@id='application']//a[@data-test-id='change-location-link']
  Click by test id  change-location-link
  Input text by test id  application-new-address  ${newName}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${newName}

Mikko decides to submit application
  Open application  ${newName}  ${propertyId}
  Wait until  Application state should be  draft
  Submit application

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  ${newName}


*** Keywords ***

Huoneisto row items disabled except muutostapa
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.huoneistoTyyppi"]
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.keittionTyyppi"]
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.porras']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.huoneistonumero']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.jakokirjain']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.huoneluku']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.huoneistoala']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.WCKytkin']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.ammeTaiSuihkuKytkin']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.saunaKytkin']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.parvekeTaiTerassiKytkin']
  Element Should Be Disabled  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-docgen-path='huoneistot.1.lamminvesiKytkin']
  Element Should Be Enabled   //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id="huoneistot.1.muutostapa"]

