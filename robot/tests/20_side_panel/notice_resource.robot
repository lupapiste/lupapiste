*** Settings ***

Documentation   Notice resource that is shared with multiple suites
Resource        ../../common_resource.robot

*** Keywords ***

Check status
  [Arguments]  ${urgency}  ${new}=false
  Run Keyword If  '${urgency}' == "normal"  Wait until  Element should be visible  jquery=button#open-notice-side-panel i.lupicon-document-list
  Run Keyword If  '${urgency}' == "urgent"  Wait until  Element should be visible  jquery=button#open-notice-side-panel i.lupicon-warning
  Run Keyword If  '${urgency}' == "pending"  Wait until  Element should be visible  jquery=button#open-notice-side-panel i.lupicon-circle-dash
  Wait until  Javascript?  Boolean( $("button#open-notice-side-panel.positive").length) === ${new}

Check notice
  [Arguments]  ${tag}  ${urgency}  ${note}
  Wait Until  Element text should be  jquery=li.tag span.tag-label  ${tag}
  Wait Until  List Selection Should Be  application-authority-urgency  ${urgency}
  Textarea value should be  application-authority-notice  ${note}

Wait save
  Positive indicator icon should be visible
  Positive indicator icon should not be visible

Edit notice
  [Arguments]  ${tag}  ${urgency}  ${note}
  Open side panel  notice
  Select From Autocomplete  div#notice-panel  ${tag}
  Wait save
  Select From List by id and value  application-authority-urgency  ${urgency}
  Wait save
  Fill test id  application-authority-notice  ${note}
  Wait save
  Check notice  ${tag}  ${urgency}  ${note}
  Close side panel  notice
