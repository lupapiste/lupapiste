*** Settings ***

Documentation   Authority admin can enable asianhallinta
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sipoo can't see asianhallinta configs, because no ftp user is set in scope
  Sipoo logs in
  Element should not be visible  xpath=//section[@data-test-id="asianhallinta"]
  Log out

Kuopio logs in and sees asianhallinta configs
  Kuopio logs in
  Go to page  backends
  Element should be visible  xpath=//section[@data-test-id="asianhallinta"]

Asianhallinta is enabled in minimal with version 1.1
  Checkbox should be selected  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  List selection should be  xpath=//section[@data-test-id="asianhallinta"]//select[@data-test-id="select-asianhallinta-version"]  1.1

Unchecking checkbox results in instant save
  Scroll to test id  enable-asianhallinta
  Unselect checkbox  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  Positive indicator should be visible
