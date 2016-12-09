*** Settings ***

Documentation  Authority uses default filter
Suite Teardown  Run Keywords  Logout  Apply minimal fixture now
Resource        ../../common_resource.robot
Suite Setup     Apply minimal fixture now

*** Test Cases ***

Sonja logs in and creates an application
  Sonja logs in
  # FIXME: There is a problem with white space characters in suite variables (Sonja -> Sibbo Sonja, käsittelijää -> Ei käsittelijää)
  Set Suite Variable  ${sonja name}  Sonja
  Set Suite Variable  ${all handlers}  Kaikki
  Set Suite Variable  ${no authority}  käsittelijää
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Sonja opens search page
  Go to page  applications
  Show all applications
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As Descending  Päivitetty
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]

Sonja selects application sorting
  Click sorting field  Tyyppi
  Sorting Should Be Set As Descending  Tyyppi
  Click sorting field  Tyyppi
  Sorting Should Be Set As Ascending  Tyyppi

...selects handler
  Select from autocomplete by test id  handlers-filter-component  ${sonja name}

...adds item into areas filter
  Select from autocomplete by test id  areas-filter-component  Keskusta

...adds item into organizations filter
  Select from autocomplete by test id  organization-filter-component  Sipoon yleisten alueiden rakentaminen

...adds item into operations filter
  Select from autocomplete by test id  operations-filter-component  Asuinkerrostalon tai rivitalon rakentaminen

...adds item into tags filter
  Select from autocomplete by test id  tags-filter-component  ylämaa

...saves MEGA filter
  Save advanced filter  MEGA

...sets MEGA as default
  Click by test id  set-MEGA-as-default-filter

Sonja reloads the page and expects that saved filter is applied as default
  Reload page and kill dev-box
  Show all applications
  Click by test id  toggle-advanced-filters
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "MEGA")]

...filter setup should be shown as default
  Filter should contain tag  handler  ${sonja name}
  Filter should contain tag  areas  Keskusta
  Filter should contain tag  tags  ylämaa
  Filter should contain tag  operations  Asuinkerrostalon tai rivitalon rakentaminen
  Filter should contain tag  organization  Sipoon yleisten alueiden rakentaminen
  Filter item should contain X number of tags  handler  1
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  tags  1
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  1
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]

Sonja removes all but operations filter
  Select from autocomplete by test id  handlers-filter-component  ${all handlers}
  Wait until  Scroll and click  div[data-test-id=areas-filter-component] ul.tags li.tag i
  Wait until  Scroll and click  div[data-test-id=tags-filter-component] ul.tags li.tag i
  Wait until  Scroll and click  div[data-test-id=organization-filter-component] ul.tags li.tag i
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As Ascending  Tyyppi

Sonja closes and opens advanced filters
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As Ascending  Tyyppi

Sonja opens an application and returns to applications page
  Wait Until  Scroll and click  table#applications-list tr[data-test-address="${appname}"]
  Go to page  applications

Filter should be set as before visiting application
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  handler  0
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As Ascending  Tyyppi

Sonja removes the operations filter
  Wait until  Scroll and click  div[data-test-id=operations-filter-component] ul.tags li.tag i
  Filter item should contain X number of tags  operations  0

Sonja sets sorting by location
  Click sorting field  Sijainti
  Sorting Should Be Set As Descending  Sijainti
  Click sorting field  Sijainti
  Sorting Should Be Set As Ascending  Sijainti

Sonja saves sort-by-location filter
  Save advanced filter  sort-by-location

...saved filters should be open
  Wait Until  Element Should Be Visible  //div[@data-test-id="saved-filter-row-sort-by-location"]
  Wait Until  Element Should Be Visible  //div[@data-test-id="saved-filter-row-MEGA"]

Sonja sets sort-by-location filter as default
  Wait Until  Click by test id  set-sort-by-location-as-default-filter
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "sort-by-location")]

Sonja closes saved filters
  Wait Until  Scroll and click  div[data-test-id=advanced-filters] button[data-test-id=toggle-saved-filters]
  Wait Until  Element Should Not Be Visible  //div[@data-test-id="saved-filter-row-MEGA"]

Default filter should be sort-by-location filter
  Reload page and kill dev-box
  Show all applications
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "sort-by-location")]

...no advanced filters shown
  Wait Until  Element should Not be visible  xpath=//div[@data-test-id="advanced-filters"]

...filters and sorting are set
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As Ascending  Sijainti

Sonja selects MEGA filter
  Select from autocomplete by test id  select-advanced-filter  MEGA
  Wait Until  Handler filter should contain text  ${sonja name}
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  tags  1
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  1

Sonja trys to overwrite MEGA filter
  Input text  new-filter-name  MEGA
  Wait Until  Element Should Be Visible  //div[@data-test-id="new-filter-submit-button"]//span[contains(text(), "Nimi on jo käytössä")]

Sonja removes sort-by-location filter
  Wait Until  Scroll and click  div[data-test-id=advanced-filters] button[data-test-id=toggle-saved-filters]
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Wait Until  Scroll and click  div[data-test-id=remove-filter-sort-by-location] button[data-test-id=remove-button]
  Wait Until  Scroll and click  div[data-test-id=remove-filter-sort-by-location] button[data-test-id=cancel-remove-button]
  Wait Until  Scroll and click  div[data-test-id=remove-filter-sort-by-location] button[data-test-id=remove-button]
  Wait Until  Scroll and click  div[data-test-id=remove-filter-sort-by-location] button[data-test-id=confirm-remove-button]
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-sort-by-location"]

Sonja saves foobar filter
  Save advanced filter  foobar
  Wait Until  Element should be visible  //div[@data-test-id="saved-filter-row-foobar"]
  Wait Until  Element should not be visible  //div[@data-test-id="saved-filter-row-sort-by-location"]


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

Sorting Should Be Set As Ascending
  [Arguments]  ${field name}
  Wait until  Element should be visible  //table[@id="applications-list"]//th[@data-test-id="search-column-${field name}"]//i[contains(@class, "lupicon-chevron-small-up")]

Sorting Should Be Set As Descending
  [Arguments]  ${field name}
  Wait until  Element should be visible  //table[@id="applications-list"]//th[@data-test-id="search-column-${field name}"]//i[contains(@class, "lupicon-chevron-small-down")]

Click sorting field
  [Arguments]  ${field name}
  Wait Until  Element should be visible  xpath=//table[@id="applications-list"]//th[@data-test-id="search-column-${field name}"]
  Wait Until  Scroll and click  table#applications-list th[data-test-id=search-column-${field name}]

Save advanced filter
  [Arguments]  ${filter-name}
  Input text  new-filter-name  ${filter-name}
  Wait Until  Scroll and click  div[data-test-id=new-filter-submit-button] button
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(text(), "${filter-name}")]
