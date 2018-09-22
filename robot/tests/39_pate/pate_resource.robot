*** Settings ***

Documentation   Resources for Pate robots
Resource       ../../common_resource.robot

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
  Wait until  jQuery should match X times  ${sel} ul.ac__items li  1
  Click element  jquery=${sel} li.ac--current

Select phrase
  [Arguments]  ${tid}  ${term}
  Pate autocomplete select  ${tid}-autocomplete  ${term}

Phrase text is
  [Arguments]  ${tid}  ${text}
  ${contents}=  Get text  jquery=[data-test-id=${tid}-edit]
  Should be equal  ${contents}  ${text}

Go back
  Click visible test id  back

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
  Scroll and click test id  back

# --------------------------
# Verdicts
# --------------------------

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
