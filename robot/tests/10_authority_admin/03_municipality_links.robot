*** Settings ***

Documentation  Authority admin edits municipality links
Test teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

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
  Element should not be visible  //a[@href='${url}']
  Wait and click  xpath=//a[@data-test-id='add-link']
  Wait until  Element should be visible  dialog-edit-link
  Input Text  //div[@id='dialog-edit-link']//input[1]  ${name} fi
  Input Text  //div[@id='dialog-edit-link']//input[2]  ${name} sv
  Input Text  //div[@id='dialog-edit-link']//input[3]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]
  Wait until  Element should be visible  //td[text()='${name} fi']
  Wait until  Element should be visible  //td[text()='${name} sv']
  Wait until  Element should be visible  //a[@href='${url}']

Update link
  [Arguments]  ${name}  ${url}
  Execute Javascript  window.scrollTo(0, 500);
  Wait and click  xpath=//table[@data-test-id='organization-links-table']//td[text()='${name} fi']/..//a[@data-test-id='edit']
  Wait Until  Element Should Be Visible  dialog-edit-link
  Wait Until  Input Text  //div[@id='dialog-edit-link']//input[3]  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]

Remove link
  [Arguments]  ${name}
  Execute Javascript  window.scrollTo(0, 500);
  Wait and click  xpath=//table[@data-test-id='organization-links-table']//td[text()='${name} fi']/..//a[@data-test-id='remove']

User sees link
  [Arguments]  ${name}  ${url}
  Prepare first request  Latokuja 103  753  753-423-2-160  R
  Element Text Should Be  xpath=//a[@href='${url}']  ${name} fi

User does not see link
  [Arguments]  ${name}
  Prepare first request  Latokuja 103  753  753-423-2-160  R
  Element should not be visible  //a[text()='${name} fi']

#Setting maps disabled again after the tests
#  Set integration proxy off
