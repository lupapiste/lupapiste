*** Settings ***

Documentation   Notice resource that is shared with multiple suites
Resource        ../../common_resource.robot

*** Keywords ***

Notice icon is
  [Arguments]  ${icon}
  Wait until  Element should be visible  jquery=button[data-test-id=side-panel-notice] i.lupicon-${icon}

Check status
  [Arguments]  ${urgency}  ${new}=false
  Run Keyword If  '${urgency}' == "normal"  Notice icon is  document-list
  Run Keyword If  '${urgency}' == "urgent"  Notice icon is  warning
  Run Keyword If  '${urgency}' == "pending"  Notice icon is  circle-dash
  Wait until  Javascript?  Boolean( $("button[data-test-id=side-panel-notice].positive").length) === ${new}

Check notice
  [Arguments]  ${tag}  ${urgency}  ${note}
  Wait Until  Element text should be  jquery=ul.tags li button span  ${tag}
  Wait Until  List Selection Should Be  application-authority-urgency  ${urgency}
  Textarea value should be  application-authority-notice  ${note}

Wait save
  Positive indicator icon should be visible
  Positive indicator icon should not be visible

Edit notice
  [Arguments]  ${tag}  ${urgency}  ${note}
  Open side panel  notice
  Wait until element is visible  jquery:div#notice-panel div.autocomplete-selection-wrapper
  Select From Autocomplete  div#notice-panel  ${tag}
  #Wait save
  Select From List by id and value  application-authority-urgency  ${urgency}
  #Wait save
  Fill test id  application-authority-notice  ${note}
  #Wait save
  Check notice  ${tag}  ${urgency}  ${note}
  Close side panel  notice
