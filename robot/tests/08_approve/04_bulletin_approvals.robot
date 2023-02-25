*** Settings ***

Documentation  Approvals not visible on bulletins.
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       ../../common_keywords/approve_helpers.robot
Resource       ../27_julkipano/julkipano_common.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

# --------------------------
# Olli
# --------------------------

Olli logs in and creates application
  Select window  main
  Olli logs in  False
  Create application with state  Silver bulletin  564-404-99-99  meluilmoitus  submitted

Olli approves operation
  Click approve  meluilmoitus

Olli rejects period with note
  Reject with note  ymp-ilm-kesto  ymp-ilm-kesto  Too noisy, too long!

Olli adds approved attachment
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Muu liite  Muu  Yleisesti hankkeeseen
  Open attachment details  muut.muu
  Wait Until  Element Should Be Visible  test-attachment-approve
  Wait Until  Element Should Be Disabled  test-attachment-approve
  Attachment is  approved
  Return to application

Olli publishes bulletin
  Approve application ok
  Open tab  ymp-bulletin
  Publish bulletin

Olli opens bulletin and checks that not icons are visible
  Wait until  Click link  jquery=td.actions a[target=_blank]
  Select window  new
  Wait until  Element should be visible  ymp-bulletin-component
  No icons  info
  No icons  attachments

No frontend errors
  There are no frontend errors

*** Keywords ***

No icons
  [Arguments]  ${tab}
  Wait test id visible  bulletin-open-${tab}-tab
  Scroll and click test id  bulletin-open-${tab}-tab
  Page should not contain  jquery=i.lupicon-circle-check
  Page should not contain  jquery=i.lupicon-circle-attention
