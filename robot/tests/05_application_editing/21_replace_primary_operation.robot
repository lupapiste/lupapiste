*** Settings ***

Documentation   Mikko ads an other operation
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New application contains the default initial operation
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  replace-primary-operation${secs}
  Create application the fast way  ${appname}  753-416-25-32  kerrostalo-rivitalo
  Wait Until  Page Should Contain  Asuinkerrostalon tai rivitalon rakentaminen

Mikko changes primary operation
  Set animations off
  Click enabled by test id  replace-primary-operation
  Wait Until  Element Should Be Visble  replace-primary-operation
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Wait and click  //section[@id="replace-primary-operation"]//div[contains(@class, 'tree-content')]//*[text()="Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"]
  Wait and click  //section[@id="replace-primary-operation"]//div[contains(@class, 'tree-content')]//*[text()="Maisemaan tai asuinympäristöön vaikuttava toimenpide"]
  Wait and click  //section[@id="replace-primary-operation"]//div[contains(@class, 'tree-content')]//*[text()="Kaivaminen, louhiminen tai maan täyttäminen omalla kiinteistöllä"]

