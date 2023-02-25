*** Settings ***

Documentation   Sonja should see only applications from Sipoo
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Init...
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  create-app${secs}
  Set Suite Variable  ${newName}  ${appname}-edit
  Set Suite Variable  ${propertyId}  753-423-2-41
  Set Suite Variable  ${newPropertyId}  753-423-3-52
  Set Suite Variable  ${paasuunnittelijaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='paasuunnittelija']
  Set Suite Variable  ${hakijanasiamiesXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='hakijan-asiamies']
  Set Suite Variable  ${asiamiesXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='hakijan-asiamies']

Mikko opens an application
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  kayttotark-muutos

Application info tab is visible
  Element should be visible  jquery=a[data-test-id=application-open-info-tab]

Application summary tab is hidden
  Page should not contain element  jquery=a[data-test-id=application-open-applicationSummary-tab]

# Testing the case that was fixed with hotfix/repeating-element-saving
# and later regression LUPA-1784
# Note: The test browser must be active or the case will fail.
Mikko adds three owners
  Open accordions  info
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  1

  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi"]  pikku

  Wait Until  Element Should Be Visible  //button[@id="rakennuksenOmistajat_append"]
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi']
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi']
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi']
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  4

  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi"]  ISO1
  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi"]  ISO2
  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi"]  ISO3
  Wait for jQuery

Owners are visible after page refresh
  Reload page and kill dev-box
  Application address should be  ${appname}
  Open accordions  info
  Wait Until  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  4
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi']  pikku
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.1.henkilo.henkilotiedot.etunimi']  ISO1
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.2.henkilo.henkilotiedot.etunimi']  ISO2
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.3.henkilo.henkilotiedot.etunimi']  ISO3

Error indicator appears with invalid data
  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.0.henkilo.osoite.postinumero"]  Test
  Focus  xpath=//div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.osoite.postitoimipaikannimi']
  Wait until  Element should be visible  xpath=//div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.osoite.postinumero' and contains(@class, 'err')]

Error indicator disappears when valid value is input
  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.0.henkilo.osoite.postinumero"]  12345
  Focus  xpath=//div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.osoite.postitoimipaikannimi']
  Wait until  Element should not be visible  xpath=//div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.0.henkilo.osoite.postinumero' and contains(@class, 'err')]

Huoneistot help text is available
  jQuery should match X times  docgen-huoneistot-table .help-text:contains(Esimerkki)  1

Huoneistot info is correct
  Xpath Should Match X Times  //div[@id='application-info-tab']//table[contains(@class, 'huoneistot-table')]//tbody//tr  1
  jQuery should match X times  tr#huoneistot-row-0.new-row  1
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-test-id='huoneistot.0.huoneistonumero']  ${EMPTY}
  Huoneisto row items enabled  0

  Open accordions  info
  Scroll and click test id  huoneistot-append-button
  Wait Until  Element Should Be Visible  jquery:tr#huoneistot-row-1.new-row
  Huoneisto row items enabled  1
  Select From List by test id and index  huoneistot.1.huoneistoTyyppi  1

  Sleep  0.5s
  Reload page and kill dev-box
  Wait Until  Element should be visible  //div[@id="application-info-tab"]
  Open accordions  info
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//select[@data-test-id='huoneistot.1.huoneistoTyyppi']
  List selection should be  xpath=//div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//select[@data-test-id='huoneistot.1.huoneistoTyyppi']  Asuinhuoneisto
  Xpath Should Match X Times  //div[@id='application-info-tab']//table[contains(@class, 'huoneistot-table')]//tbody//tr  2

Mikko removes apartment
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//button[@data-test-class="delete-schemas.huoneistot"]
  Wait Until  Element Should Be Visible  xpath=//tr[@data-test-id='huoneistot-row-0']
  Execute Javascript  $("tr[data-test-id='huoneistot-row-0']").find("button[data-test-class='delete-schemas.huoneistot']").click();
  Confirm yes no dialog
  Wait for jQuery
  Open accordions  info
  Wait Until  Element Should Not Be Visible  xpath=//tr[@data-test-id='huoneistot-row-0']
  Xpath Should Match X Times  //div[@id='application-info-tab']//table[contains(@class, 'huoneistot-table')]//tbody//tr  1

Mikko can't set "other" building material
  Open accordions  info
  Wait Until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']//select[@data-docgen-path='rakenne.kantavaRakennusaine']
  Wait Until  Element should not be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']

Mikko selects building material 'other':
  Select From List by value  //section[@id='application']//div[@id='application-info-tab']//select[@name='rakenne.kantavaRakennusaine']  other
  Wait Until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']
  Input text  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']  Purukumilla ajattelin

On the second thought, set material to 'puu':
  Select From List by value  //section[@id='application']//div[@id='application-info-tab']//select[@name='rakenne.kantavaRakennusaine']  puu
  Wait Until  Element should not be visible  //section[@id='application']//div[@id='application-info-tab']//input[@data-docgen-path='rakenne.muuRakennusaine']

Mikko goes to parties tab of an application
  Open tab  parties
  Open accordions  parties

Mikko inputs bad postal code for hakija-r
  Input text with jQuery  div#application-parties-tab section[data-doc-type="hakija-r"] input[data-docgen-path="henkilo.osoite.postinumero"]  000
  Wait Until  Element should be visible  jquery=section[data-doc-type="hakija-r"] input.err[data-docgen-path="henkilo.osoite.postinumero"]

Mikko changes hakija-r country and postal code becomes valid
  Select from list by value  jquery=div#application-parties-tab section[data-doc-type="hakija-r"] select[data-docgen-path="henkilo.osoite.maa"]  CHN
  Wait Until  Element should not be visible  jquery=section[data-doc-type="hakija-r"] input.err[data-docgen-path="henkilo.osoite.postinumero"]

# LPK-1995
Mikko can't delete paasuunnittelija, as it's only removable by authority
  Element should not be visible  xpath=//section[@data-doc-type='paasuunnittelija']//button[@data-test-class='delete-schemas.paasuunnittelija']

# LPK-LPK-1997
Mikko can't delete maksaja, as it's only removable by authority
  Element should not be visible  xpath=//section[@data-doc-type='maksaja']//button[@data-test-class='delete-schemas.maksaja']

Mikko cannot add party maksaja since maksaja doc is not repeating
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//div[@data-test-id='dialog-add-party']//select
  Wait Until  Element should contain  xpath=//div[@data-test-id='dialog-add-party']//select  Hakija
  Wait Until  Element should not contain  xpath=//div[@data-test-id='dialog-add-party']//select  Maksaja
  Deny yes no dialog

Mikko adds party hakijan-asiamies using dialog
  Click enabled by test id  add-party
  Wait Until  Element should be visible  xpath=//div[@data-test-id='dialog-add-party']//select
  Wait Until  Select From List By Value  xpath=//div[@data-test-id='dialog-add-party']//select  hakijan-asiamies
  List Selection Should Be  xpath=//div[@data-test-id='dialog-add-party']//select  hakijan-asiamies
  Confirm yes no dialog
  Wait Until  Element Should Not Be Visible  xpath=//div[@data-test-id='dialog-add-party']
  Open accordions  parties
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//button[@data-test-class='delete-schemas.hakijan-asiamies']
  Wait until  Xpath Should Match X Times  ${asiamiesXpath}  1


Mikko adds party hakija-r using button
  Set Suite Variable  ${hakijaXpath}  //section[@id='application']//div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']
  Set Suite Variable  ${hakijajQueryPath}  section[id='application'] div[id='application-parties-tab'] section[data-doc-type='hakija-r']
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  1
  Scroll to top
  Click by test id  hakija-r_append_btn
  Wait until  Xpath Should Match X Times  ${hakijaXpath}  2

Mikko fills his name as applicant, accordion text is updated
  # Ensure elements are visible:
  Scroll to top
  Input text with jQuery  ${hakijajQueryPath} input[data-docgen-path='henkilo.henkilotiedot.etunimi']  Mikko
  Input text with jQuery  ${hakijajQueryPath} input[data-docgen-path='henkilo.henkilotiedot.sukunimi']  Intonen
  Wait until  Element should contain  ${hakijaXpath}//span[@data-test-id='hakija-r-accordion-description-text']  Mikko Intonen

Mikko toggles applicant to company, company's name is updated to accordion
  Scroll and click  ${hakijajQueryPath} input[data-docgen-path='_selected'][value='yritys']
  Wait until  Element should be visible  ${hakijaXpath}//input[@data-docgen-path='yritys.yritysnimi']
  Wait until  Element should not contain  ${hakijaXpath}//span[@data-test-id='hakija-r-accordion-description-text']  Mikko Intonen
  Input text with jQuery  ${hakijajQueryPath} input[data-docgen-path='yritys.yritysnimi']  Mikon Firma
  Wait until  Element should contain  ${hakijaXpath}//span[@data-test-id='hakija-r-accordion-description-text']  Mikon Firma
  # Toggle applicant back to 'person'
  Scroll and click  ${hakijajQueryPath} input[data-docgen-path='_selected'][value='henkilo']
  Wait until  Element should contain  ${hakijaXpath}//span[@data-test-id='hakija-r-accordion-description-text']  Mikko Intonen

Mikko changes application address via property id edit
  Change address  change-property-id  Propsintie 8

Mikko changes application address via address edit
  Change address  change-location-link  ${newName}

Mikko decides to submit application
  Open application  ${newName}  ${propertyId}
  Wait until  Application state should be  draft
  Submit application

Mikko still sees the submitted app in applications list
  Go to page  applications
  Request should be visible  ${newName}

Hakemuksen jattaminen tab title shown to applicant
  Open application  ${newName}  ${propertyId}
  Test id text is  application-open-requiredFieldSummary-tab  Hakemuksen jättäminen
  [Teardown]  Logout

Siirra kasittelyyn tab title shown to authority
  Sonja logs in
  Open application  ${newName}  ${propertyId}
  Test id text is  application-open-requiredFieldSummary-tab  Siirrä käsittelyyn

Authority deletes paasuunnittelija
  Open tab  parties
  Wait until  Xpath Should Match X Times  ${paasuunnittelijaXpath}  1
  Wait Until  Element Should Be Visible  xpath=//section[@data-doc-type='paasuunnittelija']//button[@data-test-class='delete-schemas.paasuunnittelija']
  Execute Javascript  $("button[data-test-class='delete-schemas.paasuunnittelija']").click();
  Confirm yes no dialog
  Wait until  Xpath Should Match X Times  ${paasuunnittelijaXpath}  0

Authority deletes hakijan-asiamies
  Wait until  Xpath Should Match X Times  ${hakijanasiamiesXpath}  1
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@id='application-parties-tab']//button[@data-test-class='delete-schemas.hakijan-asiamies']
  Execute Javascript  $("button[data-test-class='delete-schemas.hakijan-asiamies']").click();
  Confirm yes no dialog
  Wait until  Xpath Should Match X Times  ${hakijanasiamiesXpath}  1

No errors logged in editing
  There are no frontend errors

*** Keywords ***

Huoneisto row items enabled
  [Arguments]  ${index}
  jQuery should match X times  tr[data-test-id='huoneistot-row-${index}'] :disabled  0
  jQuery should match X times  tr[data-test-id='huoneistot-row-${index}'] input  10
  jQuery should match X times  tr[data-test-id='huoneistot-row-${index}'] select  2
  jQuery should match X times  tr[data-test-id='huoneistot-row-${index}'] button  1


Change address
  [Arguments]  ${testId}  ${address}
  Page should not contain  ${address}
  Wait test id visible  ${testId}
  Scroll and click test id  ${testId}
  Input text by test id  application-new-address  ${address}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${address}
