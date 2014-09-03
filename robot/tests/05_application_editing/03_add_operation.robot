*** Settings ***

Documentation   Mikko ads an other operation
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New application contains the default initial operation
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  add-operation${secs}
  Create application the fast way  ${appname}  753  753-416-25-32  asuinrakennus
  Wait Until  Page Should Contain  Asuinrakennuksen rakentaminen

Mikko adds an operation
  Set animations off
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen (talot, grillikatokset, autotallit, remontointi)"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen (mökit, omakotitalot, saunat, julkiset rakennukset)"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Muun rakennuksen rakentaminen"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains two operations
  Wait Until  Page Should Contain  Asuinrakennuksen rakentaminen
  Wait Until  Page Should Contain  Muun rakennuksen rakentaminen

Add another operation
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen (talot, grillikatokset, autotallit, remontointi)"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen (talot, grillikatokset, autotallit, remontointi)"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen (mökit, omakotitalot, saunat, julkiset rakennukset)"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Julkisen rakennuksen rakentaminen"]
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains three operations
  Wait Until  Page Should Contain  Asuinrakennuksen rakentaminen
  Wait Until  Page Should Contain  Muun rakennuksen rakentaminen
  Wait Until  Page Should Contain  Julkisen rakennuksen rakentaminen
