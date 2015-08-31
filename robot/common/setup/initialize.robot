*** Settings ***

Documentation   Init tests
Resource        ../../common_resource.robot

*** Test Cases ***

Initialize
  Open browser to login page
  Set integration proxy off
  #Apply minimal fixture now

Initialize (integration)
  [Tags]  integration
  Open browser to login page
  Set integration proxy off
  Apply minimal fixture now
