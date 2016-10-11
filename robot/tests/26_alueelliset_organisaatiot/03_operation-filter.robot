*** Settings ***

Documentation  Authority admin edits municipality links
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja logs in and creates another application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  kerrostalo-${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Set Suite Variable  ${appname2}  teolisuusrakennus-${secs}
  Create application the fast way  ${appname2}  753-423-2-162  teollisuusrakennus
  Set Suite Variable  ${appname3}  katulupa-${secs}
  Create application the fast way  ${appname3}  753-423-2-164  ya-katulupa-vesi-ja-viemarityot

Sonja sees all her operations in the dropdown of the operations filter autocomplete component
  Go to page  applications
  Show all applications
  Element should not be visible  xpath=//div[@data-test-id="operation-filter-item"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="operation-filter-item"]
  Wait until  Element should be visible  xpath=//div[@data-test-id="operations-filter-component"]
  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="operations-filter-component"]//div[@class="autocomplete-dropdown"]
  Autocomplete option list should contain  operations-filter-component  Kiinteistötoimitukset  Kaavat ja kaavamuutokset  Ympäristölupa  Vapautushakemus vesijohtoon ja viemäriverkostoon liittämisvelvollisuudesta  Rakentamisen lupa  Poikkeamisen hakeminen  Maa-aineksen ottolupa  Ympäristöilmoitus  Yleisten alueiden lupa
  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//div[@data-test-id="operations-filter-component"]//div[@class="autocomplete-dropdown"]
  # BUG in closing the dropdown - sometimes does not close but flickers -> false error

Sonja selects the Asuinkerrostalo operation and sees only that application
  Select From Autocomplete  div[@data-test-id="operations-filter-component"]  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]


Sonja selects Asuinkerrostalo and Katulupa operations and sees only those applications
  #Select From Autocomplete  div[@data-test-id="operations-filter-component"]  Asuinkerrostalon tai rivitalon rakentaminen
  # Asuinkerrostalo already selected in previous test
  Autocomplete selectable values should not contain  div[@data-test-id="operations-filter-component"]  Asuinkerrostalon tai rivitalon rakentaminen
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Wait until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Select From Autocomplete  div[@data-test-id="operations-filter-component"]  Vesi- ja viemäritöiden tekeminen
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname3}"]
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
