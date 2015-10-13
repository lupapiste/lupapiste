*** Settings ***

Documentation   Admin edits authority admin users
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja publishes an application as a bulletin
  Sonja logs in
  Create application with state  Latokuja 3  753-416-25-22  kerrostalo-rivitalo  sent
  Click by test id  publish-bulletin
  Logout

Unlogged user sees Sonja's bulletin
  Go to bulletins page
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]/tbody/*/td[contains(text(), "Latokuja 3")]
