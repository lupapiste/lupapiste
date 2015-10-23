*** Settings ***

Documentation   Admin edits authority admin users
Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Bulletins page is empty at first
  Go to bulletins page
  Bulletin list should have no rows

Sonja publishes an application as a bulletin
  As Sonja
  Create application with state  Latokuja 3  753-416-25-22  kerrostalo-rivitalo  sent
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin
  Logout

Unlogged user sees Sonja's bulletin
  Go to bulletins page
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]//td[contains(text(), "Latokuja 3")]
  Table with id should have rowcount  application-bulletins-list  1
