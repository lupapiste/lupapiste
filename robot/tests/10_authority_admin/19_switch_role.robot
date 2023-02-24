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
  Click Element  jquery:#header-user-dropdown > ul > li:first-child
  Authority-admin front page should be open
  Click Element  header-user-dropdown
  Element should not be visible  jquery:#header-user-dropdown > ul > li > a span:contains('Sipoon rakennusvalvonta')

Ronja returns to normal authority role
  Click Element  jquery:#header-user-dropdown > ul > li:first-child
  User role should be  authority
  Authority applications page should be open
  Click Element  header-user-dropdown
  Element should not be visible  jquery:#header-user-dropdown > ul > li > a > span:contains('Asiontipalvelu')
