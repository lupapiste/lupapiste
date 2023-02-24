*** Settings ***

Documentation  Stamping view resources
Resource       ../../common_resource.robot

*** Keywords ***

Attachment should be stamped
  [Arguments]  ${type}
  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='${type}'] i[data-test-icon=stamped-icon]

Attachment should not be stamped
  [Arguments]  ${type}
  Element should not be visible  jquery=div#application-attachments-tab tr[data-test-type='${type}'] i[data-test-icon=stamped-icon]

Open stamping page
  [Arguments]  ${appname}
  Wait test id visible  stamp-attachments
  Scroll and click test id  stamp-attachments
  Wait Until  Element should be visible  stamping-container
  Wait Until  Title Should Be  ${appname} - Lupapiste

Click group selection link
  [Arguments]  ${group}  ${mode}
  ${selector}=  Set variable  div#stamping-component-container [data-test-type='${group}'] a[data-test-type='${mode}-all-group']:visible
  Scroll and click link  ${selector}

Mark attachment as verdict attachment
  Open attachment details  muut.muu
  Wait until  Element should be visible  xpath=//label[@data-test-id='is-verdict-attachment-label']
  Toggle toggle  is-verdict-attachment
  Positive indicator icon should be visible
  Return to application

One attachment is selected
  Javascript?  $("tr.selected").length === 1

Number of selected attachments should be
  [Arguments]  ${n}
  Javascript?  $("tr.selected").length === ${n}

Default stamping info fields status
  Wait until  Element should be visible  stamp-info
  Wait until  Element should be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-date"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-date"]
  Wait until   Element should be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-extratext"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-applicationId"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-verdict-date"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]
  Wait until  Element should be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-kuntalupatunnus"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-kuntalupatunnus"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-section"]
  Element should be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-xmargin"]
  Element should be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-ymargin"]
  Element should be visible  xpath=${stamp-info-fields-path}//select[@data-test-id="stamp-info-transparency"]

KV stamp fields status
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-extratext"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-applicationId"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-user"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-date"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-organization"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-verdict-date"]
  Element should be enabled  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-kuntalupatunnus"]
  Element should not be visible  xpath=${stamp-info-fields-path}//input[@data-test-id="stamp-info-section"]

Input stamping info values
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  stamp-info-date  ${STAMP_DATE}
  Input text by test id  stamp-info-organization  ${STAMP_ORGANIZATION}
  Input text by test id  stamp-info-xmargin  ${STAMP_XMARGIN}
  Input text by test id  stamp-info-ymargin  ${STAMP_YMARGIN}
