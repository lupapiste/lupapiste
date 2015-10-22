*** Settings ***

Documentation   Admin edits authority admin users
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Bulletins should be paginable
  As Sonja
  Create bulletins the fast way  11

  Go to bulletins page
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]/tbody/tr
  Element text should be  xpath=//span[@data-test-id='bulletins-left']  1kpl
  Table with id should have rowcount  application-bulletins-list  10

  Click by test id  load-more-bulletins

  Wait until  Element should not be visible  //button[@data-test-id='load-more-bulletins']
  Table with id should have rowcount  application-bulletins-list  11

Bulletins should be searchable
  As Sonja
  Create bulletins the fast way  15
  Create application with state  Mixintie 15  753-416-25-22  vapaa-ajan-asuinrakennus  sent
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin

  Go to bulletins page
  Input text  //div[@data-test-id='bulletin-search-field']//input[@type='text']  Mixintie 15
  Wait until  Element should be visible  //table[@id='application-bulletins-list']//td[contains(text(), "Mixintie 15")]
  Table with id should have rowcount  application-bulletins-list  1
