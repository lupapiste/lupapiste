*** Settings ***

Documentation   Can't submit tyolupa application when linked agreement not finished or not signed
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Sonja creates submitted sijoitussopimus
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${ss_app}  SS_${secs}
  Create application  ${ss_app}  753  753-416-45-3  YA-sijoituslupa
  Select from list by value  permitSubtypeSelect  sijoitussopimus
  Submit application
  ${linkPermitAppId} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${linkPermitAppId}

Sonja creates tyolupa
  Go to page  applications
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${digging_app}  Tyolupa_${secs}
  Create application  ${digging_app}  753  753-416-45-3  YA-kaivulupa
  Open link permit dialog
  Select from autocomplete by test id  link-permit-select  ${ss_app}, ${linkPermitAppId}
  Click enabled by test id  button-link-permit-dialog-add

Tyolupa cant be submitted because linked agreement is not post verdict state
  Open tab  requiredFieldSummary
  Wait test id visible  submit-error-0
  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']
  ${attrValue}=  Get Element Attribute  xpath=(//div[@data-test-id='submit-errors-container']//span)  data-submit-error
  Should Be Equal As Strings   error.link-permit-app-not-in-post-verdict-state  ${attrValue}

Sonja moves linked sijoitussopimus post verdict state
  Go to page  applications
  Open application  ${ss_app}  753-416-45-3
  Give legacy contract  123567890  Decision maker  01.05.2018
  Wait until  Application state should be  agreementPrepared

Tyolupa still cant be submitted because linked agreement is not signed
  Go to page  applications
  Open application  ${digging_app}  753-416-45-3
  Open tab  requiredFieldSummary
  Wait test id visible  submit-error-0
  Element should be disabled  xpath=//*[@data-test-id='application-submit-btn']
  ${attrValue}=  Get Element Attribute  xpath=(//div[@data-test-id='submit-errors-container']//span)  data-submit-error
  Should Be Equal As Strings   error.link-permit-app-not-signed  ${attrValue}

Sonja signs sijoitussopimus agreement
  Go to page  applications
  Open application  ${ss_app}  753-416-45-3
  Open tab  verdict
  Sign verdict  sonja
  Wait until  Application state should be  agreementSigned

Tyolupa should be submittable now
  Go to page  applications
  Open application  ${digging_app}  753-416-45-3
  Open tab  requiredFieldSummary
  Wait test id hidden  submit-errors-container
  Wait until  Test id enabled  application-submit-btn
  Submit application
  Wait until  Application state should be  submitted


*** Keywords ***

Open link permit dialog
  Click enabled by test id  application-add-link-permit-btn
  Wait until  Element should be visible by test id  add-link-permit-card
  Wait test id visible  link-permit-select
