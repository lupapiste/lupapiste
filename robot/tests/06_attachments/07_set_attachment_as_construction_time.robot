*** Settings ***

Documentation  Setting pre verdict attachment as contruction time attachment
Suite Setup    Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py


*** Test Cases ***

Mikko creates application and goes to attachments tab
  [Tags]  attachments
  Set Suite Variable  ${ct-checkbox-tid}  attachment-is-manually-set-construction-time
  Set Suite Variable  ${construction-time-checkbox}  jquery=attachment-details input[data-test-id=${ct-checkbox-tid}-input]
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

Sonja goes to attachments tab and sees attachment is visible as post verdict filter is on by default
  [Tags]  attachments
  Return to application
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter
  Checkbox wrapper selected by test id  preVerdict-filter
  Element should be visible  jquery=tr[data-test-type='muut.muu']

Sonja sets post verdict filter off
  [Tags]  attachments
  Toggle toggle  postVerdict-filter
  Toggle not selected  postVerdict-filter

Attachment is not visible
  [Tags]  attachments
  Wait until  Element should not be visible  jquery=tr[data-test-type='muut.muu']

Sonja opens the attachment and unsets construction time checkbox
  [Tags]  attachments
  Click by test id  postVerdict-filter-label
  Checkbox wrapper selected by test id  postVerdict-filter
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
  Toggle not selected  ${ct-checkbox-tid}
  Select construction time checkbox

Sonja logs out
  [Tags]  attachments
  Logout

Mikko logs in and goes to attachments table
  [Tags]  attachments
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments

Pre and post verdict filters are set
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter
  Checkbox wrapper selected by test id  preVerdict-filter
  Checkbox wrapper not selected by test id  parties-filter
  Checkbox wrapper not selected by test id  paapiirustus-filter
  Checkbox wrapper not selected by test id  other-filter

Mikko sees construction time checkbox is checked and disabled
  [Tags]  attachments
  Open attachment details  muut.muu
  Wait until  Page should contain element  ${construction-time-checkbox}
  Element should be disabled  ${construction-time-checkbox}
  Checkbox wrapper selected by test id  ${ct-checkbox-tid}

Mikko goes back to attachments listing and sees filters are set as they were
  Return to application
  Wait until  Checkbox wrapper selected by test id  postVerdict-filter
  Checkbox wrapper selected by test id  preVerdict-filter
  Checkbox wrapper not selected by test id  parties-filter
  Checkbox wrapper not selected by test id  paapiirustus-filter
  Checkbox wrapper not selected by test id  other-filter

Mikko toggles all filters on
  Wait until  Scroll and click test id  toggle-all-filters-label
  Wait until  Toggle all filters is selected
  Checkbox wrapper selected by test id  postVerdict-filter
  Checkbox wrapper selected by test id  preVerdict-filter
  Checkbox wrapper selected by test id  parties-filter
  Checkbox wrapper selected by test id  paapiirustus-filter
  Checkbox wrapper selected by test id  other-filter

Mikko toggles all filters off
  Scroll and click test id  toggle-all-filters-label
  Wait until  Toggle all filters is not selected
  Checkbox wrapper not selected by test id  postVerdict-filter
  Checkbox wrapper not selected by test id  preVerdict-filter
  Checkbox wrapper not selected by test id  parties-filter
  Checkbox wrapper not selected by test id  paapiirustus-filter
  Checkbox wrapper not selected by test id  other-filter


Mikko logs out
  [Tags]  attachments
  Logout

*** Keywords ***

Select construction time checkbox
  Toggle toggle  ${ct-checkbox-tid}
  Toggle selected  ${ct-checkbox-tid}
  Positive indicator icon should be visible

Unselect construction time checkbox
  Toggle toggle  ${ct-checkbox-tid}
  Toggle not selected  ${ct-checkbox-tid}
  Positive indicator icon should be visible

Toggle all filters is selected
  Element should be visible  jquery=i.lupicon-checkbox-on[data-test-id=toggle-all-filters-checkbox]

Toggle all filters is not selected
  Element should be visible  jquery=i.lupicon-checkbox-off[data-test-id=toggle-all-filters-checkbox]
