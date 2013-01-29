*** Settings ***

Documentation  Authority admin edits municipality links
Test teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Admin adds new municipality link
  Sipoo logs in
  Add link  fancy-link  http://reddit.com

Mikko asks information and sees the new link
  Mikko logs in
  User sees link  fancy-link  http://reddit.com

Admin changes link target
  Sipoo logs in
  Update link  fancy-link  http://slashdot.org

Mikko asks information and sees updated link
  Mikko logs in
  User sees link  fancy-link  http://slashdot.org

Admin removes the link
  Sipoo logs in
  Remove link  fancy-link

Mikko asks information and does not see link
  Mikko logs in
  User does not see link  fancy-link

*** Keywords ***

Add link
  [Arguments]  ${name}  ${url}
  Wait and click  xpath=//a[@data-test-id='add-link']
  Input Text  //div[@id='dialog-edit-link']//input[1]  ${name}
  Input Text  //div[@id='dialog-edit-link']//input[2]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]

Update link
  [Arguments]  ${name}  ${url}
  Wait and click  xpath=//table[@data-test-id='municipality-links-table']//td[text()='${name}']/..//a[@data-test-id='edit']
  Input Text  //div[@id='dialog-edit-link']//input[2]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]

Remove link
  [Arguments]  ${name}
  Wait and click  xpath=//table[@data-test-id='municipality-links-table']//td[text()='${name}']/..//a[@data-test-id='remove']

User sees link
  [Arguments]  ${name}  ${url}
  Begin inforequest
  Element should contain  xpath=//a[@href='${url}']  ${name}

User does not see link
  [Arguments]  ${name}
  Begin inforequest
  Element should not be visible  //a[text()='${name}']

Begin inforequest
  Click link  xpath=//a[@class='brand']
  Click by test id  applications-create-new
  Input text by test id  create-address  Latokuja 103, Sipoo
  Select From List by test id  create-municipality-select  753
  Click by test id  create-continue
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Rakentaminen ja purkaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Asuinrakennus"]
  Wait until  Element should be visible  xpath=//div[@class='tree-result']
