*** Settings ***

Documentation   Resources for Pate robots
Resource       ../../common_resource.robot
Variables       ../../common_variables.py

*** Variables ***

${R-verdict-result}  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 9 uutta vaatimusta Rakentaminen-välilehdelle.
${YA-verdict-result}  Taustajärjestelmästä haettiin 1 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 2 uutta vaatimusta Rakentaminen-välilehdelle.

*** Keywords ***

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
  Kill dev-box
  Link button disabled  preview-verdict
  Test id disabled  publish-verdict
  Click visible test id  toggle-all

Input legacy verdict
  [Arguments]  ${backend-id}  ${giver}  ${term}  ${date}  ${check-buttons}=True
  Wait test id visible  verdict-code
  ${select}=  Run keyword and return status  Element should be visible  jquery=select[data-test-id=verdict-code]
  ${input}=  Run keyword and return status  Element should be visible  jquery=input[data-test-id=verdict-code]
  ${autocomplete}=  Run keyword and return status  Element should be visible  jquery=div.pate-autocomplete[data-test-id=verdict-code]
  Input text by test id  kuntalupatunnus  ${backend-id}
  Input text by test id  handler  ${giver}
  Input text by test id  verdict-section  22
  Run keyword if  ${select}  Select from test id by text  verdict-code  ${term}
  Run keyword if  ${autocomplete}  Pate autocomplete select  verdict-code  ${term}
  Run keyword if  ${input}  Input text by test id  verdict-code  ${term}
  Input text by test id  verdict-text  Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris ut leo a ipsum sagittis faucibus. Integer ac velit eget odio tincidunt facilisis. Duis eu purus elementum, efficitur eros non, ultrices lectus. Praesent non ipsum id sapien dictum pharetra. Etiam sit amet sodales urna, ultricies pellentesque metus. Aliquam posuere, eros ac volutpat posuere, velit leo sagittis ipsum, nec interdum risus arcu vitae nunc. Cras blandit dignissim nunc, quis dapibus nisl eleifend vitae. Cras sed ornare augue.
  Run keyword if  ${check-buttons}  Link button disabled  preview-verdict
  Run keyword if  ${check-buttons}  Test id disabled  publish-verdict
  Input text by test id  anto  ${date}
  Run keyword if  ${check-buttons}  Test id enabled  publish-verdict
  Link button enabled  preview-verdict

Input legacy contract
  [Arguments]  ${backend-id}  ${giver}  ${date}
  Input text by test id  kuntalupatunnus  ${backend-id}
  Input text by test id  handler  ${giver}
  Input text by test id  contract-text  Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris ut leo a ipsum sagittis faucibus. Integer ac velit eget odio tincidunt facilisis. Duis eu purus elementum, efficitur eros non, ultrices lectus. Praesent non ipsum id sapien dictum pharetra. Etiam sit amet sodales urna, ultricies pellentesque metus. Aliquam posuere, eros ac volutpat posuere, velit leo sagittis ipsum, nec interdum risus arcu vitae nunc. Cras blandit dignissim nunc, quis dapibus nisl eleifend vitae. Cras sed ornare augue.
  Input text by test id  verdict-date  ${date}
  # Debounce
  Sleep  1s

Publish verdict
  Click enabled by test id  publish-verdict
  Confirm yes no dialog
  No such test id  publish-verdict

Give legacy verdict
  [Arguments]  ${backend-id}  ${giver}  ${term}  ${date}
  Go to give new legacy verdict
  Input legacy verdict  ${backend-id}  ${giver}  ${term}  ${date}
  # Debounce
  Sleep  2s
  Publish verdict

Give legacy contract
  [Arguments]  ${backend-id}  ${giver}  ${date}
  Go to give new legacy verdict
  Input legacy contract  ${backend-id}  ${giver}  ${date}
  # Debounce
  Sleep  2s
  Publish verdict
  Click back

Submit empty verdict
  [Arguments]  ${targetState}=verdictGiven
  Give legacy verdict  12345  -  Myönnetty  1.5.2018
  Click back
  Wait until  Application state should be  ${targetState}

Pate upload
  [Arguments]  ${index}  ${path}  ${type}  ${contents}  ${test-id-prefix}=upload
  ${test-id}=  Set variable  ${test-id-prefix}-input
  Wait test id visible  ${test-id-prefix}-label
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

Input review
  [Arguments]  ${index}  ${name}  ${type}
  ${name-tid}=  Test id for  reviews  ${index}  name
  ${type-tid}=  Test id for  reviews  ${index}  type
  Input text by test id  ${name-tid}  ${name}
  Select from list by test id and value  ${type-tid}  ${type}

Add legacy review
  [Arguments]  ${index}  ${name}  ${type}
  Scroll and click test id  add-review
  Input review  ${index}  ${name}  ${type}

Add legacy foreman
  [Arguments]  ${index}  ${role}
  Scroll and click test id  add-foreman
  ${tid}=  Test id for  foremen  ${index}  role
  Select from list by test id and value  ${tid}  ${role}

Add legacy condition
  [Arguments]  ${index}  ${name}
  Scroll and click test id  add-condition
  ${tid}=  Test id for  conditions  ${index}  name
  Input text by test id  ${tid}  ${name}

Verdict fetched
  [Arguments]  ${fetchConfirmationText}
  Wait test id visible  ok-dialog  60 s
  Element Text Should Be  jquery=p.dialog-desc:visible  ${fetchConfirmationText}
  Confirm notification dialog
  Wait test id visible  verdict-link-0

R verdict fetched
  Verdict fetched  ${R-verdict-result}

YA verdict fetched
  Verdict fetched  ${YA-verdict-result}

Do fetch verdict
  [Arguments]  ${fetchConfirmationText}
  Click enabled by test id  fetch-verdict
  Sleep  2s
  Verdict fetched  ${fetchConfirmationText}

Fetch verdict
  Do fetch verdict  ${R-verdict-result}

Fetch YA verdict
  Do fetch verdict  ${YA-verdict-result}

# Verdict is given
#   [Arguments]  ${kuntalupatunnus}  ${i}
#   Wait until  Element should be visible  application-verdict-details
#   Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-${i}']  ${kuntalupatunnus}

Check signature
  [Arguments]  ${name}  ${index}=1
  Test id text is  signature-${index}-name  ${name}
  Test id text is  signature-${index}-date  ${CURRENT_DATE}

Sign contract
  [Arguments]  ${password}  ${ok}=True
  Scroll and click test id  sign-contract

  Fill test id  password-input  ${password}
  Click by test id  password-button
  Run keyword if  ${ok}  No such test id  password-input
  Run keyword if  ${ok}  No such test id  sign-contract
  Run keyword unless  ${ok}  Wait until element is visible  jquery=input[type=password].warning

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
  Xpath should match X times  //table[contains(@class, 'pate-attachments')]//tr  ${amount}

Verdict count is
  [Arguments]  ${amount}
  Xpath should match X times  //table[@id='verdicts-table']/tbody/tr  ${amount}


Fields required
  Wait until  Element should be visible  jquery=div.pate-required-fields-note:visible

Delete verdict
  [Arguments]  ${index}=0
  Click enabled by test id  verdict-delete-${index}
  Confirm yes no dialog
