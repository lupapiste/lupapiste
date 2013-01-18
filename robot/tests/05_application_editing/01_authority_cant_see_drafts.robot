*** Settings ***

Documentation  Seija should see only applications from Sipoo
Resource       ../../common_resource.txt

*** Test Cases ***

Sonja should not see zero applications
  Sonja logs in
  Wait until  Number of visible applications on page  applications  0
  Logout
