*** Settings ***

Documentation   Resources for Pate robots
Resource       ../../common_resource.robot

*** Keywords ***

Safe kill dev-box
  Execute Javascript  $(".dev-debug").hide();

Phrase categories
  [Arguments]  ${tid}  @{categories}
  Test id select values are  ${tid}-category  @{categories}

Select phrase category
  [Arguments]  ${tid}  ${category}
  Select from test id  ${tid}-category  ${category}

Pate autocomplete select
  [Arguments]  ${tid}  ${term}
  ${sel}=  Set variable  [data-test-id=${tid}]
  Scroll and click test id  ${tid}
  Wait until  Input text  jquery=${sel} .ac__term > input  ${term}
  #Wait until  jQuery should match X times  ${sel} ul.ac__items li  1
  Click element  jquery=${sel} li.ac--current

Select phrase
  [Arguments]  ${tid}  ${term}
  Pate autocomplete select  ${tid}-autocomplete  ${term}

Phrase text is
  [Arguments]  ${tid}  ${text}
  ${contents}=  Get text  jquery=[data-test-id=${tid}-edit]
  Should be equal  ${contents}  ${text}

Type fill test id
  [Arguments]  ${tid}  ${text}
  Fill test id  ${tid}  ${EMPTY}
  Press key test id  ${tid}  ${text}

Row test id
  [Arguments]  ${repeating}  ${index}
  Wait test id visible  ${repeating}-${index}
  ${id}=  Get element attribute  jquery=[data-test-id=${repeating}-${index}]  data-repeating-id
  [Return]  ${repeating}-${id}

Test id for
  [Arguments]  ${repeating}  ${index}  ${dict}
  ${row-id}=  Row test id  ${repeating}  ${index}
  [Return]  ${row-id}-${dict}

Test id warning
  [Arguments]  ${tid}
  Wait until  Element should be visible  jquery=[data-test-id=${tid}].warning:visible

Click back
  Wait test id visible  back
  Scroll and click test id  back

# --------------------------
# Verdicts
# --------------------------

Link button disabled
  [Arguments]  ${test-id}
  Wait until element is visible  jquery=span.disabled[data-test-id=${test-id}]

Link button enabled
  [Arguments]  ${test-id}
  Wait until element is visible  jquery=a[data-test-id=${test-id}]

Go to give new legacy verdict
  Open tab  verdict
  Click enabled by test id  new-legacy-verdict
  Fields required
  Safe kill dev-box
  Link button disabled  preview-verdict
  Test id disabled  publish-verdict
  Click visible test id  toggle-all

Input legacy verdict
  [Arguments]  ${backend-id}  ${giver}  ${term}  ${date}  ${check-buttons}=True
  Input text by test id  kuntalupatunnus  ${backend-id}
  Input text by test id  handler  ${giver}
  Input text by test id  verdict-section  22
  Pate autocomplete select  verdict-code  ${term}
  Input text by test id  verdict-text  Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris ut leo a ipsum sagittis faucibus. Integer ac velit eget odio tincidunt facilisis. Duis eu purus elementum, efficitur eros non, ultrices lectus. Praesent non ipsum id sapien dictum pharetra. Etiam sit amet sodales urna, ultricies pellentesque metus. Aliquam posuere, eros ac volutpat posuere, velit leo sagittis ipsum, nec interdum risus arcu vitae nunc. Cras blandit dignissim nunc, quis dapibus nisl eleifend vitae. Cras sed ornare augue.
  Run keyword if  ${check-buttons}  Link button disabled  preview-verdict
  Run keyword if  ${check-buttons}  Test id disabled  publish-verdict
  Input text by test id  anto  ${date}
  Run keyword if  ${check-buttons}  Test id enabled  publish-verdict
  Link button enabled  preview-verdict

Publish verdict
  Click enabled by test id  publish-verdict
  Confirm yes no dialog
  Wait test id visible  new-appeal  30 s

Give legacy verdict
  [Arguments]  ${backend-id}  ${giver}  ${term}  ${date}
  Go to give new legacy verdict
  Input legacy verdict  ${backend-id}  ${giver}  ${term}  ${date}
  Publish verdict

Submit empty verdict
  [Arguments]  ${targetState}=verdictGiven
  Give legacy verdict  12345  -  Myönnetty  1.5.2018
  Click back
  Wait until  Application state should be  ${targetState}

Pate upload
  [Arguments]  ${index}  ${path}  ${type}  ${contents}  ${test-id}=upload-input
  Expose file input  input[data-test-id=${test-id}]
  Scroll to bottom
  Choose file  jquery=input[data-test-id=${test-id}]  ${path}
  Hide file input  input[data-test-id=${test-id}]
  Wait test id visible  batch-${index}-file-link
  Pate autocomplete select  batch-${index}-type  ${type}
  Test id enabled  batch-${index}-contents
  Input text by test id  batch-${index}-contents  ${contents}

Pate batch ready
  Test id enabled  batch-ready
  Scroll and click test id  batch-ready
  No such test id  batch-ready

Add legacy review
  [Arguments]  ${index}  ${name}  ${type}
  Scroll and click test id  add-review
  ${name-tid}=  Test id for  reviews  ${index}  name
  ${type-tid}=  Test id for  reviews  ${index}  type
  Input text by test id  ${name-tid}  ${name}
  Select from list by test id  ${type-tid}  ${type}

Add legacy foreman
  [Arguments]  ${index}  ${role}
  Scroll and click test id  add-foreman
  ${tid}=  Test id for  foremen  ${index}  role
  Input text by test id  ${tid}  ${role}

Add legacy condition
  [Arguments]  ${index}  ${name}
  Scroll and click test id  add-condition
  ${tid}=  Test id for  conditions  ${index}  name
  Input text by test id  ${tid}  ${name}

Do fetch verdict
  [Arguments]  ${fetchConfirmationText}
  Click enabled by test id  fetch-verdict
  #Wait for jQuery
  Wait test id visible  ok-dialog  60 s
  Element Text Should Be  jquery=p.dialog-desc:visible  ${fetchConfirmationText}
  Confirm ok dialog
  Wait test id visible  verdict-link-0

Fetch verdict
  Do fetch verdict  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 9 uutta vaatimusta Rakentaminen-välilehdelle.

Fetch YA verdict
  Do fetch verdict  Taustajärjestelmästä haettiin 1 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 2 uutta vaatimusta Rakentaminen-välilehdelle.

# Verdict is given
#   [Arguments]  ${kuntalupatunnus}  ${i}
#   Wait until  Element should be visible  application-verdict-details
#   Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-${i}']  ${kuntalupatunnus}

# Sign verdict
#   [Arguments]  ${password}  ${idx}=0
#   Click Element  xpath=//div[@data-test-id='given-verdict-id-${idx}-content']//button[@data-test-id='sign-verdict-button']
#   Wait Until  Element Should Be Visible  xpath=//input[@data-test-id='sign-verdict-password']
#   Input Text  xpath=//div[@id='dialog-sign-verdict']//input[@data-test-id='sign-verdict-password']  ${password}
#   Click Element  xpath=//div[@id='dialog-sign-verdict']//button[@data-test-id='do-sign-verdict']
#   Wait Until  Element should be visible  xpath=//div[@data-test-id='given-verdict-id-${idx}-content']//div[@data-test-id='verdict-signature-listing']


Check verdict row
  [Arguments]  ${index}  ${link}  ${date}  ${giver}
  Test id text is  verdict-link-${index}  ${link}
  Test id text is  verdict-date-${index}  ${date}
  Test id text is  verdict-giver-${index}  ${giver}

Open verdict
  [Arguments]  ${index}=0
  Scroll and click test id  verdict-link-${index}
  Wait test id visible  back
  No such test id  pate-spin

No verdict attachments
  No such test id  file-link-0

Verdict attachment count
  [Arguments]  ${amount}
  jQuery should match X times  table.pate-attachments tr  ${amount}

Verdict count is
  [Arguments]  ${amount}
  jQuery should match X times  table.pate-verdicts-table tbody tr  ${amount}


Fields required
  Wait until  Element should be visible  jquery=div.pate-required-fields-note:visible

Delete verdict
  [Arguments]  ${index}=0
  Click enabled by test id  verdict-delete-${index}
  Confirm yes no dialog
  No such test id  verdict-delete-${index}
