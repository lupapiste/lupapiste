*** Settings ***

Documentation   Info panel
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Pena logs in and creates application
  Set Suite Variable  ${appname}  Linklater
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

Pena sees star and opens info panel
  Star is shown
  Open info panel

Pena sees two organization links no info links
  Check organization link  0  Sipoo  http://sipoo.fi  true
  Check organization link  1  Rakennusvalvonta  http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta  true
  No such test id  organization-link-2

Pena toggles side panel and stars are now gone
  Close info panel
  Star is not shown
  Open info panel
  Check organization link  0  Sipoo  http://sipoo.fi
  Check organization link  1  Rakennusvalvonta  http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta

Pena cannot add links
  No such test id  add-info-link

Pena submits application
  Submit application
  [Teardown]  Logout  

Sonja logs in and also sees new organization links
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  http://sipoo.fi  true
  Check organization link  1  Rakennusvalvonta  http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta  true
  No such test id  organization-link-2

Sonja adds info link
  Add info link  0  Example  www.example.org
  Save link  0
  Check view link  0  Example  http://www.example.org  false  true
  
Sonja start editing link but cancels
  Edit info link  0  Example  http://www.example.org
  Fill link  0  Foo  bar.baz
  Cancel link  0
  Check view link  0  Example  http://www.example.org  false  true

Sonja edits and saves
  Edit info link  0  Example  http://www.example.org
  Fill link  0  Foo  https://bar.baz
  Save link  0
  Check view link  0  Foo  https://bar.baz  false  true

Sonja adds link but cancels
  Add info link  1  Hii  hii.hoo
  Cancel link  1
  No such test id  view-link-edit-1

*** Keywords ***

Star is shown
  Wait Until  Element should be visible  jquery=button#open-info-side-panel.positive i.lupicon-circle-star 

Star is not shown
  Wait Until  Element should be visible  jquery=button#open-info-side-panel.primary i.lupicon-circle-info 

Open info panel
  Click element  open-info-side-panel
  Wait Until  Star is not shown

Close info panel
  Click element  open-info-side-panel
  

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

