*** Settings ***

Documentation  Authority admin edits municipality links
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Admin adds new municipality link
  As Sipoo
  Add new link  "link-to-foo"  "http://reddit.com"
  Close browser
  
*** Keywords ***

Add new link
  [Arguments]  ${name}  ${url}
  Wait and click  xpath=//a[@data-test-id='add-link']
  Input Text  //div[@id='dialog-edit-link']//input[1]  ${name}
  Input Text  //div[@id='dialog-edit-link']//input[2]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]
