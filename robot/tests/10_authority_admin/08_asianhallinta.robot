*** Settings ***

Documentation   Authority admin can enable asianhallinta
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sipoo can't see asianhallinta configs, because no ftp user is set in scope
  Sipoo logs in
  Element should not be visible  xpath=//section[@data-test-id="asianhallinta"]
  Log out

Kuopio logs in and sees asianhallinta configs
  Kuopio logs in
  Element should be visible  xpath=//section[@data-test-id="asianhallinta"]
