*** Settings ***

Documentation  Authority admin edits municipality links
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja logs in and creates another application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  rakennuslupa-${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Set Suite Variable  ${appname2}  ya-${secs}
  Create application the fast way  ${appname2}  753-423-2-162  ya-katulupa-vesi-ja-viemarityot

Sonja sees all her organizations in the dropdown of the organization filter autocomplete component
  Go to page  applications
  Open search tab  all
  Element should not be visible  xpath=//div[@data-test-id="organization-filter-item"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="organization-filter-item"]
  Wait until  Element should be visible  xpath=//div[@data-test-id="organization-filter-component"]
  Click Element  xpath=//div[@data-test-id="organization-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="organization-filter-component"]//div[@class="autocomplete-dropdown"]
  Xpath should match X times  //div[@data-test-id="organization-filter-component"]//ul[@class="autocomplete-result"]//li[contains(@class,"autocomplete-result-item")]  3
  Element text should be  xpath=//div[@data-test-id="organization-filter-component"]//ul[@class="autocomplete-result"]//li[@class="autocomplete-result-item"][2]  Sipoon yleisten alueiden rakentaminen
  Click Element  xpath=//div[@data-test-id="organization-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//div[@data-test-id="organization-filter-component"]//div[@class="autocomplete-dropdown"]

Sonja selects the Sipoo's YA filter and sees only the YA application in the applications list
  Select From Autocomplete  div[@data-test-id="organization-filter-component"]  Sipoon yleisten alueiden rakentaminen
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]

Same organization cannot be selected twice as organization filter
  Click Element  xpath=//div[@data-test-id="organization-filter-component"]//span[contains(@class, "autocomplete-selection")]//i[contains(@class, "lupicon-chevron-small-down")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="organization-filter-component"]//div[@class="autocomplete-dropdown"]
  Xpath should match X times  //div[@data-test-id="organization-filter-component"]//ul[@class="autocomplete-result"]//li[contains(@class,"autocomplete-result-item")]  2
  Element should not be visible  xpath=//ul[@class="autocomplete-result"]//li[@class="autocomplete-result-item"]//span[contains(., "Sipoon yleisten alueiden rakentaminen")]
