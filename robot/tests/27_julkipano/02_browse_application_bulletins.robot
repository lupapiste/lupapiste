*** Settings ***

Documentation   Admin edits authority admin users
Suite Setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Bulletins should be paginable
  Create bulletins  11
  Go to bulletins page

  Bulletin list should have rows  10
  Bulletin button should have bulletins left to fetch  1

  Load more bulletins

  Bulletin list should have rows  11

Bulletins should be searchable
  As Sonja
  Create application and publish bulletin  Mixintie 15  753-416-25-22
  Go to bulletins page

  Search bulletins by text  Mixintie 15
  Bulletin list should have rows and text  1  Mixintie 15

Bulletins should be filterable
  As Velho
  Create application and publish bulletin  Tiaisenpolku 26  297-14-3-16
  Go to bulletins page

  Click Element  xpath=//div[@data-test-id="municipalities-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="municipalities-filter-component"]//div[@class="autocomplete-dropdown"]
  Autocomplete option list should contain  municipalities-filter-component  Koko Suomi  Kuopio  Sipoo

