*** Settings ***

Documentation   Resources for price catalogue robots
Resource       ../../common_resource.robot

*** Variables ***

${runeberg}             5.2.2020
${april-fools}          1.4.2020
${valid-from-future}   22.4.3000
${valid-until-future}  21.4.3000

*** Keywords ***

Row item visible
  [Arguments]  ${index}  ${item}
  Wait Until Element is visible  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}]

No such row item
  [Arguments]  ${index}  ${item}
  Wait Until  Element should not be visible  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}]

Edit row item
  [Arguments]  ${index}  ${item}  ${value}
  ${selector}=  Set variable  //tr[@data-test-id="catalogue-row-${index}"]//*[@data-test-id="${item}"]
  Run keyword if  '${item}' == 'unit'  Select from list by value  xpath=${selector}  ${value}\
  ...  ELSE  Input text  ${selector}  ${value}

Row item input is
  [Arguments]  ${index}  ${item}  ${value}
  ${selector}=  Set variable  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}]
  Run keyword if  '${item}' == 'unit'  List selection should be  ${selector}  ${value}\
  ...  ELSE  Value should be  ${selector}  ${value}

Row item text is
  [Arguments]  ${index}  ${item}  ${value}
  Wait until  Element text should be  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}]  ${value}

Bad row item
  [Arguments]  ${index}  ${item}
  Wait Until Element is visible  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}].warning

Good row item
  [Arguments]  ${index}  ${item}
  Row item visible  ${index}  ${item}
  Wait Until  Element should not be visible  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=${item}].warning

Move row up
  [Arguments]  ${index}
  Click button  jquery=tr[data-test-id=catalogue-row-${index}] button[data-test-id=move-up]
  Sleep  0.5s

Move row down
  [Arguments]  ${index}
  Click button  jquery=tr[data-test-id=catalogue-row-${index}] button[data-test-id=move-down]
  Sleep  0.5s

Op row field is
  [Arguments]  ${op}  ${code}  ${field}  ${value}
  Wait until  Element text should be  jquery=div[data-test-id=${op}].price-catalogue-operations tr[data-test-id=${code}] td[data-test-id=${field}]  ${value}

Delete row
  [Arguments]  ${index}  ${confirm}=True
  Click by test id  delete-row-${index}
  Run keyword if  ${confirm}  Confirm yes no dialog
  ...  ELSE  Deny yes no dialog

Toggle product constants
  [Arguments]  ${index}
  Wait until  Click link  jquery=tr[data-test-id=catalogue-row-${index}] [data-test-id=toggle-product-constants]
