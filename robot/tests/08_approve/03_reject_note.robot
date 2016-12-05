*** Settings ***

Documentation  Reject note functionality for documents and attachment versions.
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       ../common_keywords/approve_helpers.robot
Variables      ../06_attachments/variables.py

*** Variables ***

${appname}     Reject Note
${propertyId}  753-416-25-30

*** Test Cases ***

# --------------------------
# Pena
# --------------------------

Pena logs in and creates application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  sisatila-muutos

Pena adds attachments
  Open tab  attachments
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}
  Return to application
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  paatoksenteko.muistio
  Return to application

Pena submits application
  Submit application
  [Teardown]  Logout

# --------------------------
# Sonja
# --------------------------

Sonja logs in
  Sonja logs in  
  Open application  ${appname}  ${propertyId} 
  Open accordions  info

Sonja rejects Hankkeen kuvaus with note
  Reject with note  hankkeen-kuvaus-rakennuslupa  hankkeen-kuvaus-rakennuslupa  Bad description

Sonja rejects Rakennuspaikka with note
  Reject with note  rakennuspaikka  rakennuspaikka  Bad place

Sonja approves Rakennuspaikka 
  Click approve  rakennuspaikka
  Reject note is  rakennuspaikka  Bad place

Sonja again rejects Rakennuspaikka but cancels note writing
  Reject with note but cancel  rakennuspaikka  rakennuspaikka  Garble gorble

Sonja approves Rakennuspaikka again
  Click approve  rakennuspaikka
  Reject note is  rakennuspaikka  Bad place

Sonja rejects primary operation with note
  Reject with note  rakennuksen-muuttaminen  rakennuksen-muuttaminen  Bad operation

Sonja rejects kaytto with note
  Reject with note and save  kaytto  rakennuksen-muuttaminen-kaytto  Bad usage
  Click approve  kaytto
  Reject note is  rakennuksen-muuttaminen-kaytto  Bad usage
  Reject with note but cancel  kaytto  rakennuksen-muuttaminen-kaytto  foobar

Sonja rejects attachment
  Open tab  attachments
  Tab indicator is  2
  Reject attachment with note  jquery=tr[data-test-state=requires_authority_action] button.reject  muut-muu  Bad attachment

Clicking reject button again is supported
  Reject attachment with note  jquery=tr[data-test-state=requires_user_action] button.negative  muut-muu  Worse attachment

Sonja rejects again but cancels
  Reject with note but cancel  jquery=tr[data-test-state=requires_user_action] button.negative  muut-muu  All righty then  False

Sonja opens attachment details
  Open attachment details  muut.muu
  Reject note is  details-reject  Worse attachment

Sonja approves attachment
  Click button  test-attachment-approve

Sonja rejects but cancels attachment
  Reject with note but cancel  test-attachment-reject  details-reject  Dum dum  False

Sonja approves attachment again
  Click button  test-attachment-approve

Sonja adds two new versions
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait until  No such test id  details-reject-note
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait until  No such test id  details-reject-note

Sonja checks versions
  Click button  show-attachment-versions
  No such test id  0-1-2-note
  No such test id  1-1-1-note
  Reject note is  2-1-0  Worse attachment

Sonja rejects new version with note
  Reject attachment with note  test-attachment-reject  details-reject  Bad version
  Reject note is  0-1-2  Bad version

There is now one approved, one rejected and one neutral version
  Wait test id visible  0-1-2-rejected
  Neutral version  1-1-1-neutral
  Wait test id visible  2-1-0-approved

Sonja could delete every version
  Can delete version  1.2
  Can delete version  1.1
  Can delete version  1.0
  
Sonja returns the attachments tab and opens the other attachment
  Return to application
  Reject note is  muut-muu  Bad version
  Open attachment details  paatoksenteko.muistio
  Reject attachment with note  test-attachment-reject  details-reject  Bad memorandum
  Click button  test-attachment-approve
  Wait until  Element should be enabled  test-attachment-reject
  Reject note is  details-reject  Bad memorandum
  Return to application
  No such test id  paatoksenteko-muistio-note  
  [Teardown]  Logout

# --------------------------
# Pena again
# --------------------------

Pena logs in
  Pena logs in
  Open application  ${appname}  ${propertyId}

Pena sees notes for the rejected documents and groups
  Reject note is  hankkeen-kuvaus-rakennuslupa  Bad description
  Reject note is  rakennuksen-muuttaminen  Bad operation
  Reject note is  rakennuksen-muuttaminen-kaytto  Bad usage

Pena does not see notes for the approved
  No such test id  rakennuspaikka-note

Pena goes to attachments tab
  Open tab  attachments
  Tab indicator is  1
  Reject note is  muut-muu  Bad version

Pena opens attachment details
  Open attachment details  muut.muu
  Attachment rejected  Bad version  
  #Reject note is  details-reject  Bad version

Pena sees only the rejected version notes
  Click button  show-attachment-versions
  Reject note is  0-1-2  Bad version
  No such test id  1-1-1-note
  No such test id  1-1-0-note

Pena checks versions' approval states
  Wait test id visible  0-1-2-rejected
  Neutral version  1-1-1-neutral
  Wait test id visible  2-1-0-approved

Pena could delete only the neutral version
  Cannot delete version  1.2
  Can delete version  1.1
  Cannot delete version  1.0
  Return to application

Pena does not see approved attachment's note
  No such test id  paatoksenteko-muistio-note
  Open attachment details  paatoksenteko.muistio
  No such test id  reject-attachment-note 
  [Teardown]  Logout

# --------------------------
# Sonja again
# --------------------------

Sonja logs in again
  Sonja logs in  
  Open application  ${appname}  ${propertyId} 
  Open accordions  info

Sonja rejects with note and approves osoite
  Click approve  osoite
  Reject with note  osoite  rakennuksen-muuttaminen-osoite  Bad address
  Click approve  osoite
  Reject note is  rakennuksen-muuttaminen-osoite  Bad address

Attachment state is changed when the latest version is deleted
  Open tab  attachments
  No tab indicator
  Open attachment details  muut.muu
  Click button  show-attachment-versions

... from rejected to neutral
  Attachment rejected  Bad version
  Delete version  1.2
  Attachment neutral
  Neutral version  0-1-1-neutral
  Wait test id visible  1-1-0-approved

... from neutral to approved
  Delete version  1.1
  Attachment approved
  Wait test id visible  0-1-0-approved
  [Teardown]  Logout

# --------------------------
# Pena one more time
# --------------------------

Pena logs in and does not see note for the approved address
  Pena logs in
  Open application  ${appname}  ${propertyId}
  No tab indicator
  No such test id  rakennuksen-muuttaminen-osoite-note
  [Teardown]  Logout

# --------------------------
# Sonja invites neighbor
# --------------------------

Sonja logs in and invites neighbor
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  statement
  Scroll and click test id  manage-neighbors
  Add neighbor  753-416-22-22  Penade Linju  linju@example.com
  Scroll and click test id  manager-neighbors-done
  Wait test id visible  manage-neighbors
  Scroll and click test id  neighbor-row-invite
  Wait test id visible  neighbors-sendemail-send
  Scroll and click test id  neighbors-sendemail-send
  Wait until  Element should not be visible  dialog-send-neighbor-email
  [Teardown]  Logout  

Neighbor reads email
  Open last email
  # Last email page does not include jQuery
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  linju@example.com
  Click element  xpath=//a
  Neighbor application address should be  ${appname}

Approval/rejection states not visible to neighbor
  Page should not contain  jquery=i.lupicon-circle-check
  Page should not contain  jquery=i.lupicon-circle-attention


*** Keywords ***

Can delete version
  [Arguments]  ${version}
  Wait Until  Element should be visible  jquery=tr[data-test-id='version-row-${version}'] td a[data-test-id=delete-version]

Cannot delete version
  [Arguments]  ${version}
  Wait Until  Element should not be visible  jquery=tr[data-test-id='version-row-${version}'] td a[data-test-id=delete-version]

# Visible selector does not match 0x0 element
Neutral version
  [Arguments]  ${testid}
  jQuery should match X times  i[data-test-id=${testid}]  1

Delete version
  [Arguments]  ${version}
  Click Element  jquery=tr[data-test-id='version-row-${version}'] td a[data-test-id=delete-version]
  Confirm yes no dialog
  Cannot delete version  ${version}

Attachment approved
  Wait Until  Element should be disabled  test-attachment-approve
  Wait Until  Element should be enabled   test-attachment-reject
  Wait until  Element should be visible  jquery=span.form-approval-status.approved
  
Attachment rejected
  [Arguments]  ${note}
  Wait Until  Element should be disabled  test-attachment-reject
  Wait Until  Element should be enabled   test-attachment-approve
  Wait until  Element should be visible  jquery=span.form-approval-status.rejected
  Reject note is  details-reject  ${note}

Attachment neutral
  Wait Until  Element should be enabled  test-attachment-approve
  Wait Until  Element should be enabled  test-attachment-reject

Tab indicator is
  [Arguments]  ${n}
  Wait until  Element text should be  applicationAttachmentsRequiringAction  ${n}

No tab indicator
  Wait Until  Element should not be visible  applicationAttachmentsRequiringAction
