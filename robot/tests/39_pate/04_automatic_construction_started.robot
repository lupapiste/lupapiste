*** Settings ***

Documentation   Automatic construction started toggle visible
Suite Setup     Apply pate-enabled fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       pate_resource.robot

*** Test Cases ***

Sipoo admin logs in
  Sipoo logs in
  Go to page  reviews

Automatic construction started toggle
  Toggle selected  automatic-construction-started
  Toggle toggle  automatic-construction-started
  Positive indicator should be visible

Toggle survives reload
  Reload page
  Toggle not selected  automatic-construction-started
  [Teardown]  Logout
