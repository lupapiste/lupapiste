*** Settings ***

Documentation  Organization handler roles and application handlers
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       handlers_resource.robot

*** Variables ***

${appname}     Handlung
${propertyId}  753-416-25-30

*** Test Cases ***

# -------------------------
# Authority admin
# -------------------------
Sipoo logs in and sees the default handler roles plus one
  Sipoo logs in
  Go to page  assignments
  Handler role is  0  Käsittelijä  Handläggare  Handler  False
  Handler role is  1  KVV-Käsittelijä  KVV-Handläggare  KVV-Handler

Sipoo edits handler roles
  Edit handler role  0  en  Handy  True
  Edit handler role  1  sv  EVVK-Handläggare  True

Sipoo adds new handler role
  Click enabled by test id  add-handler-role
  Handler role is  2  ${EMPTY}  ${EMPTY}  ${EMPTY}

Partly filled roles show required highlights
  No required  2  fi
  No required  2  sv
  No required  2  en
  Warning not visible
  Edit handler role  2  fi  Uusi
  No required  2  fi
  Yes required  2  sv
  Yes required  2  en
  Warning visible
  Edit handler role  2  en  New
  No required  2  fi
  Yes required  2  sv
  No required  2  en
  Warning visible
  Edit handler role  2  sv  Nytt  True
  No required  2  fi
  No required  2  sv
  No required  2  en
  Handler role is  2  Uusi  Nytt  New
  Warning not visible

Partly filled roles are not saved
  Click enabled by test id  add-handler-role
  Edit handler role  3  en  Gone soon
  Reload page
  No such test id  edit-role-3-en
  [Teardown]  Logout

# -------------------------
# Applicant
# -------------------------
Pena creates an inforequest
  Pena logs in
  Create inforequest with state  ${appname}-info  ${propertyId}  pientalo  info

No handlers in inforequest and Pena cannot add one
  No such test id  handler-0
  No such test id  edit-handlers

Pena creates an application
  Create application with state  ${appname}  ${propertyId}  pientalo  open

No handlers in application and Pena cannot add one
  No such test id  handler-0
  No such test id  edit-handlers
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja logs in and can edit handlers
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  No such test id  handler-0
  Click element  xpath=//section[@id='application']//a[@data-test-id='edit-handlers']
  Wait until  Element should be visible  xpath=//section[@id='application']//button[@data-test-id='add-handler']
  Wait test id visible  add-handler
  No such test id  edit-person-0

Sonja adds handler
  Click by test id  add-handler
  Handler is  0  ${EMPTY}  ${EMPTY}
  Edit handler  0  Sibbo Sonja  Käsittelijä
  Save indicated
  Handler is  0  Sibbo Sonja  Käsittelijä

Sonja adds second handler but cannot reuse role
  Click by test id  add-handler
  Unavailable roles  1  Käsittelijä
  Edit handler  1  Sibbo Ronja  KVV-Käsittelijä

Sonja adds third handler
  Click by test id  add-handler
  Unavailable roles  2  Käsittelijä  KVV-Käsittelijä
  Edit handler  2  Sibbo Sonja  Uusi

Roles have been used and adding is disabled
  Test id disabled  add-handler

Sonja removes KVV-Käsittelijä
  Click by test id  remove-handler-1
  Remove indicated
  Test id enabled  add-handler

Sonja edits Käsittelijä
  Edit handler  0  Sibbo Ronja  Käsittelijä
  Indicator should contain text  Tiedot tallennettu

Sonja returns to the application and checks handlers
  Scroll and click test id  edit-handlers-back
  List handler is  0  Sibbo Ronja  Käsittelijä
  List handler is  1  Sibbo Sonja  Uusi
  No such test id  handler-2

Sonja goes to applications page and comes back
  Open application  ${appname}  ${propertyId}
  List handler is  0  Sibbo Ronja  Käsittelijä
  List handler is  1  Sibbo Sonja  Uusi
  No such test id  handler-2
  [Teardown]  Logout

# -------------------------
# Applicant
# -------------------------
Pena logs in and sees handlers
  Pena logs in
  Open application  ${appname}  ${propertyId}
  List handler is  0  Sibbo Ronja  Käsittelijä
  List handler is  1  Sibbo Sonja  Uusi
  No such test id  handler-2

Pena goes to applications page and comes back
  Open application  ${appname}  ${propertyId}
  List handler is  0  Sibbo Ronja  Käsittelijä
  List handler is  1  Sibbo Sonja  Uusi
  No such test id  handler-2

No handlers in inforequest
  Open inforequest  ${appname}-info  ${propertyId}
  No such test id  handler-0
  [Teardown]  Logout

# -------------------------
# Authority admin
# -------------------------
Sipoo logs and fires Ronja
  Sipoo logs in
  Click element  jquery=div.admin-users-table tr[data-user-email="ronja.sibbo@sipoo.fi"] button[data-op=removeFromOrg]
  Confirm  dynamic-yes-no-confirm-dialog

Sipoo renames Käsittelijä handler role
  Go to page  assignments
  Edit handler role  0  fi  Muutettu  True

Sipoo removes Uusi handler role
  Click by test id  remove-role-2
  Remove indicated
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja logs in and sees changes
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  List handler is  0  Sibbo Ronja  Muutettu
  List handler is  1  Sibbo Sonja  Uusi

Both handlers are disabled in the edit view
  Click by test id  edit-handlers
  Wait test id visible  add-handler
  Handler is  0  Sibbo Ronja  Muutettu
  Handler is  1  Sibbo Sonja  Uusi
  Handler disabled  0
  Handler disabled  1

Sonja opens inforequest and changes language to English
  Open inforequest  ${appname}-info  ${propertyId}
  Language to  EN
  No such test id  handler-0
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//a[@data-test-id='edit-handlers']
  Click element  xpath=//section[@id='inforequest']//a[@data-test-id='edit-handlers']
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//button[@data-test-id='add-handler']
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='add-handler']

Ronja cannot be selected
  Test id autocomplete options check  edit-person-0  false  Sibbo Ronja

Removed role cannot be selected
  Test id autocomplete options check  edit-role-0  false  New

Sonja assigns handler
  Edit handler  0  Sibbo Sonja  Handy
  Positive indicator should not be visible
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='edit-handlers-back']
  List handler is  0  Sibbo Sonja  Handy
  [Teardown]  Logout

# -------------------------
# Applicant
# -------------------------
Pena sees the handler on the inforequest
  Pena logs in
  Open inforequest  ${appname}-info  ${propertyId}
  List handler is  0  Sibbo Sonja  Muutettu
  [Teardown]  Logout

# -------------------------
# Authority admin
# -------------------------
Sipoo logs in to revisit deleted handler roles
  Sipoo logs in
  Go to page  assignments
  No such test id  edit-role-2-fi

Show also the deleted roles
  Scroll and click test id  show-all-handler-roles-label
  Disabled handler role is  2  Uusi  Nytt  New
  Wait test id visible  recover-role-2
  Handler role is  0  Muutettu  Handläggare  Handy  False
  Handler role is  1  KVV-Käsittelijä  EVVK-Handläggare  KVV-Handler

Recover role
  Click by test id  recover-role-2
  Recovery indicated
  Handler role is  2  Uusi  Nytt  New

Delete KVV Handler role
  Click by test id  remove-role-1
  Remove indicated
  Disabled handler role is  1  KVV-Käsittelijä  EVVK-Handläggare  KVV-Handler

Changes survice reload
  Reload page
  Scroll to  table.handler-roles
  Toggle not selected  show-all-handler-roles
  Kill dev-box
  No such test id  edit-role-1-fi
  Handler role is  2  Uusi  Nytt  New
  Scroll and click test id  show-all-handler-roles-label
  Disabled handler role is  1  KVV-Käsittelijä  EVVK-Handläggare  KVV-Handler
  Handler role is  2  Uusi  Nytt  New
  [Teardown]  Logout

# -------------------------
# Authority
# -------------------------
Sonja logs in creates application
  Sonja logs in
  Create application with state  Duplicate fix  ${propertyId}  pientalo  submitted

No handlers in application
  No such test id  handler-0

Sonja approves application and becomes handler automatically
  Approve application no dialogs
  List handler is  0  Sibbo Sonja  Muutettu
  [Teardown]  Logout
