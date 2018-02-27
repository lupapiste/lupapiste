*** Settings ***

Documentation  Signed icon title
Suite setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py

*** Variables ***

${appname}     Signature
${propertyId}  753-416-6-1

*** Test Cases ***

# ------------------
# Mikko
# ------------------

Mikko logs in and creates application
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  pientalo

Mikko uploads attachment
  Open tab  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Other  ${EMPTY}

Mikko signs attachment
  Sign all attachments  mikko123

Signed icon hover contains Mikko Intonen
  Signed icon bubble hover contains  Mikko Intonen

Mikko invites Pena to the application
  Invite pena@example.com to application
  [Teardown]  Logout

# ------------------
# Pena
# ------------------

Pena logs in and accepts invitation
  Pena logs in
  Wait test id visible  accept-invite-button
  Click enabled by test id  accept-invite-button
  Open application  ${appname}  ${propertyId}

Pena checks the signed icon title
  Open tab  attachments
  Signed icon bubble hover contains  Mikko Intonen

Pena signs the attachment
  Sign all attachments  pena

Pena is now also on the icon title
  Signed icon bubble hover contains  Mikko Intonen
  Signed icon bubble hover contains  Pena Panaani

Pena adds new attachment version
  Open attachment details  muut.muu
  Add attachment version  ${TXT_TESTFILE_PATH}
  Wait until  Click by test id  back-to-application-from-attachment

There is no more signed icon
  Wait until  Attachment indicator icon should be visible  state  muut.muu
  Attachment indicator icon should not be visible  signed  muut.muu
  [Teardown]  Logout


*** Keywords ***

Signed icon bubble hover contains
  [Arguments]  ${name}
  Mouse over  xpath=//*[@data-test-icon='signed-icon']
  Element should be visible  xpath=//*[@data-test-id='attachment-state-icons-hover-signed']
  Element should contain  xpath=//*[@data-test-id='attachment-state-icons-hover-signed']  ${name}
