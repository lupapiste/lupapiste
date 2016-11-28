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
  Reject with note  kaytto  rakennuksen-muuttaminen-kaytto  Bad usage
  Click approve  kaytto
  Reject note is  rakennuksen-muuttaminen-kaytto  Bad usage
  Reject with note but cancel  kaytto  rakennuksen-muuttaminen-kaytto  foobar

Sonja rejects attachment
  Open tab  attachments
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

Sonja adds new version
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait until  No such test id  details-reject-note

Sonja checks versions
  Click button  show-attachment-versions
  No such test id  0-1-1-note
  Reject note is  1-1-0  Worse attachment

Sonja rejects new version with note
  Reject attachment with note  test-attachment-reject  details-reject  Bad version
  Reject note is  0-1-1  Bad version

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
  Reject note is  muut-muu  Bad version

Pena opens attachment details
  Open attachment details  muut.muu
  Reject note is  details-reject  Bad version

Pena does not see version notes
  Click button  show-attachment-versions
  No such test id  0-1-1-note
  No such test id  1-1-0-note
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
  [Teardown]  Logout

# --------------------------
# Pena one more time
# --------------------------

Pena logs in and does not see note for the approved address
  Pena logs in
  Open application  ${appname}  ${propertyId}
  No such test id  rakennuksen-muuttaminen-osoite-note
  [Teardown]  Logout


*** Keywords ***

Press Key Test Id
  [Arguments]  ${id}  ${key}
  Press Key  jquery=input[data-test-id=${id}]  ${key}

Reject note is
  [Arguments]  ${prefix}  ${text}
  Scroll to test id  ${prefix}-note
  Wait test id visible  ${prefix}-note
  Test id text is  ${prefix}-note  ${text}
  
Reject and fill note  
  [Arguments]  ${button}  ${prefix}  ${text}  ${doc-style}=True
  Run Keyword if  ${doc-style}  Click reject  ${button}
  Run Keyword unless  ${doc-style}  Click button  ${button}
  Wait test id visible  ${prefix}-editor
  Input text by test id  ${prefix}-editor  ${text}  True

Reject with note
  [Arguments]  ${button}  ${prefix}  ${text}
  Reject and fill note  ${button}  ${prefix}  ${text}
  Press Key test id  ${prefix}-editor  \\13
  Reject note is  ${prefix}  ${text}

Reject with note but cancel
  [Arguments]  ${button}  ${prefix}  ${text}  ${doc-style}=True
  ${old}=  Execute Javascript  return $("[data-test-id=${prefix}-note]").text()
  Reject and fill note  ${button}  ${prefix}  ${text}  ${doc-style}
  Press Key test id  ${prefix}-editor  \\27  
  No such test id  ${prefix}-editor
  Reject note is  ${prefix}  ${old}

Reject attachment with note
  [Arguments]  ${selector}  ${prefix}  ${text}
  Reject and fill note  ${selector}  ${prefix}  ${text}  False
  Press Key test id  ${prefix}-editor  \\13
  Reject note is  ${prefix}  ${text}
  
  
     
