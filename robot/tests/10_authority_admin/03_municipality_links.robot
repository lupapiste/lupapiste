*** Settings ***

Documentation  Authority admin edits municipality links
Test teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Admin adds new municipality link
  Sipoo logs in
  Add link  "fancy-link"  "http://reddit.com"
  
Mikko asks information and sees the new link
  Mikko logs in
  User sees link  "fancy-link"  "http://reddit.com"

Admin changes link target
  Sipoo logs in
  Update link  "fancy-link"  "http://slashdot.org"
  
Mikko asks information and sees updated link
  Mikko logs in
  User sees link  "fancy-link"  "http://slashdot.org"

Admin removed the link
  Remove link  "fancy-link"
  
Mikko asks information and does not see link
  Mikko logs in
  User does not see link  "fancy-link"
  
*** Keywords ***

Add link
  [Arguments]  ${name}  ${url}
  Wait and click  xpath=//a[@data-test-id='add-link']
  Input Text  //div[@id='dialog-edit-link']//input[1]  ${name}
  Input Text  //div[@id='dialog-edit-link']//input[2]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]

Update link
  [Arguments]  ${name}  ${url}
  Log  "todo"
  
Remove link
  [Arguments]  ${name}
  Log  "todo"

User sees link
  [Arguments]  ${name}  ${url}
  Log  "todo"

User does not see link
  [Arguments]  ${name}
  Log  "todo"
