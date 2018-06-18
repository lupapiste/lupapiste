*** Settings ***

Documentation   Settings and verdict templates for R
Suite Setup     Apply pate-phrases fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       pate_resource.robot

*** Test Cases ***

Sipoo admin logs in
  Sipoo logs in
  Go to page  pate-verdict-templates

No templates yet, let's create new one
  Wait test id visible  add-template
  No templates
  Click visible test id  add-template

Initial template status
  Template not published
  Test id text is  template-name-text  Päätöspohja
  Wait test id visible  required-template
  Wait test id visible  required-settings

Edit template name: cancel
  Click visible test id  template-name-edit
  Test id input is  template-name-input  Päätöspohja
  Type fill test id  template-name-input  ${SPACE}
  Test id disabled  template-name-save
  Type fill test id  template-name-input  Foobar
  Test id input is  template-name-input  Foobar
  Test id enabled  template-name-save
  Type fill test id  template-name-input  \\27
  Test id text is  template-name-text  Päätöspohja
  No such test id  template-name-save

Set template name to Tesht
  Click visible test id  template-name-edit
  Type fill test id  template-name-input  Tesht
  Press key test id  template-name-input  \\13
  Test id text is  template-name-text  Tesht
  No such test id  template-name-save

Set template name to Test
  Click visible test id  template-name-edit
  Type fill test id  template-name-input  Test
  Click visible test id  template-name-save
  Test id text is  template-name-text  Test
  No such test id  template-name-save

Fill the only required template field (giver)
  Select from test id  giver  viranhaltija
  No such test id  required-template

Toggle appeal section (muutoksenhaku)
  Wait test id visible  appeal-category
  Toggle toggle  section-appeal
  No such test id  appeal-category
  Toggle toggle  section-appeal
  Wait test id visible  appeal-category

Foreman selection
  Toggle disabled  tj
  Toggle toggle  tj-included
  Toggle selected  tj-included
  Toggle enabled  tj

Add condition (repeating)
  No such test id  conditions-0
  Scroll and click test id  add-condition
  ${tid}=  Test id for  conditions  0  condition
  Select phrase  ${tid}  muut

Add another condition
  No such test id  conditions-1
  Scroll and click test id  add-condition
  ${tid}=  Test id for  conditions  1  condition-edit
  Fill test id  ${tid}  Second condition

Remove the first condition
  ${tid}=  Test id for  conditions  0  remove-condition
  Click visible test id  ${tid}
  No such test id  conditions-1

There is only one condition
  ${tid}=  Test id for  conditions  0  condition-edit
  Test id text is  ${tid}  Second condition

Follow required link to settings
  Follow link to settings  required-settings

Fill julkipano with invalid value
  Fill test id  julkipano  -2
  Fill test id  anto  2
  Test id warning  julkipano

Fill date deltas and go back
  Fill test id  julkipano  1
  Fill test id  muutoksenhaku  3
  Fill test id  lainvoimainen  4
  Fill test id  aloitettava  1
  Fill test id  voimassa  2
  Click back

Now follow verdict code link to settings
  Follow link to settings  verdict-code-link

Select three verdict codes and return to the template
  Toggle toggle  ehdollinen
  Toggle toggle  myonnetty
  Toggle toggle  evatty
  Click back

Template has the selected verdict codes
  Test id select values are  verdict-code  ${EMPTY}  ehdollinen  evatty  myonnetty

Select ehdollinen and then remove it in settings
  Select from test id  verdict-code  ehdollinen
  Scroll and click test id  required-settings
  Toggle toggle  ehdollinen
  Click back

The verdict code select has been updated accordingly
  Test id select is  verdict-code  ${EMPTY}
  Test id select values are  verdict-code  ${EMPTY}  evatty  myonnetty

Follow reviews link to settings
  Scroll and click test id  reviews-link
  Scroll to test id  add-review

Add three reviews and return
  Click visible test id  add-review
  Fill review  0  K1  F1  P1  aloituskokous
  Click visible test id  add-review
  Fill review  1  K2  F2  P2  rakennekatselmus
  Click visible test id  add-review
  Fill review  2  K3  F3  P3  loppukatselmus
  Click back

Reviews are available in the template
  Check initial review  0  K1
  Check initial review  1  K2
  Check initial review  2  K3

Select some reviews
  Toggle review  0  True  True
  Toggle review  1  True  False

Follow plans link to settings
  Follow link to settings  plans-link

Fill lautakunta settings
  Fill test id  lautakunta-muutoksenhaku  10
  Fill test id  boardname  The Board

All required settings fields are now filled
  Scroll to top
  No such test id  settings-missing

Remove review K2
  ${tid}=  Test id for  reviews  1  remove-review
  Scroll and click test id  ${tid}

Rename review K3
  ${row-id}=  Row test id  reviews  1

  Fill test id  ${row-id}-fi  Beijing
  Fill test id  ${row-id}-en  Washington

Add two plans and go back to template
  Scroll and click test id  add-plan
  Scroll to top
  Wait test id visible  settings-missing
  Fill plan  0  S1  P1  P1
  Scroll and click test id  add-plan
  Fill plan  1  S2  P2  P2
  Scroll to top
  No such test id  settings-missing
  Click back

All the required fields are filled
  Scroll to top
  No such test id  required-template
  No such test id  required-settings

Reviews have changed
  Scroll to test id  section-conditions-label

  ${r1}=  Row test id  reviews  0
  Toggle not selected  ${r1}-included
  Toggle not selected  ${r1}-selected
  Toggle disabled  ${r1}-selected
  Test id text is  ${r1}-included-label  Beijing

  ${r2}=  Row test id  reviews  1
  Toggle selected  ${r2}-included
  Toggle selected  ${r2}-selected
  Toggle enabled  ${r2}-selected
  Test id text is  ${r2}-included-label  K1

  No such test id  reviews-2

Plans are listed
  Check initial plan  0  S1
  Check initial plan  1  S2

Change language to English
  Language to  EN

Check the template listing
  Test id text is  link-template-0  Test
  Test id enabled  open-template-0
  Test id enabled  copy-template-0
  Test id enabled  delete-template-0

Visit settings
  Click visible test id  open-settings
  Test id input is  julkipano  1
  Click back

Open template from link
  Click visible test id  link-template-0
  Test id text is  template-name-text  Test
  Click back

Copy template
  Click visible test id  copy-template-0
  Test id text is  template-name-text  Test (kopio)
  Click visible test id  template-name-edit
  Type fill test id  template-name-input  Foobar
  Click visible test id  template-name-save

  No such test id  required-template
  No such test id  required-settings
  Click back

Template list has been updated
  Test id text is  link-template-0  Test
  Test id text is  link-template-1  Foobar

Open template with button
  Click visible test id  open-template-0

Reviews are in English and sorted accordingly
  Scroll to test id  section-conditions-label

  ${r1}=  Row test id  reviews  0
  Toggle selected  ${r1}-included
  Toggle selected  ${r1}-selected
  Toggle enabled  ${r1}-selected
  Test id text is  ${r1}-included-label  R1

  ${r2}=  Row test id  reviews  1
  Toggle not selected  ${r2}-included
  Toggle not selected  ${r2}-selected
  Toggle disabled  ${r2}-selected
  Test id text is  ${r2}-included-label  Washington

  No such test id  reviews-2

Plans are listed in English
  Check initial plan  0  P1
  Check initial plan  1  P2



*** Keywords ***

No templates
  Wait until  Element should not be visible  jquery=table.pate-templates-table

Template not published
  Test id disabled  publish-template
  Test id text is  template-state  Päätöspohjaa ei olla vielä julkaistu.

Follow link to settings
  [Arguments]  ${tid}
  Scroll and click test id  ${tid}
  Wait test id visible  settings-missing

Fill row langs
  [Arguments]  ${row-id}  ${fi}  ${sv}  ${en}
  Fill test id  ${row-id}-fi  ${fi}
  Fill test id  ${row-id}-sv  ${sv}
  Fill test id  ${row-id}-en  ${en}

Fill review
  [Arguments]  ${index}  ${fi}  ${sv}  ${en}  ${type}
  ${row-id}=  Row test id  reviews  ${index}
  Fill row langs  ${row-id}  ${fi}  ${sv}  ${en}
  Select from test id  ${row-id}-type  ${type}

Fill plan
  [Arguments]  ${index}  ${fi}  ${sv}  ${en}
  ${row-id}=  Row test id  plans  ${index}
  Fill row langs  ${row-id}  ${fi}  ${sv}  ${en}

Check initial repeating
  [Arguments]  ${repeating}  ${index}  ${text}
  ${r}=  Row test id  ${repeating}  ${index}
  Scroll to test id  ${r}-included-label
  Toggle enabled  ${r}-included
  Toggle not selected  ${r}-included
  Toggle enabled  ${r}-included
  Toggle not selected  ${r}-included
  Test id text is  ${r}-included-label  ${text}

Check initial review
  [Arguments]  ${index}  ${text}
  Check initial repeating  reviews  ${index}  ${text}

Check initial plan
  [Arguments]  ${index}  ${text}
  Check initial repeating  plans  ${index}  ${text}

Toggle review
  [Arguments]  ${index}  ${included}  ${selected}
  ${r}=  Row test id  reviews  ${index}
  Scroll to test id  ${r}-included-label
  Run keyword if  ${included}  Toggle toggle  ${r}-included
  Run keyword if  ${selected}  Toggle toggle  ${r}-selected
