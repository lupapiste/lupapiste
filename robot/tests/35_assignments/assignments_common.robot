*** Settings ***
Resource   ../../common_resource.robot

*** Keywords ***

Fill assignment editor
  [Arguments]  ${componentXpath}  ${targetGroup}  ${targetDocId}  ${user}  ${description}
  Element should be disabled  xpath=//${componentXpath}//button[@data-test-id='bubble-dialog-ok']
  Select from list by value  xpath=//${componentXpath}//select[@id='assignment-target-group']  ${targetGroup}
  Element should be disabled  xpath=//${componentXpath}//button[@data-test-id='bubble-dialog-ok']
  Select from list by value  xpath=//${componentXpath}//select[@id='assignment-target-id']  ${targetDocId}
  Element should be disabled  xpath=//${componentXpath}//button[@data-test-id='bubble-dialog-ok']
  Select from list by label  xpath=//${componentXpath}//select[@id='assignment-recipient']  ${user}
  Element should be disabled  xpath=//${componentXpath}//button[@data-test-id='bubble-dialog-ok']
  Input text  xpath=//${componentXpath}//textarea[@id='assignment-description']  ${description}
  Wait until  Element should be enabled  xpath=//${componentXpath}//button[@data-test-id='bubble-dialog-ok']

Create assignment
  [Arguments]  ${user}  ${targetGroup}  ${doctype}  ${description}=Foosignment
  ${docId}=  Get Element Attribute  xpath=//section[@class='accordion' and @data-doc-type='${doctype}']@data-doc-id
  Click by test id  create-assignment-editor
  Wait until  Element should be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[@class='bubble-dialog']/div
  Fill assignment editor  bubble-dialog[@data-test-id='create-assignment-bubble']  ${targetGroup}  ${docId}  ${user}  ${description}
  Click element  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']//button[@data-test-id='bubble-dialog-ok']
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[@class='bubble-dialog']/div
