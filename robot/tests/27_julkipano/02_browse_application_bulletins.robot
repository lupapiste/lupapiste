*** Settings ***

Documentation   Admin edits authority admin users
Suite Setup     Apply minimal fixture now
Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Bulletins should be paginable
  Create bulletins  11
  Go to bulletins page

  Bulletin list should have rows  10
  Bulletin button should have bulletins left to fetch  1

  Load more bulletins

  Bulletin list should have rows  11

Bulletins should be searchable
  Create application and publish bulletin  Mixintie 15
  Go to bulletins page

  Bulletin list should not have text  Mixintie 15
  Search bulletins by text  Mixintie 15
  Bulletin list should have rows and text  1  Mixintie 15
