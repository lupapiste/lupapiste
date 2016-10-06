*** Settings ***

Documentation  Authority admin edits organization links
Suite Setup    Apply minimal fixture now
Test Teardown  Logout
Resource       ../../common_resource.robot
Resource       authority_admin_resource.robot

*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

Admin adds new organization link
  Sipoo logs in
  Go to page  backends
  Add link  fancy-link  http://reddit.com

Mikko asks information and sees the new link
  Mikko logs in
  User sees link  fancy-link  http://reddit.com

Admin changes link target
  Sipoo logs in
  Go to page  backends
  Update link  fancy-link  http://slashdot.org

Mikko asks information and sees updated link
  Mikko logs in
  User sees link  fancy-link  http://slashdot.org

Admin removes the link
  Sipoo logs in
  Go to page  backends
  Remove link  fancy-link

Mikko asks information and does not see link
  Mikko logs in
  User does not see link  fancy-link
