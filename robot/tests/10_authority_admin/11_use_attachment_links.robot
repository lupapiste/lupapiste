*** Settings ***

Documentation  Authority admin sets organization to use attachment links instead of files
Resource       ../../common_resource.robot

*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

Authority admin enables attachment links
  Open browser to login page
  Sipoo logs in
  Go to page  backends
  Toggle links

Authority admin sees attachment links enabled
  Reload page
  Go to page  backends
  Checkbox should be selected  attachments-as-links-enabled

Authority admin disables attachment links
  Go to page  backends
  Toggle links

Authority admin sees attachment links disabled
  Reload page
  Go to page  backends
  Checkbox should not be selected  attachments-as-links-enabled
  Logout

*** Keywords ***

Toggle links
  [Arguments]
  Wait and click  attachments-as-links-enabled
  Positive indicator should be visible


#Setting maps disabled again after the tests
#  Set integration proxy off
