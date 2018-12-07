*** Settings ***

Documentation   Authority admin adds handlers and task triggers
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Ronja logs in as authority
  Ronja logs in

Ronja checks out Sipoo admin interface
  Click Element  header-user-dropdown
  Click Element  jquery:#header-user-dropdown > ul > li:nth-child(2)
  Authority-admin front page should be open

Ronja returns to normal authority role
  Click Element  header-user-dropdown
  Click Element  jquery:#header-user-dropdown > ul > li:first-child
  User role should be  authority
  Authority applications page should be open
