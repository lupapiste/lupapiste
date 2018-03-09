*** Settings ***

Documentation   Mikko replaces an operation
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New application contains the default initial operation
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  replace-operation${secs}
  Create application the fast way  ${appname}  753-416-25-32  kerrostalo-rivitalo
  Wait Until  Page Should Contain  Asuinkerrostalon tai rivitalon rakentaminen

Mikko replaces primary operation
  Set animations off
  Click enabled by test id  replace-operation
  Wait Until  Element Should Be Visible  replace-operation
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Maisemaan tai asuinympäristöön vaikuttava toimenpide"]
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Muu tontin tai korttelialueen muutos"]
  Wait and click  //section[@id="replace-operation"]//button[@data-test-id='replace-operation']

Mikko sees the replaced primary operation
  Wait Until  Element should be visible  //*[@data-test-id='maisematyo-accordion-title-text']
  Element should not be visible  //*[@data-test-id='uusiRakennus-accordion-title-text']

Mikko adds an operation
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait Until  Element Should Be Visible  add-operation
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"]
  Wait Until  Element should be visible  xpath=//section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Mikko replaces the new operation
  Wait and click  //section[@data-doc-type='uusiRakennus']//button[@data-test-id="replace-operation"]
  Wait Until  Element Should Be Visible  replace-operation
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="replace-operation"]//div[contains(@class, 'tree-content')]//*[text()="Vapaa-ajan asunnon, saunarakennuksen tai vierasmajan rakentaminen"]
  Wait and click  //section[@id="replace-operation"]//button[@data-test-id='replace-operation']

Mikko sees the replaced secondary operation
  Wait Until  Element should be visible  //*[@data-test-id='uusi-rakennus-ei-huoneistoa-accordion-title-text']
  Element should be visible  //*[@data-test-id='maisematyo-accordion-title-text']
  Element should not be visible  //*[@data-test-id='muu-uusi-rakentaminen-accordion-title-text']

