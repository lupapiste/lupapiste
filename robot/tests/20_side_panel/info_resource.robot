*** Settings ***

Documentation  Info panel resources
Resource       ../../common_resource.robot

*** Keywords ***

Star is shown
  Wait Until  Element should be visible  jquery=button#open-info-side-panel.positive i.lupicon-circle-star 

Star is not shown
  Wait Until  Element should be visible  jquery=button#open-info-side-panel.primary i.lupicon-circle-info 

No info panel stars
  Wait Until  Javascript?  $("div.info-links-content i.lupicon-circle-star:visible").length === 0

Open info panel
  Click element  open-info-side-panel
  Wait Until  Star is not shown

Close info panel
  Click element  open-info-side-panel

Clear stars and close panel
  Click element  open-conversation-side-panel
  Wait until  Element should be visible  conversation-panel
  Click element  open-info-side-panel
  No info panel stars
  Close info panel

Check organization link
  [Arguments]  ${index}  ${text}  ${url}  ${new}=false
  Wait test id visible  organization-link-${index}
  ${a}=  Set Variable  a[data-test-id=organization-link-${index}]
  Javascript?  $("${a} span").text() === "${text}"
  Javascript?  $("${a}").attr("href") === "${url}"
  Javascript?  Boolean($("${a} i.lupicon-circle-star:visible").length) === ${new}

Check view link
  [Arguments]  ${index}  ${text}  ${url}  ${new}  ${canEdit}
  Wait test id visible  view-link-${index}
  ${a}=  Set Variable  td[data-test-id=view-link-${index}] a
  Javascript?  $("${a} span").text() === "${text}"
  Javascript?  $("${a}").attr("href") === "${url}"
  Javascript?  Boolean($("${a} i.lupicon-circle-star:visible").length) === ${new}
  Javascript?  Boolean($("a[data-test-id=view-link-edit-${index}]").length) === ${canEdit}
  Javascript?  Boolean($("a[data-test-id=view-link-remove-${index}]").length) === ${canEdit}

Check just link
  [Arguments]  ${index}  ${text}  ${url}  ${new}=false
  Wait test id visible  just-link-${index}
  ${a}=  Set Variable  a[data-test-id=just-link-${index}]
  Javascript?  $("${a} span").text() === "${text}"
  Javascript?  $("${a}").attr("href") === "${url}"
  Javascript?  Boolean($("${a} i.lupicon-circle-star:visible").length) === ${new}
  
Edit info link
  [Arguments]  ${index}  ${text}  ${url}
  Scroll and click test id  view-link-edit-${index}
  Wait until  Test id input is  edit-link-text-${index}  ${text}
  Test id input is  edit-link-url-${index}  ${url}

Fill link
  [Arguments]  ${index}  ${text}  ${url}
  Fill test id  edit-link-text-${index}  ${text}
  Fill test id  edit-link-url-${index}  ${url}

Save link
  [Arguments]  ${index}
  Click enabled by test id  edit-link-save-${index}

Cancel link
  [Arguments]  ${index}
  Click enabled by test id  edit-link-cancel-${index}

Add info link
  [Arguments]  ${index}  ${text}  ${url}
  Click enabled by test id  add-info-link
  Test id empty  edit-link-text-${index}
  Test id empty  edit-link-url-${index}
  Fill link  ${index}  ${text}  ${url}

Delete info link
  [Arguments]  ${index}
  Scroll and click test id  view-link-remove-${index}

Cannot edit links
  Wait until  element should not be visible  jquery=table.info-link-editor-view i.lupicon-pen
  Wait until  element should not be visible  jquery=table.info-link-editor-view i.lupicon-remove

Cannot edit anything
  Cannot edit links
  No such test id  add-info-link
