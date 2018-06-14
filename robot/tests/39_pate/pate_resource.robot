*** Settings ***

Documentation   Resources for Pate robots
Resource       ../../common_resource.robot

*** Keywords ***

Phrase categories
  [Arguments]  ${tid}  @{categories}
  Wait test id visible  ${tid}-category
  @{values}=  Get list items  jquery=[data-test-id=${tid}-category]  values=True
  Should be true  @{values} == @{categories}

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
