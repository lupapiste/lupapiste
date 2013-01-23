*** Settings ***

Documentation  Seija should see only applications from Sipoo
Test setup      Wait Until  Ajax calls have finished
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja should not see zero applications
  Sonja logs in
  Number of visible applications on page  applications  0
  Logout
