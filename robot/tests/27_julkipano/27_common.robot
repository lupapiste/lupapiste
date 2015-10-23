*** Settings ***
Resource  ../../common_resource.robot

*** Keywords ***
Create bulletins
  [Arguments]  ${count}
  As Sonja
  Create bulletins the fast way  11

Bulletin list should have no rows
  Element should not be visible  //table[@id="application-bulletins-list"]/tbody/tr

Bulletin list should have rows
  [Arguments]  ${rows}
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]/tbody/tr
  Table with id should have rowcount  application-bulletins-list  ${rows}

Bulletin list should have rows and text
  [Arguments]  ${rows}  ${text}
  Wait until  Element should be visible  //table[@id='application-bulletins-list']//td[contains(text(), "${text}")]
  Bulletin list should have rows  ${rows}

Bulletin list should not have text
  [Arguments]  ${text}
  Element should not be visible  //table[@id='application-bulletins-list']//td[contains(text(), "${text}")]

Bulletin button should have bulletins left to fetch
  [Arguments]  ${elements}
  Element text should be  xpath=//span[@data-test-id='bulletins-left']  ${elements}kpl

Load more bulletins
  ${initallyBulletinsLeft}=  Get text  //span[@data-test-id='bulletins-left']
  Click by test id  load-more-bulletins
  Wait until  Element should not be visible  //span[@data-test-id='bulletins-left'][contains(text(), '${initallyBulletinsLeft}')]

Create application and publish bulletin
  [Arguments]  ${address}
  Create application with state  ${address}  753-416-25-22  vapaa-ajan-asuinrakennus  sent
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin

Search bulletins by text
  [Arguments]  ${text}
  Input text  //div[@data-test-id='bulletin-search-field']//input[@type='text']  ${text}