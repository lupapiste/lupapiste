*** Settings ***

Documentation   Admin edits authority admin users
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./julkipano_common.robot

*** Test Cases ***

Bulletins page is empty at first
  Go to bulletins page
  Bulletin list should have no rows

Sonja publishes an application as a bulletin
  As Olli
  Create application and publish bulletin  Vaalantie 540  564-404-26-102
  Logout

Unlogged user sees Sonja's bulletin
  Go to bulletins page
  Bulletin list should have rows and text  1  Vaalantie 540
