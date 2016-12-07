*** Settings ***

Documentation   Building selection
Suite setup     Apply minimal fixture now  
Resource        ../../common_resource.robot


*** Variables ***

${doctype1}   rakennuksen-muuttaminen
${building1}  122334455R
${company1}   Testiyritys 11477
${doctype2}   rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia
${building2}  199887766E
${company2}   Testiyritys 9242
   

*** Test Cases ***

Pena logs in and creates application
  Pena logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  BUILDINGS${secs}
  Create application the fast way  ${appname}  753-416-25-30  sisatila-muutos

Pena chooses building 1
  Select building  ${doctype1}  ${building1}  True
  No other input  ${doctype1}
  Company name is  ${doctype1}  ${company1}

Pena edits company name
  Set company name  ${doctype1}  New company name
  
Pena chooses building 2, no merge
  Select building  ${doctype1}  ${building2}  False
  No other input  ${doctype1}
  Company name is  ${doctype1}  New company name

Pena chooses empty selection, no dialog
  Clear building selection  ${doctype1}

Pena chooses Other building
  Select other building  ${doctype1}
  Other input is  ${doctype1}  ${EMPTY}

Pena inputs bad and good building numbers
  Set building number  ${doctype1}  foobar
  Bad building number  ${doctype1}
  Set building number  ${doctype1}  123  
  Good building number  ${doctype1}
  
Pena adds operation
  Set animations off
  Scroll and click test id  add-operation
  Select operation branch  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Select operation branch  "Rakennuksen korjaaminen ja/tai muuttaminen (käyttötarkoitus, julkisivut, remontit yms.)"
  Select operation branch  "Rakennuksen julkisivujen tai katon muutos, mainoslaitteet tai opasteet rakennuksissa"
  Scroll and click test id  add-operation-to-application
  Wait until  Element should be visible  jquery=section[data-doc-type=${doctype2}]
  
Initially new building selector and company name are empty
  No building selected  ${doctype2}
  No other input  ${doctype2}
  Scroll and click  section[data-doc-type=${doctype2}] input[value=yritys]
  Company name is  ${doctype2}  ${EMPTY}

Pena chooses building 2 for the secondary operation
  Select building  ${doctype2}  ${building2}  True
  No other input  ${doctype2}
  Company name is  ${doctype2}  ${company2}

Primary operation details are unchanged
  Other input is  ${doctype1}  123
  Good building number  ${doctype1}
  Company name is  ${doctype1}  New company name
  [Teardown]  Logout 


** Keywords ***

No building selected
  [Arguments]  ${doctype}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] select[name=buildingId]
  Wait until  Javascript?  $("${selector}").val() === ""

Clear building selection
  [Arguments]  ${doctype}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] select[name=buildingId]
  Scroll to  ${selector}
  Wait until  Select from list  jquery=${selector}  ${EMPTY}
  No building selected  ${doctype}
  Element should not be visible  dynamic-yes-no-confirm-dialog
  
Select other building
  [Arguments]  ${doctype}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] select[name=buildingId]
  Scroll to  ${selector}
  Wait until  Select from list  jquery=${selector}  other

Select building
  [Arguments]  ${doctype}  ${building}  ${merge}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] select[name=buildingId]
  Scroll to  ${selector}
  Execute Javascript  $("${selector}").addClass( "test-reload")
  Wait until  Select from list by value  jquery=${selector}  ${building}
  Run keyword if  ${merge}  Confirm  dynamic-yes-no-confirm-dialog
  Run keyword unless  ${merge}  Deny  dynamic-yes-no-confirm-dialog
  # After reload, our manually added class is removed.
  Wait until  Element should not be visible  jquery=select.test-reload

No other input
  [Arguments]  ${doctype}
  Wait until  Element should not be visible  jquery=section[data-doc-type=${doctype}] div.building-select-box input[data-test-id=manuaalinen_rakennusnro]

Other input is
  [Arguments]  ${doctype}  ${value}
  Wait until  Textfield value should be  jquery=section[data-doc-type=${doctype}] div.building-select-box input[data-test-id=manuaalinen_rakennusnro]  ${value}

Set building number
  [Arguments]  ${doctype}  ${value}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] div.building-select-box input[data-test-id=manuaalinen_rakennusnro]
  Scroll to  ${selector}
  Input text with jquery  ${selector}  ${value}
  Wait for jQuery

Bad building number
  [Arguments]  ${doctype}
  Wait until  Element should be visible  jquery=section[data-doc-type=${doctype}] div.building-select-box input.warn[data-test-id=manuaalinen_rakennusnro] 

Good building number
  [Arguments]  ${doctype}
  Wait until  Element should not be visible  jquery=section[data-doc-type=${doctype}] div.building-select-box input.warn[data-test-id=manuaalinen_rakennusnro] 

Company name is
  [Arguments]  ${doctype}  ${name}  
  ${selector}=  Set Variable  jquery=section[data-doc-type=${doctype}] input[data-docgen-path="rakennuksenOmistajat.0.yritys.yritysnimi"]
  Wait until  Element should be visible  ${selector}
  Textfield value should be  ${selector}  ${name}  

Set company name
  [Arguments]  ${doctype}  ${name}
  ${selector}=  Set Variable  section[data-doc-type=${doctype}] input[data-docgen-path="rakennuksenOmistajat.0.yritys.yritysnimi"]   
  Input text with jQuery  ${selector}  ${name}

Select operation branch
  [Arguments]  ${text}
  Wait Until  Click element  //div[@class="tree-content"]//*[text()=${text}]
