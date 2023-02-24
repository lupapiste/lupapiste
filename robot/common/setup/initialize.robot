*** Settings ***

Documentation   Init tests
Resource        ../../common_resource.robot

*** Test Cases ***

Initialize
  [Tags]  ie8
  Open browser to login page
  #Set integration proxy off

Initialize (integration)
  [Tags]  integration
  Open browser to login page
  #Set integration proxy off

Initialize (ajanvaraus)
  [Tags]  ajanvaraus
  Open browser to login page
  #Set integration proxy off
