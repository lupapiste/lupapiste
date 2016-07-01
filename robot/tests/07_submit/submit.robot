*** Settings ***

Documentation   Sonja can't submit application
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       ../06_attachments/variables.py

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
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']
  Logout

Sonja can not submit application
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Wait until  Element should not be visible  application-requiredFieldSummary-tab
  Logout

#
# Testing the missing required fields and attachments, plus the "attachment not needed" functionality
#

Sipoo marks required fields obligatory
  Sipoo logs in
  Go to page  applications
  Wait until Element is visible  required-fields-obligatory-enabled
  Focus  required-fields-obligatory-enabled
  Checkbox Should Not Be Selected  required-fields-obligatory-enabled
  Select Checkbox  required-fields-obligatory-enabled
  Wait for jQuery
  Wait Until  Checkbox Should Be Selected  required-fields-obligatory-enabled
  Logout

Mikko logs in, goes to attachments tab and sees all "not needed" checkboxes as enabled and not selected
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Wait Until  Element should be visible  xpath=//table[@data-test-id='attachments-template-table']//td[contains(text(), 'Yleiset hankkeen liitteet')]
  Xpath Should Match X Times  //table[@data-test-id='attachments-template-table']//td[contains(@class, 'attachment-not-needed')]//input  4
  Checkbox Should Not Be Selected  //table[@data-test-id='attachments-template-table']//input[@data-test-id='attachment-not-needed-hakija-valtakirja']
  Checkbox Should Not Be Selected  //table[@data-test-id='attachments-template-table']//input[@data-test-id='attachment-not-needed-paapiirustus-asemapiirros']
  Checkbox Should Not Be Selected  //table[@data-test-id='attachments-template-table']//input[@data-test-id='attachment-not-needed-pelastusviranomaiselle_esitettavat_suunnitelmat-vaestonsuojasuunnitelma']
  Checkbox Should Not Be Selected  //table[@data-test-id='attachments-template-table']//input[@data-test-id='attachment-not-needed-paapiirustus-pohjapiirustus']

Mikko can not submit application because there are "missing required" items on the requiredFieldSummary tab
  Open tab  requiredFieldSummary
  Wait Until  Element Should Be Visible  xpath=//i[@class='error-text']
  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-warnings']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-required-fields']
  Element should be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-required-attachments']
  Xpath Should Match X Times  //div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-warnings']//*[@class='requiredField-line']  6
  ${missingRequiredCount} =  Get Matching Xpath Count  xpath=//*[@class='requiredField-line']
  Set Suite Variable  ${missingRequiredCount}
  Logout

Sipoo marks required fields not obligatory
  Sipoo logs in
  Go to page  applications
  Wait until Element is visible  xpath=//input[@data-test-id='required-fields-obligatory-enabled']
  Focus  xpath=//input[@id='required-fields-obligatory-enabled']
  Checkbox Should Be Selected  id=required-fields-obligatory-enabled
  Sleep  0.5s
  Unselect Checkbox  id=required-fields-obligatory-enabled
  Wait for jQuery
  Wait Until  Checkbox Should Not Be Selected  id=required-fields-obligatory-enabled
  Logout

Sonja logs in and adds new attachment template
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Add empty attachment template  Muu liite  muut  muu
  Wait Until Element Is Visible  xpath=//div[@id="application-attachments-tab"]//a[@data-test-type="muut.muu"]

For that template, the "not needed" checkbox is enabled and not selected
  Checkbox Should Not Be Selected  xpath=//div[@id="application-attachments-tab"]//table[@data-test-id='attachments-template-table']//input[@data-test-id='${attachment-not-needed-test-id-sonja}']
  Element should be enabled  xpath=//div[@id="application-attachments-tab"]//table[@data-test-id='attachments-template-table']//input[@data-test-id='${attachment-not-needed-test-id-sonja}']
  Logout

Mikko logs back in and browses to the Attachments tab
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Wait Until  Page should contain element  xpath=//div[@id="application-attachments-tab"]//table[@data-test-id="attachments-template-table"]//td
  Wait Until  Element should be visible  xpath=//div[@id="application-attachments-tab"]//table[@data-test-id="attachments-template-table"]//td[contains(text(), 'Yleiset hankkeen liitteet')]

For the added attachment template added by Sonja, Mikko sees the "not needed" checkbox as disabled and not selected
  ${checkbox-path-sonja} =  Set Variable  //div[@id="application-attachments-tab"]//table[@data-test-id='attachments-template-table']//input[@data-test-id='${attachment-not-needed-test-id-sonja}']
  Wait Until  Xpath Should Match X Times  ${checkbox-path-sonja}  1
  Element should be disabled  ${checkbox-path-sonja}
  Checkbox Should Not Be Selected  ${checkbox-path-sonja}

Mikko selects the "not needed" checkbox of some other attachment template than the one of Sonja's
  ${checkbox-path-hakija-valtakirja} =  Set Variable  //div[@id='application-attachments-tab']//table[@data-test-id='attachments-template-table']//input[@data-test-id='${attachment-not-needed-test-id-hakija-valtakirja}']
  Wait Until  Xpath Should Match X Times  ${checkbox-path-hakija-valtakirja}  1
  Wait Until  Element should be visible  ${checkbox-path-hakija-valtakirja}
  Element should be enabled  ${checkbox-path-hakija-valtakirja}
  Checkbox Should Not Be Selected  ${checkbox-path-hakija-valtakirja}
  Select Checkbox  ${checkbox-path-hakija-valtakirja}
  Wait Until  Checkbox Should Be Selected  ${checkbox-path-hakija-valtakirja}

Mikko adds pdf attachment to the attachment template added by Sonja
  Open attachment details  muut.muu
  Add attachment version  ${PDF_TESTFILE_PATH}
  Click element  xpath=//section[@id="attachment"]//a[@data-test-id="back-to-application-from-attachment"]
  Wait Until  Tab should be visible  attachments
  Page Should Not Contain  xpath=//div[@id="application-attachments-tab"]//a[@data-test-type="muut.muu"]

Mikko fills up a field marked with a VRK warning
  Open tab  info
  Open accordions  info
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='kaytto.kayttotarkoitus']  131 asuntolat yms
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='lammitys.lammitystapa']  ilmakeskus
  Select From List By Value  xpath=//div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//select[@data-test-id='lammitys.lammonlahde']  kaasu

Mikko fills up first name for the hakija party in the parties tab
  Open tab  parties
  Open accordions  parties
  ${hakija-etunimi-path} =  Set Variable  //div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']//input[@data-docgen-path='henkilo.henkilotiedot.etunimi']
  Wait until  Element should be visible  xpath=${hakija-etunimi-path}
  Execute Javascript  $('#application-parties-tab').find('section[data-doc-type="hakija-r"]').find('input[data-docgen-path="henkilo.henkilotiedot.etunimi"]').val("Elmeri").change().blur();
  Wait Until  Textfield value should be  xpath=${hakija-etunimi-path}  Elmeri
  Focus  xpath=//div[@id='application-parties-tab']//section[@data-doc-type='hakija-r']//input[@data-docgen-path='henkilo.henkilotiedot.sukunimi']
  Wait until  Element should be visible  xpath=//span[contains(@class,'form-input-saved')]

The filled-up warning field and party info plus the added attachment cause corresponding items to disappear from the "missing required" list in the requiredFieldSummary tab
  Open tab  requiredFieldSummary
  Wait for jQuery
  Wait Until  Element should be visible  xpath=//*[@data-test-id='application-submit-btn']
  ${missingRequiredCountAfter} =  Evaluate  ${missingRequiredCount} - 8
  Wait Until  Xpath Should Match X Times  //*[@class='requiredField-line']  ${missingRequiredCountAfter}
  Xpath Should Match X Times  //div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-warnings']//*[@class='requiredField-line']  0
  Element should not be visible  xpath=//div[@id='application-requiredFieldSummary-tab']//div[@data-test-id='test-application-warnings']

Mikko could submit application after missing stuff have been added
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='application-submit-btn']

Submit date is not be visible
  Element should not be visible  xpath=//span[@data-test-id='application-submitted-date']

Mikko submits application
  Submit application

Mikko cant re-submit application
  Wait Until  Element should not be visible  xpath=//*[@data-test-id='application-submit-btn']

Submit date should be visible
  Wait until  Element should be visible  xpath=//span[@data-test-id='application-submitted-date']
