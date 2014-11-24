*** Settings ***

Documentation   Mikko ads an other operation
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New application contains the default initial operation
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  add-operation${secs}
  Create application the fast way  ${appname}  753  753-416-25-32  kerrostalo-rivitalo
  Wait Until  Page Should Contain  Asuinkerrostalon ja/tai rivitalon rakentaminen

Mikko adds an operation
  Set animations off
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen sekä maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Muun kuin edellä mainitun rakennuksen rakentaminen (liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains two operations
  Wait Until  Page Should Contain  Asuinkerrostalon ja/tai rivitalon rakentaminen
  Wait Until  Page Should Contain  Muun kuin edellä mainitun rakennuksen rakentaminen (liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)

Add another operation
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen sekä maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen sekä maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Loma-asunnon ja/tai saunarakennuksen rakentaminen"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains three operations
  Wait Until  Page Should Contain  Asuinkerrostalon ja/tai rivitalon rakentaminen
  Wait Until  Page Should Contain  Muun kuin edellä mainitun rakennuksen rakentaminen (liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)
  Wait Until  Page Should Contain  Loma-asunnon ja/tai saunarakennuksen rakentaminen
