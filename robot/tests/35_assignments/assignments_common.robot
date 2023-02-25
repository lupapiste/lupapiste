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
  Wait until  Element should be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[contains(@class, 'bubble-dialog')]/div

Save assignment
  Scroll and click test id  bubble-dialog-ok
  Positive indicator should be visible
  Wait until  Element should not be visible  xpath=//bubble-dialog[@data-test-id='create-assignment-bubble']/div[contains(@class, 'bubble-dialog')]/div

Create assignment
  [Arguments]  ${user}  ${targetGroup}  ${doctype}  ${description}=Foosignment  ${assignmentIdx}=1
  ${docId}=  Get Element Attribute  xpath=//section[contains(@class, 'accordion') and @data-doc-type='${doctype}']  data-doc-id
  Open assignment editor
  Fill assignment editor  bubble-dialog[@data-test-id='create-assignment-bubble']  ${targetGroup}  ${docId}  ${user}  ${description}
  Save assignment
  Wait until  Element should contain  xpath=(//section[@data-doc-type='${doctype}']//div[@data-test-id='accordion-assignment'])[${assignmentIdx}]//div[@data-test-id='assignment-text']  ${description}

Create attachment assignment
  [Arguments]  ${user}  ${targetGroup}  ${attType}  ${description}=Attsignment  ${assignmentIdx}=1
  ${attId}=  Get element attribute  xpath=//tr[@data-test-type='${attType}']  attachment-id
  Open assignment editor
  Fill assignment editor  bubble-dialog[@data-test-id='create-assignment-bubble']  ${targetGroup}  ${attId}  ${user}  ${description}
  Save assignment
  Wait until  Element should contain  xpath=(//tr[@data-test-id='attachment-assignments-${attId}']//div[@data-test-id='accordion-assignment'])[${assignmentIdx}]//div[@data-test-id='assignment-text']  ${description}

Open assignments search
  Go to page  applications
  Wait until  Element should be visible  xpath=//label[@for='searchTypeAssignments']
  Scroll to and click xpath results  //label[@for='searchTypeAssignments']

Open applications search
  Go to page  applications
  Wait until  Element should be visible  xpath=//label[@for='searchTypeApplications']
  Scroll to and click xpath results  //label[@for='searchTypeApplications']

Count of open assignments is
  [Arguments]  ${count}
  Wait until  Element should be visible  xpath=//label[@for='searchTypeAssignments']
  Run Keyword Unless  ${count}==0  Wait until  Element should contain  xpath=//label[@for='searchTypeAssignments']  ${count}
  Run Keyword If  ${count}==0  Wait until  Element should not contain  xpath=//label[@for='searchTypeAssignments']  ${count}

Edit handler
  [Arguments]  ${index}  ${person}  ${role}
  Select from autocomplete by test id  edit-person-${index}  ${person}
  Select from autocomplete by test id  edit-role-${index}  ${role}

Assignment text contains
  [Arguments]  ${index}  ${text}
  Wait until  Element should contain  jquery=div[data-test-id=automatic-assignment-${index}] div[data-test-id=assignment-text]  ${text}

Assignment text does not contain
  [Arguments]  ${index}  ${text}
  Wait until  Element should not contain  jquery=div[data-test-id=automatic-assignment-${index}] div[data-test-id=assignment-text]  ${text}

Automatic assignment
  [Arguments]  ${index}  ${count}=1  ${assignee}=${EMPTY}  ${type}=Aita ja asema
  Run keyword if  '${count}'=='1'  Assignment text contains  ${index}  Hakemuksella on käsittelemätön päivitys
  Run keyword unless  '${count}'=='1'  Assignment text contains  ${index}  Hakemuksella on ${count} käsittelemätöntä päivitystä
  Run keyword if  '${assignee}'=='${EMPTY}'  Assignment text does not contain  ${index}  : Hakemuksella
  Run keyword unless  '${assignee}'=='${EMPTY}'  Assignment text contains  ${index}  ${assignee}: Hakemuksella
  Assignment text contains  ${index}  ${type}
