*** Settings ***

Documentation   Authority admin edits organization specific selected operations
Suite teardown  Logout
Resource        ../../common_resource.robot


*** Test Cases ***

Operation tree does have 'Asuinrakennuksen rakentaminen' in it
  Apply minimal fixture now
  Mikko logs in
  Go to operation tree  Latokuja 1, Sipoo  753  753-416-25-30
  Click tree item by text  "Rakentaminen ja purkaminen (talot, grillikatokset, autotallit, remontointi)"
  Click tree item by text  "Uuden rakennuksen rakentaminen (mökit, omakotitalot, saunat, julkiset rakennukset)"
  Set Suite Variable  ${element}  xpath=//section[@id='create-part-2']//div[@class='operation-tree tree-control']//span[contains(text(),'Asuinrakennuksen rakentaminen')]
  Wait until  Element should be visible  ${element}
  # for IE8
  Focus  ${element}
  Wait until  Element should be visible  ${element}
  Logout

AuthAdmin removes 'Uuden rakennuksen rakentaminen (mökit, omakotitalot, saunat, julkiset rakennukset)' from selected operations
  Sipoo logs in
  # Open dialog

  # ** TODO: Korjaa tama! **
  # Total count of operations is 44
  Wait until  Xpath Should Match X Times  //section[@id='admin']//table[@data-test-id='organization-selected-operations']//tr[@class='sel-op-row']  44

  Click by test id  authadmin-edit-selected-operations

  ${dialogPath} =  Set Variable  xpath=//div[@id='dialog-edit-selected-operations']
  # Remove the 'Asuinrakennuksen rakentaminen' operation
  Wait until  Element should be visible  ${dialogPath}//select[@class='selectm-target']//option[contains(text(),'Asuinrakennuksen rakentaminen')]
  Click element  ${dialogPath}//select[@class='selectm-target']//option[contains(text(),'Asuinrakennuksen rakentaminen')]
  Click element  ${dialogPath}//button[@data-loc='selectm.remove']
  # Save
  Click element  ${dialogPath}//button[@data-loc='selectm.ok']
  Wait until  Element should not be visible  ${dialogPath}
  Logout

Operation tree does not have 'Asuinrakennuksen rakentaminen' in it
  Mikko logs in
  Go to operation tree  Latokuja 1, Sipoo  753  753-416-25-30
  Click tree item by text  "Rakentaminen ja purkaminen (talot, grillikatokset, autotallit, remontointi)"
  Click tree item by text  "Uuden rakennuksen rakentaminen (mökit, omakotitalot, saunat, julkiset rakennukset)"

   Wait Until  Page Should Not Contain  ${element}
#  Wait until  Element should not be visible  ${element}
  # for IE8
#  Focus  ${element}
#  Wait until  Element should not be visible  ${element}
  Logout


*** Keywords ***

Go to operation tree
  [Arguments]  ${address}  ${municipality}  ${propertyId}
  Go to page  applications
  Click by test id  applications-create-new-application
  Execute Javascript  $("input[data-test-id='create-property-id']").val("${propertyId}").change();
  Wait Until  List Selection Should Be  xpath=//select[@data-test-id='create-municipality-select']  ${municipality}
  Input text by test id  create-address  ${address}
  Set animations off
  Click enabled by test id  create-continue

