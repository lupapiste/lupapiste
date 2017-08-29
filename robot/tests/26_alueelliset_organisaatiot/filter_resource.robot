*** Settings ***

Documentation  Shared filter resources
Resource       ../../common_resource.robot

*** Keywords ***

Save advanced filter
  [Arguments]  ${filter-name}
  Input text  new-filter-name  ${filter-name}
  Wait Until  Scroll and click  div[data-test-id=new-filter-submit-button] button
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(text(), "${filter-name}")]

Filter item should contain X number of tags
  [Arguments]  ${filter name}  ${amount}
  Wait until  Xpath should match X times  //div[@data-test-id="${filter name}-filter-component"]//ul[contains(@class, 'tags')]//li[contains(@class, 'tag')]  ${amount}

Filter should contain tag
  [Arguments]  ${filter name}  ${text}
  Wait Until  Element Should Contain  xpath=//div[@data-test-id="${filter name}-filter-component"]//ul[contains(@class, 'tags')]//li[contains(@class, 'tag')]//span  ${text}
