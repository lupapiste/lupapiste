*** Settings ***

Documentation  Muutostapa column is hidden for new buildings.
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot

*** Variables ***

${appname}     Newhouse
${propertyId}  753-423-2-41

*** Test Cases ***

Pena wants to build a new house
  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

No muutostapa help text
  jQuery should match X times  docgen-huoneistot-table .help-text:contains(Esimerkki)  0

Huoneistot info is correct
  Xpath Should Match X Times  //div[@id='application-info-tab']//table[contains(@class, 'huoneistot-table')]//tbody//tr  1
  Textfield Value Should Be  //div[@id='application-info-tab']//section[@data-doc-type='uusiRakennus']//input[@data-test-id='huoneistot.0.huoneistonumero']  000

No Muutostapa column
  No such test id  'huoneistot.0.muutostapa'

Every cell is enabled
  jQuery should match X times  tr[data-test-id='huoneistot-row-0'] :disabled  0
  [Teardown]  Logout
