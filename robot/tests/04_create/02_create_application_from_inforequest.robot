*** Settings ***

Documentation   Mikko creates a new inforequest and then an application from it.
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../38_handlers/handlers_resource.robot

*** Variables ***

${propertyid}   753-416-25-30

*** Test Cases ***

# -----------------------
# Applicant (Mikko)
# -----------------------

Mikko creates a new inforequest
  Mikko logs in
  Create inforequest the fast way  create-app-from-info  360603.153  6734222.95  ${propertyid}  kerrostalo-rivitalo  Jiihaa

There are no attachments at this stage
  Element should not be visible  xpath=//*[@data-test-id='inforequest-attachments-table']
  Element should be visible  xpath=//*[@data-test-id='inforequest-attachments-no-attachments']
  [Teardown]  Logout

# -----------------------
# Authority
# -----------------------

Sonja logs in and assigns herself as the application handler
  Sonja logs in
  Open inforequest  create-app-from-info  ${propertyid}
  Assign to Sonja
  [Teardown]  Logout

# -----------------------
# Applicant (Mikko)
# -----------------------

Mikko is back
  Mikko logs in
  Open inforequest  create-app-from-info  ${propertyid}

Mikko creates new application from inforequest
  Convert inforequest to application

Proper attachment templates are present
  Open tab  attachments
  Wait until  Element should be visible  xpath=//rollup[@data-test-level='accordion-level-0']

Sonja is the handler
  General handler is  Sibbo Sonja

Mikko closes application
  Cancel current application
  Wait Until  Element should be visible  applications
  [Teardown]  Logout

# -----------------------
# Authority admin
# -----------------------

Sipoo enables handler removal for converted applications
  Sipoo logs in
  Go to page  applications
  Toggle not selected  remove-handlers-from-converted-application
  Toggle toggle  remove-handlers-from-converted-application

Checkbox survives reload
  Reload page
  Toggle selected  remove-handlers-from-converted-application
  [Teardown]  Logout

# -----------------------
# Applicant (Pena)
# -----------------------

Pena logs in and creates new inforequest
  Pena logs in
  Create inforequest the fast way  Information  360603.153  6734222.95  ${propertyid}  pientalo  Hello world!
  Open inforequest  Information  ${propertyid}
  [Teardown]  Logout

# -----------------------
# Authority
# -----------------------

Sonja logs in again and assigns herself as the application handler
  Sonja logs in
  Open inforequest  Information  ${propertyid}
  Assign to Sonja
  [Teardown]  Logout

# -----------------------
# Applicant (Pena)
# -----------------------

Pena is back
  Pena logs in
  Open inforequest  Information  ${propertyid}

Pena creates new application from inforequest
  General handler is  Sibbo Sonja
  Convert inforequest to application

No general handler
  No such test id  handler-0
  [Teardown]  Logout


*** Keywords ***

Assign to Sonja
  Click element  jquery=a[data-test-id=edit-handlers]:visible
  Wait until element is visible  jquery:button[data-test-id=add-handler]:visible
  Click element  jquery=button[data-test-id=add-handler]:visible
  Edit handler  0  Sonja  Käsittelijä
  Scroll and click  [data-test-id=edit-handlers-back]:visible

Convert inforequest to application
  Click by test id  inforequest-convert-to-application
  Wait until  Element should be visible  application
  Wait until  Element Text Should Be  jquery=span[data-test-id=application-property-id]:visible  ${propertyid}
  Wait until  Element should be visible  xpath=//*[contains(text(),'Hankkeen kuvaus')]
