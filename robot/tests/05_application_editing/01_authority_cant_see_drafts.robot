*** Settings ***

Documentation  Seija should see only applications from Sipoo
Resource       ../../common_resource.robot

*** Test Cases ***

Sonja should not see zero applications
  Sonja logs in
  Wait until  Number of requests on page  application  0
  Wait until  Number of requests on page  inforequest  0
  Logout
