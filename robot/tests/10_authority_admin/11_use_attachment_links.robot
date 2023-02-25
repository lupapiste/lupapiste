*** Settings ***

Documentation  Authority admin sets organization to use attachment links instead of files
Resource       ../../common_resource.robot

*** Test Cases ***

Authority admin enables attachment links
  Sipoo logs in
  Go to page  backends
  Toggle links

Authority admin sees attachment links enabled
  Reload page and kill dev-box
  Go to page  backends
  Checkbox should be selected  attachments-as-links-enabled

Authority admin disables attachment links
  Go to page  backends
  Toggle links

Authority admin sees attachment links disabled
  Reload page and kill dev-box
  Go to page  backends
  Checkbox should not be selected  attachments-as-links-enabled
  Logout

*** Keywords ***

Toggle links
  Click label  attachments-as-links-enabled
  Positive indicator should be visible
