*** Settings ***

Documentation   Init tests
Resource        ../../common_resource.robot

*** Test Cases ***

Initialize
  Open browser to login page
  Apply minimal fixture now
