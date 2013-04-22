*** Settings ***

Documentation   Mikko ads an other operation
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko creates application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  add-operation${secs}
  Create application the fast way  ${appname}  753  753-416-25-31

Application contains the default initial operation
  Wait Until  Page Should Contain  Asuinrakennuksen rakentaminen

Mikko adds an operation
  Click enabled by test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Set Selenium Speed  ${OP_TREE_SPEED}
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Rakentaminen ja purkaminen"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  //section[@id="add-operation"]//div[@class="tree-content"]//*[text()="Muun rakennuksen rakentaminen"]
  Set Selenium Speed  ${DEFAULT_SPEED}
  Wait until  Element should be visible  xpath=//section[@id="add-operation"]//div[@class="tree-content"]//*[@data-test-id="add-operation-to-application"]
  Click enabled by test id  add-operation-to-application

Application contains two operations
  Wait Until  Page Should Contain  Asuinrakennuksen rakentaminen
  Wait Until  Page Should Contain  Muun rakennuksen rakentaminen
