*** Settings ***

Documentation  Setting pre verdict attachment as contruction time attachment
Suite Setup    Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py


*** Test Cases ***

Mikko creates application and goes to attachments tab
  [Tags]  attachments
  Set Suite Variable  ${construction-time-checkbox}  jquery=attachment-details input[data-test-id=attachment-is-manually-set-construction-time]
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments

Mikko adds txt attachment without comment
  [Tags]  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Moo  Asuinkerrostalon tai rivitalon rakentaminen
  Open attachment details  muut.muu

Mikko does not see checkbox for setting attachment as construction time
  [Tags]  attachments
  Page should not contain element  ${construction-time-checkbox}

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
  Wait until  Page should contain element  ${construction-time-checkbox}
  Checkbox should not be selected  ${construction-time-checkbox}

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
  Wait until  Checkbox should be selected  ${construction-time-checkbox}
  Unselect construction time checkbox

Application state filters are hidden
  [Tags]  attachments
  Return to application
  Element should not be visible  jquery=filters label[data-test-type='preVerdict-filter-label']
  Element should not be visible  jquery=filters label[data-test-type='postVerdict-filter-label']

Sonja sets construction time checkbox again
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  ${construction-time-checkbox}
  Checkbox should not be selected  ${construction-time-checkbox}
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

Pre and post verdict filters are set
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter-checkbox
  Checkbox wrapper selected by test id  preVerdict-filter-checkbox
  Checkbox wrapper not selected by test id  general-filter-checkbox
  Checkbox wrapper not selected by test id  parties-filter-checkbox
  Checkbox wrapper not selected by test id  paapiirustus-filter-checkbox
  Checkbox wrapper not selected by test id  other-filter-checkbox

Mikko sees construction time checkbox is checked and disabled
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  ${construction-time-checkbox}
  Element should be disabled  ${construction-time-checkbox}
  Checkbox should be selected  ${construction-time-checkbox}

Mikko goes back to attachments listing and sees filters are set as they were
  Click by test id  back-to-application-from-attachment
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter-checkbox
  Checkbox wrapper selected by test id  preVerdict-filter-checkbox
  Checkbox wrapper not selected by test id  general-filter-checkbox
  Checkbox wrapper not selected by test id  parties-filter-checkbox
  Checkbox wrapper not selected by test id  paapiirustus-filter-checkbox
  Checkbox wrapper not selected by test id  other-filter-checkbox

Mikko toggles all filters on
  Click by test id  toggle-all-filters-label
  Wait until  Toggle all filters is selected
  Checkbox wrapper selected by test id  postVerdict-filter-checkbox
  Checkbox wrapper selected by test id  preVerdict-filter-checkbox
  Checkbox wrapper selected by test id  general-filter-checkbox
  Checkbox wrapper selected by test id  parties-filter-checkbox
  Checkbox wrapper selected by test id  paapiirustus-filter-checkbox
  Checkbox wrapper selected by test id  other-filter-checkbox

Mikko toggles all filters off
  Click by test id  toggle-all-filters-label
  Wait until  Toggle all filters is not selected
  Checkbox wrapper not selected by test id  postVerdict-filter-checkbox
  Checkbox wrapper not selected by test id  preVerdict-filter-checkbox
  Checkbox wrapper not selected by test id  general-filter-checkbox
  Checkbox wrapper not selected by test id  parties-filter-checkbox
  Checkbox wrapper not selected by test id  paapiirustus-filter-checkbox
  Checkbox wrapper not selected by test id  other-filter-checkbox


Mikko logs out
  [Tags]  attachments
  Logout

*** Keywords ***

Select construction time checkbox
  Select checkbox  ${construction-time-checkbox}
  Positive indicator icon should be visible

Unselect construction time checkbox
  Unselect checkbox  ${construction-time-checkbox}
  Positive indicator icon should be visible

Toggle all filters is selected
  Element should be visible  jquery=i.lupicon-checkbox-on[data-test-id=toggle-all-filters-checkbox]

Toggle all filters is not selected
  Element should be visible  jquery=i.lupicon-checkbox-off[data-test-id=toggle-all-filters-checkbox]
