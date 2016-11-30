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

Open assignment editor
  Wait test id visible  create-assignment-editor-button
  Click by test id  create-assignment-editor-button
  Wait until  Element should be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[@class='bubble-dialog']/div

Save assignment
  Scroll and click test id  bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[@class='bubble-dialog']/div

Create assignment
  [Arguments]  ${user}  ${targetGroup}  ${doctype}  ${description}=Foosignment  ${assignmentIdx}=1
  ${docId}=  Get Element Attribute  xpath=//section[@class='accordion' and @data-doc-type='${doctype}']@data-doc-id
  Open assignment editor
  Fill assignment editor  bubble-dialog[@data-test-id='create-assignment-bubble']  ${targetGroup}  ${docId}  ${user}  ${description}
  Save assignment
  Wait until  Element should contain  xpath=(//section[@data-doc-type='${doctype}']//div[@data-test-id='accordion-assignment'])[${assignmentIdx}]//div[@data-test-id='assignment-text']  ${description}

Create attachment assignment
  [Arguments]  ${user}  ${targetGroup}  ${attType}  ${description}=Attsignment  ${assignmentIdx}=1
  ${attId}=  Get element attribute  xpath=//tr[@data-test-type='${attType}']@attachment-id
  Open assignment editor
  Fill assignment editor  bubble-dialog[@data-test-id='create-assignment-bubble']  ${targetGroup}  ${attId}  ${user}  ${description}
  Save assignment
  Wait until  Element should contain  xpath=(//tr[@data-test-id='attachment-assignments-${attId}']//div[@data-test-id='accordion-assignment'])[${assignmentIdx}]//div[@data-test-id='assignment-text']  ${description}

Open assignments search
  Go to page  applications
  Wait until  Element should be visible  xpath=//label[@for='searchTypeAssignments']
  Select Radio Button  searchType  searchTypeAssignments

Count of open assignments is
  [Arguments]  ${count}
  Wait until  Element should be visible  xpath=//label[@for='searchTypeAssignments']
  Wait until  Element should contain  xpath=//label[@for='searchTypeAssignments']  ${count}
