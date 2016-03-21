*** Settings ***

Documentation   Task utils
Resource        ../../common_resource.robot

*** Keywords ***

Open task
  [Arguments]  ${name}
  Wait until  Element should be visible  xpath=//div[@id='application-tasks-tab']//table[@class="tasks"]//td/a[text()='${name}']
  Scroll to  div#application-tasks-tab table.tasks
  Wait until  Click Element  //div[@id='application-tasks-tab']//table[@class="tasks"]//td/a[text()='${name}']
  Wait Until  Element should be visible  xpath=//section[@id="task"]/h1/span[contains(., "${name}")]
  Wait Until  Element should be visible  taskAttachments
  Wait until  Element should be visible  taskDocgen
