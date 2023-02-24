*** Settings ***

Documentation  Send attachments to backing system.
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Resource       ../39_pate/pate_resource.robot
Variables      variables.py

*** Test Cases ***

# ------------------------------
# Applicant
# ------------------------------

Pena creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Fetching!
  Set Suite Variable  ${propertyId}  753-416-6-1
  Pena logs in
  Create application with state  ${appname}  ${propertyId}  kerrostalo-rivitalo  submitted
  ${app-id}=  Get Element Attribute  jquery=span[data-test-id=application-id]  data-test-value
  Set Suite Variable  ${app-id}
  [Teardown]  Logout

# ------------------------------
# Authority
# ------------------------------

Sonja fetches verdict
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict

Add transfer to application
  Go to  ${SERVER}/dev/add-transfer/${app-id}
  Wait until  Page should contain  Transfer added
  Go to  ${lOGOUT URL}
  [Teardown]  Logout

Add two files
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Upload batch file  0  ${PNG_TESTFILE_PATH}  Valokuva  Regular
  Upload batch file  1  ${TXT_TESTFILE_PATH}  Valtakirja  Fetched
  Scroll and click test id  batch-ready
  No such test id  batch-ready

Sonja checks sendable attachments
  Scroll to top
  Scroll and click test id  export-attachments-to-backing-system
  Listed count is  2
  Contents listed  Regular
  Contents listed  Fetched
  Scroll and click test id  back-to-application-from-multiselect

Mark Fetched as fetched
  Go to  ${SERVER}/dev/fetched-attachment/${app-id}/Fetched
  Wait until  Page should contain  Attachment marked as fetched
  Go to  ${lOGOUT URL}
  [Teardown]  Logout

Fetched is not listed
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Scroll and click test id  export-attachments-to-backing-system
  Listed count is  1
  Contents listed  Regular
  Contents not listed  Fetched

Sonja sends Regular to the backing system
  Scroll and click test id  multiselect-action-button
  Confirm  dynamic-yes-no-confirm-dialog

No sendable attachments
  No such test id  export-attachments-to-backing-system

Add new version to Regular
  Add version  muut.valokuva  ${PNG_TESTFILE_PATH}

Add new version to Fetched
  Wait until element is visible  jquery=div.filter-group__state
  Click element  jquery=div.filter-group__state
  Add version  hakija.valtakirja  ${TXT_TESTFILE_PATH}

Both attachments are again sendable
  Wait until  Scroll and click test id  export-attachments-to-backing-system
  Listed count is  2
  Contents listed  Regular
  Contents listed  Fetched
  Wait until  Scroll and click test id  back-to-application-from-multiselect
  [Teardown]  Logout


*** Keywords  ***

Contents listed
  [Arguments]  ${contents}
  Wait test id visible  back-to-application-from-multiselect
  Wait until element is visible  jquery=span[data-test-id=attachment-contents]:contains(${contents})

Contents not listed
  [Arguments]  ${contents}
  Wait test id visible  back-to-application-from-multiselect
  Wait until element is not visible  jquery=span[data-test-id=attachment-contents]:contains(${contents})

Listed count is
  [Arguments]  ${count}
  jQuery should match X times  span[data-test-id=attachment-contents]:visible  ${count}

Add version
  [Arguments]  ${type}  ${path}
  Open attachment details  ${type}
  Add attachment version  ${path}
  Wait until  Scroll and click test id  back-to-application-from-attachment
