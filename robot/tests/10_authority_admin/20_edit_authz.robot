*** Settings ***

Documentation   Authority admin changes his/her authz
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin goes to the authority admin page
  Sipoo logs in
  Wait until page contains  Organisaation viranomaiset

Authority admin removes Ronja
  Element should be visible  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='ronja.sibbo@sipoo.fi']//button[@data-op='removeFromOrg']
  Click element  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='ronja.sibbo@sipoo.fi']//button[@data-op='removeFromOrg']
  Confirm  dynamic-yes-no-confirm-dialog
  Page should not contain  ronja.sibbo@sipoo

Authority admin tries to remove authorityAdmin from him/herself when no other admins exist
  Element should be visible  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='admin@sipoo.fi']//button[@data-op='editUser']
  Click element  xpath=//div[contains(@class, 'admin-users-table')]//tr[@data-user-email='admin@sipoo.fi']//button[@data-op='editUser']
  Wait until  Element should be visible  //label[@for='role-check-box-authorityAdmin']
  Click element  //label[@for='role-check-box-reader']
  Click element  //label[@for='role-check-box-authorityAdmin']
  Click enabled by test id  authz-roles-save
  Negative indicator should be visible
