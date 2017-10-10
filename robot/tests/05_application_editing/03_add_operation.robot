*** Settings ***

Documentation   Mikko ads an other operation
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New application contains the default initial operation
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  add-operation${secs}
  Create application the fast way  ${appname}  753-416-25-32  kerrostalo-rivitalo
  Wait Until  Page Should Contain  Asuinkerrostalon tai rivitalon rakentaminen

Mikko adds an operation
  Set animations off
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains two operations
  Wait Until  Page Should Contain  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Page Should Contain  Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)

Add another operation
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[text()="Vapaa-ajan asunnon, saunarakennuksen tai vierasmajan rakentaminen"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[contains(@class, 'tree-content')]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains three operations
  Open accordions  info
  Wait Until  Element should Contain  xpath=//span[@data-test-id="test-application-primary-operation"]  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should Contain  xpath=//ul[@data-test-id="application-secondary-operations"]  Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)
  Wait Until  Element should Contain  xpath=//ul[@data-test-id="application-secondary-operations"]  Vapaa-ajan asunnon, saunarakennuksen tai vierasmajan rakentaminen

Primary operation can be changed
  Open accordion editors
  Wait Until  Element Should Be Visible  xpath=//button[@data-op-name="kerrostalo-rivitalo"]/i[contains(@class, 'lupicon-star')]
  Wait Until  Element Should Be Visible  xpath=//button[@data-op-name="muu-uusi-rakentaminen"]/i[contains(@class, 'lupicon-star')]
  Wait Until  Element Should Be Visible  xpath=//button[@data-op-name="vapaa-ajan-asuinrakennus"]/i[contains(@class, 'lupicon-star')]

  Scroll to  button[data-op-name='muu-uusi-rakentaminen']
  Click Element  //button[@data-op-name="muu-uusi-rakentaminen"]

  Wait until  Primary operation is  muu-uusi-rakentaminen
  Open accordion editors
  Wait Until  Element Should Be Enabled  xpath=//button[@data-op-name="kerrostalo-rivitalo"]
  Wait Until  Element Should Be Disabled  xpath=//button[@data-op-name="muu-uusi-rakentaminen"]
  Wait Until  Element Should Be Enabled  xpath=//button[@data-op-name="vapaa-ajan-asuinrakennus"]

  Wait Until  Element should Contain  xpath=//span[@data-test-id="test-application-primary-operation"]  Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)
  Wait Until  Element should Contain  xpath=//ul[@data-test-id="application-secondary-operations"]  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should Contain  xpath=//ul[@data-test-id="application-secondary-operations"]  Vapaa-ajan asunnon, saunarakennuksen tai vierasmajan rakentaminen
  Logout

Frontend errors check
  There are no frontend errors
