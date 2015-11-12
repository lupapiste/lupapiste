*** Settings ***

Documentation   Admin edits authority admin users
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Bulletins page is empty at first
  Go to bulletins page
  Bulletin list should have no rows

Sonja publishes an application as a bulletin
  As Sonja
  Create application and publish bulletin  Mixintie 15  753-416-25-22
  Logout

Unlogged user sees Sonja's bulletin
  Go to bulletins page
  Bulletin list should have rows and text  1  Mixintie 15
