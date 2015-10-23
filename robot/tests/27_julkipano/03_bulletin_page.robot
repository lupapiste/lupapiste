*** Settings ***

Documentation   Bulletin page
Suite teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Init Bulletins
  Create bulletins  2
  Go to bulletins page

Bulletin page should have docgen data
  Open bulletin by index  1
