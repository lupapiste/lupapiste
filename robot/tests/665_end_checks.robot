*** Settings ***

Documentation   Final checks
Resource        ../common_resource.robot

*** Test Cases ***

Tests passed without frontend errors
  [Tags]  ie8
  There are no frontend errors

Integration tests passed without frontend errors
  [Tags]  integration
  There are no frontend errors
