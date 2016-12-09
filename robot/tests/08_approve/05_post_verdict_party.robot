*** Settings ***

Documentation  Post-verdict party approvals.
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       ../common_keywords/approve_helpers.robot
  
*** Test Cases ***

Sonja logs in and creates application
  Sonja logs in
  Create application with state  Post verdict party  753-416-25-30  pientalo  verdictGiven

Sonja adds removes old designer and adds new one
  Open tab  parties
  Scroll and click test id  toggle-document-status
  Confirm yes no dialog
  Scroll and click test id  suunnittelija_append_btn

Sonja rejects designer with empty note
  Reject with note  suunnittelija  suunnittelija  ${EMPTY}

Sonja approves designer
  Click approve  suunnittelija

Sonja rejects designer with note
  Reject with note  suunnittelija  suunnittelija  Bad designer!

Sonja approves designer again
  Click approve  suunnittelija
  Reject note is  suunnittelija  Bad designer!
  [Teardown]  Logout

No frontend errors
  There are no frontend errors

