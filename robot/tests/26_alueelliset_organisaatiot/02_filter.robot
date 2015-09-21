*** Settings ***

Documentation  Authority uses default filter
Suite teardown  Logout
Resource       ../../common_resource.robot
Suite setup  Apply minimal fixture now

*** Test Cases ***

Sonja logs in and creates an application
  Sonja logs in
  Set Suite Variable  ${sonja name}  Sonja
  Set Suite Variable  ${all handlers}  Kaikki
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Sonja opens search page
  Go to page  applications
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="areas-filter-component"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Päivitetty  desc

Sonja selects application sorting
  Click sorting field  Tyyppi
  Sorting Should Be Set As  Tyyppi  desc
  Click sorting field  Tyyppi
  Sorting Should Be Set As  Tyyppi  asc

Sonja selects handler
  Select From Autocomplete  div[@data-test-id="handlers-filter-component"]  ${sonja name}

Sonja adds item into areas filter
  Select From Autocomplete  div[@data-test-id="areas-filter-component"]  Keskusta

Sonja adds item into organizations filter
  Select From Autocomplete  div[@data-test-id="organization-filter-component"]  Sipoon yleisten alueiden rakentaminen

Sonja adds item into operations filter
  Select From Autocomplete  div[@data-test-id="operations-filter-component"]  Asuinkerrostalon tai rivitalon rakentaminen

Sonja adds item into tags filter
  Select From Autocomplete  div[@data-test-id="tags-filter-component"]  ylämaa

Sonja saves default filter
  Click by test id  save-advanced-filters
  Reload Page
  Wait Until  Element should be visible  xpath=//div[contains(@class, "filter-row advanced")]
  Handler filter should contain text  ${sonja name}
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  tags  1
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  1
  Filter should contain tag  areas  Keskusta
  Filter should contain tag  tags  ylämaa
  Filter should contain tag  operations  Asuinkerrostalon tai rivitalon rakentaminen
  Filter should contain tag  organization  Sipoon yleisten alueiden rakentaminen

Sonja removes all but operations filter
  Select From Autocomplete  div[@data-test-id="handlers-filter-component"]  ${all handlers}
  Wait until  Click Element  xpath=//div[@data-test-id="areas-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Wait until  Click Element  xpath=//div[@data-test-id="tags-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Wait until  Click Element  xpath=//div[@data-test-id="organization-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja closes and opens advanced filters
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[contains(@class, "filter-row advanced")]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja opens an application and returns to application page
  Wait Until  Click Element  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Go to page  applications
  Wait Until  Element should be visible  xpath=//div[contains(@class, "filter-row advanced")]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja removes the operations filter
  Wait until  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Click sorting field  Päivitetty
  Filter item should contain X number of tags  operations  0
  Sorting Should Be Set As  Päivitetty  desc

Sonja saves empty default filter
  Click by test id  save-advanced-filters
  Reload page
  Click by test id  toggle-advanced-filters
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Päivitetty  desc



*** Keywords ***
Handler filter should contain text
  [Arguments]  ${text}
  Wait until  Element should be visible  //div[@data-test-id="handler-filter-component"]//span[@class="autocomplete-selection"]//span[contains(text(), "${text}")]

Filter item should contain X number of tags
  [Arguments]  ${filter name}  ${amount}
  Wait until  Xpath should match X times  //div[@data-test-id="${filter name}-filter-component"]//ul[@class="tags"]//li[@class="tag"]  ${amount}

Filter should contain tag
  [Arguments]  ${filter name}  ${text}
  Wait Until  Element Should Contain  xpath=//div[@data-test-id="${filter name}-filter-component"]//ul[@class="tags"]//li[@class="tag"]//span  ${text}

Sorting Should Be Set As
  [Arguments]  ${field name}  ${order}
  Wait until  Element should be visible  //table[@id="applications-list"]//th[contains(text(), "${field name}") and contains(@class, "${order}")]

Click sorting field
  [Arguments]  ${field name}
  Wait Until  Click Element  xpath=//table[@id="applications-list"]//th[contains(text(), "${field name}")]
