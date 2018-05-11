*** Settings ***

Documentation   Company search filters
Suite Setup     Apply company-application fixture now
Suite Teardown  Logout
Default Tags    company
Resource        ../../common_resource.robot
Resource        company_resource.robot

*** Test Cases ***

Teppo logs in and creates new application
  Teppo logs in
  Create application the fast way  Test Road 8  753-423-13-15  pientalo

Teppo sees two applications listed
  Go to page  applications
  Number of visible applications  2

Teppo creates tag filter Taggy
  Click visible test id  toggle-advanced-filters
  Select from autocomplete by test id  tags-filter-component  Projekti1
  Fill test id  new-filter-name  Taggy
  Click element  jquery=div.submit-button_item button

The first created filter is also the default filter
  jQuery should match X times  button.active[data-test-id=set-Taggy-as-default-filter]  1
  Tag filter is active

Default filter is active after reload
  Reload page
  Tag filter is active
  [Teardown]  Logout

Teppo logs back in and the filter is active
  Teppo logs in
  Tag filter is active


*** Keywords ***

Tag filter is active
  Autocomplete selection by test id is  select-advanced-filter  Taggy
  Number of visible applications  1
