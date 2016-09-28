*** Settings ***

Documentation  Setting pre verdict attachment as contruction time attachment
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py


*** Test Cases ***

Mikko creates application and goes to attachments tab
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments

Mikko adds txt attachment without comment
  [Tags]  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  operation=Asuinkerrostalon tai rivitalon rakentaminen

Mikko does not see checkbox for setting attachment as construction time
  [Tags]  attachments
  Page should not contain element  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]

Mikko opens application to authorities and logs out
  Open to authorities  huhuu
  [Tags]  attachments
  Logout

Sonja goes to attachments tab
  [Tags]  attachments
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Sonja opens attachment and sees set construction time checkbox
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Checkbox should not be selected  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]

Sonja sets attachment as construction time
  [Tags]  attachments
  Select construction time checkbox

Sonja goes to attachments tab and sees attachment is hidden by filter
  [Tags]  attachments
  Return to application
  Wait until  Checkbox wrapper not selected by test id  postVerdict-filter-checkbox
  Checkbox wrapper selected by test id  preVerdict-filter-checkbox
  Element should not be visible  jquery=tr[data-test-type='muut.muu']

Sonja sets post verdict filter on
  [Tags]  attachments
  Click by test id  postVerdict-filter-label
  Checkbox wrapper selected by test id  postVerdict-filter-checkbox

Attachment is visible
  [Tags]  attachments
  Wait until  Element should be visible  jquery=tr[data-test-type='muut.muu']

Sonja opens the attachment and unsets construction time checkbox
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Checkbox should be selected  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Unselect construction time checkbox

Application state filters are hidden
  [Tags]  attachments
  Return to application
  Element should not be visible  jquery=filters label[data-test-type='preVerdict-filter-label']
  Element should not be visible  jquery=filters label[data-test-type='postVerdict-filter-label']

Sonja sets construction time checkbox again
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Checkbox should not be selected  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Select construction time checkbox

Sonja logs out
  [Tags]  attachments
  Logout

Mikko logs in and goes to attachments table
  [Tags]  attachments
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Click by test id  postVerdict-filter-label

Mikko sees construction time checkbox is checked and disabled
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Element should be disabled  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Checkbox should be selected  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]

Mikko logs out
  [Tags]  attachments
  Logout

*** Keywords ***

Select construction time checkbox
  Select checkbox  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Positive indicator icon should be visible

Unselect construction time checkbox
  Unselect checkbox  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  Positive indicator icon should be visible
