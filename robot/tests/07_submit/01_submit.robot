*** Settings ***

Documentation   Sonja can't submit application
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../06_attachments/variables.py

*** Variables ***
@{EXPECTED-FIELDS}    missing-uusiRakennus-kayttotarkoitus  missing-paasuunnittelija-osapuoli.patevyys.valmistumisvuosi
...                   missing-suunnittelija-osapuoli.patevyys.valmistumisvuosi  missing-hankkeen-kuvaus-hankkeen-kuvaus.kuvaus
...                   missing-rakennuspaikka-rakennuspaikka.hallintaperuste._group_label  missing-uusiRakennus-huoneistot.huoneluku
...                   missing-uusiRakennus-huoneistot.keittionTyyppi  missing-uusiRakennus-huoneistot.huoneistoala
...                   missing-uusiRakennus-huoneistot.huoneistoTyyppi  missing-uusiRakennus-uusiRakennus.kaytto.rakentajaTyyppi._group_label
...                   missing-uusiRakennus-omistajalaji._group_label  missing-uusiRakennus-puhelin  missing-uusiRakennus-email
...                   missing-uusiRakennus-hetu  missing-uusiRakennus-sukunimi  missing-uusiRakennus-etunimi  missing-uusiRakennus-osoite.katu
...                   missing-uusiRakennus-osoite.postinumero  missing-uusiRakennus-osoite.postitoimipaikannimi
...                   missing-uusiRakennus-rakentamistapa  missing-uusiRakennus-kantavaRakennusaine  missing-uusiRakennus-uusiRakennus.tunnus
...                   missing-paatoksen-toimitus-rakval-sukunimi  missing-paatoksen-toimitus-rakval-etunimi
...                   missing-paatoksen-toimitus-rakval-osoite.katu  missing-paatoksen-toimitus-rakval-osoite.postinumero
...                   missing-paatoksen-toimitus-rakval-osoite.postitoimipaikannimi
...                   missing-paasuunnittelija-osapuoli.suunnittelutehtavanVaativuusluokka._group_label  missing-paasuunnittelija-koulutus
...                   missing-paasuunnittelija-osapuoli.patevyys.patevyysluokka._group_label  missing-paasuunnittelija-osapuoli.patevyys.valmistumisvuosi
...                   missing-paasuunnittelija-puhelin  missing-paasuunnittelija-email  missing-paasuunnittelija-hetu
...                   missing-paasuunnittelija-sukunimi  missing-paasuunnittelija-etunimi  missing-paasuunnittelija-osoite.katu
...                   missing-paasuunnittelija-osoite.postinumero  missing-paasuunnittelija-osoite.postitoimipaikannimi
...                   missing-suunnittelija-osapuoli.suunnittelutehtavanVaativuusluokka._group_label
...                   missing-suunnittelija-osapuoli.suunnittelija.kuntaRoolikoodi._group_label  missing-suunnittelija-koulutus
...                   missing-suunnittelija-osapuoli.patevyys.patevyysluokka._group_label  missing-suunnittelija-osapuoli.patevyys.valmistumisvuosi
...                   missing-suunnittelija-puhelin  missing-suunnittelija-email  missing-suunnittelija-hetu  missing-suunnittelija-sukunimi
...                   missing-suunnittelija-etunimi  missing-suunnittelija-osoite.katu  missing-suunnittelija-osoite.postinumero
...                   missing-suunnittelija-osoite.postitoimipaikannimi  missing-maksaja-puhelin  missing-maksaja-email
...                   missing-maksaja-hetu  missing-maksaja-sukunimi  missing-maksaja-etunimi  missing-maksaja-osoite.katu
...                   missing-maksaja-osoite.postinumero  missing-maksaja-osoite.postitoimipaikannimi
...                   missing-paapiirustus-asemapiirros  missing-paapiirustus-pohjapiirustus  missing-hakija-valtakirja
...                   missing-pelastusviranomaiselle_esitettavat_suunnitelmat-vaestonsuojasuunnitelma

@{AFTER-FILL}  missing-hankkeen-kuvaus-hankkeen-kuvaus.kuvaus
...            missing-rakennuspaikka-rakennuspaikka.hallintaperuste._group_label  missing-uusiRakennus-huoneistot.huoneluku
...            missing-uusiRakennus-huoneistot.keittionTyyppi  missing-uusiRakennus-huoneistot.huoneistoala
...            missing-uusiRakennus-uusiRakennus.kaytto.rakentajaTyyppi._group_label  missing-uusiRakennus-omistajalaji._group_label
...            missing-uusiRakennus-puhelin  missing-uusiRakennus-email  missing-uusiRakennus-hetu  missing-uusiRakennus-sukunimi
...            missing-uusiRakennus-etunimi  missing-uusiRakennus-osoite.katu  missing-uusiRakennus-osoite.postinumero
...            missing-uusiRakennus-osoite.postitoimipaikannimi  missing-uusiRakennus-rakentamistapa  missing-uusiRakennus-kantavaRakennusaine
...            missing-uusiRakennus-uusiRakennus.tunnus  missing-paatoksen-toimitus-rakval-sukunimi  missing-paatoksen-toimitus-rakval-etunimi
...            missing-paatoksen-toimitus-rakval-osoite.katu  missing-paatoksen-toimitus-rakval-osoite.postinumero
...            missing-paatoksen-toimitus-rakval-osoite.postitoimipaikannimi  missing-hakija-r-sukunimi
...            missing-paasuunnittelija-osapuoli.suunnittelutehtavanVaativuusluokka._group_label
...            missing-paasuunnittelija-osapuoli.patevyys.valmistumisvuosi  missing-paasuunnittelija-koulutus
...            missing-paasuunnittelija-osapuoli.patevyys.patevyysluokka._group_label
...            missing-paasuunnittelija-osapuoli.patevyys.valmistumisvuosi  missing-paasuunnittelija-puhelin  missing-paasuunnittelija-email
...            missing-paasuunnittelija-hetu  missing-paasuunnittelija-sukunimi  missing-paasuunnittelija-etunimi
...            missing-paasuunnittelija-osoite.katu  missing-paasuunnittelija-osoite.postinumero
...            missing-paasuunnittelija-osoite.postitoimipaikannimi
...            missing-suunnittelija-osapuoli.suunnittelutehtavanVaativuusluokka._group_label
...            missing-suunnittelija-osapuoli.suunnittelija.kuntaRoolikoodi._group_label  missing-suunnittelija-koulutus
...            missing-suunnittelija-osapuoli.patevyys.valmistumisvuosi
...            missing-suunnittelija-osapuoli.patevyys.patevyysluokka._group_label  missing-suunnittelija-osapuoli.patevyys.valmistumisvuosi
...            missing-suunnittelija-puhelin  missing-suunnittelija-email  missing-suunnittelija-hetu  missing-suunnittelija-sukunimi
...            missing-suunnittelija-etunimi  missing-suunnittelija-osoite.katu  missing-suunnittelija-osoite.postinumero
...            missing-suunnittelija-osoite.postitoimipaikannimi  missing-maksaja-puhelin  missing-maksaja-email
...            missing-maksaja-hetu  missing-maksaja-sukunimi  missing-maksaja-etunimi  missing-maksaja-osoite.katu
...            missing-maksaja-osoite.postinumero  missing-maksaja-osoite.postitoimipaikannimi
...            missing-paapiirustus-asemapiirros  missing-paapiirustus-pohjapiirustus
...            missing-pelastusviranomaiselle_esitettavat_suunnitelmat-vaestonsuojasuunnitelma

*** Keywords ***

Open test application in required field summary tab
  Open application  ${appname}  ${propertyId}
  Open tab  requiredFieldSummary

Submit button is enabled
  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']

Check expected required fields and warnings
  [Arguments]  ${EXPECTED-TEST-IDS}
  Wait for jquery
  # Wait that ajax request + rendering might be ready..-
  Sleep  0.2s
  ${ELEMENTS}=  Get Webelements  xpath=//div[contains(@class,'info-line')]//span[contains(@class, 'required-field-error-element-name')]
  ${FOUND-TEST-IDS}=  Create List
  ${NOT-FOUND-TEST-IDS}=  Create List
  FOR  ${elem}  IN  @{ELEMENTS}
    ${TEST-ID}=  Get Element Attribute  ${elem}  data-test-id
    ${EXPECTED-IDX}=  Get Index From List  ${EXPECTED-TEST-IDS}  ${TEST-ID}
    Run keyword if  ${EXPECTED-IDX} >= 0  Remove From List  ${EXPECTED-TEST-IDS}  ${EXPECTED-IDX}
    Run keyword if  ${EXPECTED-IDX} < 0  Append to list  ${NOT-FOUND-TEST-IDS}  ${TEST-ID}
  END
  ${EXPECTED-LENGTH}=  Get Length  ${EXPECTED-TEST-IDS}
  ${NOT-FOUND-LENGTH}=  Get Length  ${NOT-FOUND-TEST-IDS}
  Should Be Equal As Integers  0  ${EXPECTED-LENGTH}  Expected errors were not found: ${EXPECTED-TEST-IDS}
  Should Be Equal As Integers  0  ${NOT-FOUND-LENGTH}  Errors were not expected: ${NOT-FOUND-TEST-IDS}


*** Test Cases ***

Mikko creates a new application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  submit${secs}
  Set Suite Variable  ${propertyId}  753-416-7-1

  Create application with state  ${appname}  ${propertyId}  kerrostalo-rivitalo  open
  Set Suite Variable  ${attachment-not-needed-test-id-hakija-valtakirja}  attachment-not-needed-hakija-valtakirja
  Set Suite Variable  ${attachment-not-needed-test-id-sonja}  attachment-not-needed-muut-muu

Mikko could submit application (when required fields are not obligatory)
  Open tab  requiredFieldSummary
  Wait Until  Submit button is enabled
  Logout

Sonja can submit application
  Sonja logs in
  Open test application in required field summary tab
  Wait until  Submit button is enabled
  Logout

#
# Testing the missing required fields and attachments
#

Sipoo marks required fields obligatory
  Sipoo logs in
  Go to page  applications
  Checkbox wrapper not selected by test id  required-fields-obligatory-enabled
  Click label by test id  required-fields-obligatory-enabled-label
  Checkbox wrapper selected by test id  required-fields-obligatory-enabled
  Logout

Mikko logs in
  Mikko logs in
  Open application  ${appname}  ${propertyId}

Mikko can not submit application because there are "missing required" items on the requiredFieldSummary tab
  Open tab  requiredFieldSummary
  Wait test id visible  submit-error-0
  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-warnings']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-required-fields']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-required-attachments']

  Check expected required fields and warnings  ${EXPECTED-FIELDS}
  Logout

Sipoo marks required fields not obligatory
  Sipoo logs in
  Go to page  applications
  Checkbox wrapper selected by test id  required-fields-obligatory-enabled
  Click label by test id  required-fields-obligatory-enabled-label
  Positive indicator should be visible
  Checkbox wrapper not selected by test id  required-fields-obligatory-enabled
  Logout

Sonja logs in and adds new attachment template
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Add empty attachment template  Muu liite  muut  muu
  Logout

Mikko logs back in and browses to the Attachments tab
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Mikko selects not needed for valtakirja attachment
  Click not needed  hakija.valtakirja

Mikko follows missing attachment link
  Open tab  requiredFieldSummary
  Scroll and click test id  missing-attachment-muut-muu

Mikko adds pdf attachment to the template requested by Sonja
  Add attachment version  ${PDF_TESTFILE_PATH}
  Positive indicator should not be visible
  Scroll and click test id  back-to-application-from-attachment
  Wait Until  Tab should be visible  attachments

Mikko fills up a field marked with a VRK warning
  Open tab  info
  Open accordions  info
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='kaytto.kayttotarkoitus']  131 asuntolat yms
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='lammitys.lammitystapa']  ilmakeskus
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='rakenne.julkisivu']  betoni
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='lammitys.lammonlahde']  kaasu
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='huoneistot.0.huoneistoTyyppi']  asuinhuoneisto

Mikko removes last name for the hakija party in the parties tab
  Open tab  parties
  Open accordions  parties
  ${hakija-etunimi-path} =  Set Variable  //div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']//input[@data-docgen-path='henkilo.henkilotiedot.etunimi']
  ${hakija-sukunimi-path} =  Set Variable  //div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']//input[@data-docgen-path='henkilo.henkilotiedot.sukunimi']
  Wait until  Element should be visible  xpath=${hakija-etunimi-path}
  Scroll to test id  application-invite-hakija-r
  # Applicant is filled by default
  Wait Until  Textfield value should be  xpath=${hakija-etunimi-path}  Mikko
  Wait Until  Textfield value should be  xpath=${hakija-sukunimi-path}  Intonen
  # ok, lets remove lastname to get validation error
  Edit party name  hakija-r  Mikko  ${EMPTY}  henkilo.henkilotiedot
  Focus  xpath=//div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']//input[@data-docgen-path='henkilo.henkilotiedot.sukunimi']
  Wait until  Element should be visible  xpath=//span[contains(@class,'form-input-saved')]
  Wait Until  Textfield value should be  xpath=${hakija-etunimi-path}  Mikko
  Wait Until  Textfield value should be  xpath=${hakija-sukunimi-path}  ${EMPTY}


Expected warnings and errors
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  xpath=//*[@data-test-id='application-submit-btn']
  # NOTE: for example Julkisivu became error after filling some information, expected or not...?
  Check expected required fields and warnings  ${AFTER-FILL}


Mikko could submit application after missing stuff have been added
  Wait Until  Submit button is enabled
  Logout

Sonja could submit Mikko's application when it's submittable by Mikko
  Sonja logs in
  Open test application in required field summary tab
  Wait Until  Submit button is enabled
  Logout

Submit date is not be visible
  Mikko logs in
  Open test application in required field summary tab
  Element should not be visible  xpath=//span[@data-test-id='application-submitted-date']

Mikko submits application
  Submit application

Mikko cant re-submit application
  Wait Until  Element should not be visible  xpath=//*[@data-test-id='application-submit-btn']

Submit date should be visible
  Wait until  Element should be visible  xpath=//span[@data-test-id='application-submitted-date']

