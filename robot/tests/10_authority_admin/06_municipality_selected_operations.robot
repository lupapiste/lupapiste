*** Settings ***

Documentation   Authority admin edits organization specific selected operations
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot


*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

Operation tree does have 'Asuinkerrostalon tai rivitalon rakentaminen' in it
  Mikko logs in
  Go to operation tree  Latokuja 1, Sipoo  753  753-416-25-30
  Click tree item by text  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Click tree item by text  "Uuden rakennuksen rakentaminen"
  Set Suite Variable  ${element}  xpath=//section[@id='create-part-2']//div[@class='operation-tree tree-control']//span[contains(text(),'Asuinkerrostalon tai rivitalon rakentaminen')]
  Wait until  Element should be visible  ${element}
  # for IE8
  Focus  ${element}
  Wait until  Element should be visible  ${element}
  Logout

AuthAdmin removes 'Uuden rakennuksen rakentaminen' from selected operations
  Sipoo logs in
  Go to page  operations

  # => (ns lupapalvelu.operations)
  # => (count (filter (fn [[_ v]] (#{permit/R permit/P permit/YI permit/YL permit/YM permit/MAL permit/VVVL permit/KT permit/MM} (:permit-type v) ))  operations))
  Wait until  Xpath Should Match X Times  //table[@data-test-id='organization-selected-operations']//tr[@class='sel-op-row']  76
  Xpath Should Match X Times  //span[contains(text(),'Asuinkerrostalon tai rivitalon rakentaminen')]  1

  Click by test id  authadmin-edit-selected-operations

  ${dialogPath} =  Set Variable  xpath=//div[@id='dialog-edit-selected-operations']
  # Remove the 'Asuinkerrostalon tai rivitalon rakentaminen' operation
  Wait until  Element should be visible  ${dialogPath}//select[@class='selectm-target']//option[contains(text(),'Asuinkerrostalon tai rivitalon rakentaminen')]
  Click element  ${dialogPath}//select[@class='selectm-target']//option[contains(text(),'Asuinkerrostalon tai rivitalon rakentaminen')]
  Click element  ${dialogPath}//button[@data-loc='selectm.remove']
  # Save
  Click element  ${dialogPath}//button[@data-loc='selectm.ok']
  Wait until  Element should not be visible  ${dialogPath}

  Wait until  Xpath Should Match X Times  //span[contains(text(),'Asuinkerrostalon tai rivitalon rakentaminen')]  0

  Logout

Operation tree does not have 'Asuinkerrostalon tai rivitalon rakentaminen' in it
  Mikko logs in
  Go to operation tree  Latokuja 1, Sipoo  753  753-416-25-30
  Click tree item by text  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Click tree item by text  "Uuden rakennuksen rakentaminen"

   Wait Until  Page Should Not Contain  ${element}
#  Wait until  Element should not be visible  ${element}
  # for IE8
#  Focus  ${element}
#  Wait until  Element should not be visible  ${element}
  Logout

#Setting maps disabled again after the tests
#  Set integration proxy off


*** Keywords ***

Go to operation tree
  [Arguments]  ${address}  ${municipality}  ${propertyId}
  Go to page  applications
  Click by test id  applications-create-new-application
  Input Text  create-search  ${propertyId}
  Click enabled by test id  create-search-button
  Wait until  Element should be visible  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']
  Textfield Value Should Be  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']  ${propertyId}
  Wait Until  Selected Municipality Is  ${municipality}
  Execute Javascript  $("div[id='popup-id'] input[data-test-id='create-address']").val("${address}").change();
  Set animations off
  Click by test id  create-continue
