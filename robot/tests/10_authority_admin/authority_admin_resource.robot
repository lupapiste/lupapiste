*** Settings ***

Documentation  Authority admin resources
Resource       ../../common_resource.robot

*** Keywords ***

Add link
  [Arguments]  ${name}  ${url}
  Element should not be visible  //a[@href='${url}']
  Wait and click  xpath=//a[@data-test-id='add-link']
  Wait until  Element should be visible  dialog-edit-link
  Input Text  jquery=#dialog-edit-link #link-text-fi  ${name}
  Input Text  jquery=#dialog-edit-link #link-url-fi  ${url}
  Input Text  jquery=#dialog-edit-link #link-text-sv  ${name} sv
  Input Text  jquery=#dialog-edit-link #link-url-sv  ${url}
  Input Text  jquery=#dialog-edit-link #link-text-en  ${name} en
  Input Text  jquery=#dialog-edit-link #link-url-en  ${url}

  Click element  //div[@id='dialog-edit-link']//button[1]
  Wait until  Element should be visible  //td/div[text()='${name}']
  Wait until  Element should be visible  //td/div[text()='${name} sv']
  Wait until  Element should be visible  //td/div[text()='${name} en']
  Wait until  Element should be visible  //a[@href='${url}']

Update link
  [Arguments]  ${name}  ${url}
  Wait test id visible  organization-links-table
  Scroll to top
  Wait and click  xpath=//table[@data-test-id='organization-links-table']//td/div[text()='${name}']/../..//a[@data-test-id='edit']
  Wait Until  Element Should Be Visible  dialog-edit-link
  Input Text  jquery=#dialog-edit-link #link-url-fi  ${url}
  Click element  //div[@id='dialog-edit-link']//button[1]
  Wait for jQuery
  Wait Until  Page Should Contain  ${url}

Remove link
  [Arguments]  ${name}
  Wait test id visible  organization-links-table
  Scroll to test id  organization-links-table
  Wait and click  xpath=//table[@data-test-id='organization-links-table']//td/div[text()='${name}']/../..//a[@data-test-id='remove']

User sees link
  [Arguments]  ${name}  ${url}
  Prepare first request  Latokuja 103  753  753-423-2-160  R
  Element Text Should Be  xpath=//a[@href='${url}']  ${name}

User does not see link
  [Arguments]  ${name}
  Prepare first request  Latokuja 103  753  753-423-2-160  R
  Element should not be visible  //a[text()='${name}']

#Setting maps disabled again after the tests
#  Set integration proxy off
