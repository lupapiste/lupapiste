*** Settings ***

Documentation  Authority admin edits municipality tags and authority tests them
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Admin adds new tags
  Sipoo logs in
  Go to page  applications
  Wait until  Element should be visible  xpath=//div[@class="tags-editor-component"]//li[@class='tag'][1]
  ${tagCount} =  Get Matching Xpath Count  xpath=//div[@class="tags-editor-component"]//li[@class="tag"]
  Click by test id  add-tag-button
  Input text by test id  edit-tag-input-${tagCount}  kalamaa
  Wait until  Element should contain  xpath=//span[@data-test-id="tag-label-${tagCount}"]  kalamaa
  Wait until  Saved
  [Teardown]  Logout

# Using tags in application

Sonja logs in and creates another application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Set Suite Variable  ${appname2}  notice2${secs}
  Create application the fast way  ${appname2}  753-423-2-40  kerrostalo-rivitalo

Sonja sets tags for application
  Open application  ${appname}  ${propertyId}
  Wait until  Element should be visible  //div[@id='side-panel']//button[@id='open-notice-side-panel']
  Open side panel  notice
  Select from autocomplete  div#notice-panel  ylämaa
  Select from autocomplete  div#notice-panel  kalamaa
  Wait until  Xpath should match X times  //div[@id='notice-panel']//ul[@class="tags"]//li[@class="tag"]  2

Sonja sees tags component where tags are grouped by organizations
  Go to page  applications
  Element should not be visible  xpath=//div[@data-test-id="tag-filter-item"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Wait until  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="tag-filter-item"]
  Wait until  Element should be visible  xpath=//div[@data-test-id="tags-filter-component"]
  Click Element  xpath=//div[@data-test-id="tags-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Xpath should match X times  //div[@data-test-id="tags-filter-component"]//ul[contains(@class, "autocomplete-result")]//li[contains(@class, "autocomplete-group-header")]  2
  Element text should be  xpath=//div[@data-test-id="tags-filter-component"]//ul[contains(@class, "autocomplete-result")]//li[contains(@class, "autocomplete-group-header")][1]  Sipoon rakennusvalvonta
  Click Element  xpath=//div[@data-test-id="tags-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//div[@data-test-id="tags-filter-component"]//input[@data-test-id='autocomplete-input']

Sonja uses tags filter by selecting tag from autocomplete
  Select from autocomplete by test id  tags-filter-component  kalamaa
  Wait Until  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname2}"]
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Logout

Admin removes the last tag
  Sipoo logs in
  Go to page  applications
  Wait until  Element should be visible  xpath=//div[@class="tags-editor-component"]//li[@class='tag'][1]
  ${tagCount} =  Get Matching Xpath Count  xpath=//div[@class="tags-editor-component"]//li[@class="tag"]
  ${lastTagIndex} =  Evaluate  ${tagCount} - 1
  Wait until  Element Should Be Visible  xpath=//span[@data-test-id="tag-label-${lastTagIndex}"]
  Click by test id  remove-tag-button-${lastTagIndex}
  Confirm yes no dialog
  Wait until  Saved
  Wait until  Saved gone
  Wait until  Element Should Not Be Visible  xpath=//span[@data-test-id="tag-label-${lastTagIndex}"]
  Wait until  Xpath should match x times  //div[@class="tags-editor-component"]//li[@class="tag"]  2

Edit ylämaa to alamaa
  Xpath should match x times  //div[@class="tags-editor-component"]//li[@class="tag"]  2
  Click element  xpath=//li[@class='tag' and span[contains(., 'ylämaa')]]//span[contains(@class, 'tag-edit')]
  Wait until  Element should be visible  xpath=//li[@class='tag' and span[contains(., 'ylämaa')]]/input
  Input text by test id  edit-tag-input-0  alamaa
  Focus  xpath=//button[@data-test-id="add-tag-button"]
  Wait until  Saved
  Wait until  Saved gone
  Wait until  Xpath should match x times  //div[@class="tags-editor-component"]//li[@class="tag"]  2
  [Teardown]  Logout

Ronja logs in, sees that tag kalamaa is gone, and label for ylämaa changed to alamaa
  Ronja logs in
  Open application  ${appname}  ${propertyId}
  Wait until  Element should be visible  //div[@id='side-panel']//button[@id='open-notice-side-panel']
  Open side panel  notice
  Wait until  Xpath should match X times  //div[@id="notice-panel"]//ul[@class="tags"]//li[@class="tag"]  1
  Element text should be  xpath=//div[@id="notice-panel"]//ul[@class="tags"]//li//span  alamaa

Ronja doesn't have grouping in tags component, as she belongs only to one organization
  Go to page  applications
  Element should not be visible  xpath=//div[@data-test-id="tag-filter-item"]
  Click by test id  toggle-advanced-filters
  Wait until  Element should be visible  xpath=//div[@data-test-id="tag-filter-item"]
  Wait until  Element should be visible  xpath=//div[@data-test-id="tags-filter-component"]
  Click Element  xpath=//div[@data-test-id="tags-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Xpath should match X times  //div[@data-test-id="tags-filter-component"]//ul[contains (@class, "autocomplete-result")]//li[contains (@class, "autocomplete-group-header")]  0

*** Keywords ***

Saved
  Positive indicator should be visible

Saved gone
  Positive indicator should not be visible
