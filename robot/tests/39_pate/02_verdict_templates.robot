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
  Pate fill test id  template-name-input  ${SPACE}
  Test id disabled  template-name-save
  Pate fill test id  template-name-input  Foobar
  Test id input is  template-name-input  Foobar
  Test id enabled  template-name-save
  Pate fill test id  template-name-input  \\27
  Test id text is  template-name-text  Päätöspohja
  No such test id  template-name-save

Set template name to Tesht
  Click visible test id  template-name-edit
  Pate fill test id  template-name-input  Tesht
  Press key test id  template-name-input  \\13
  Test id text is  template-name-text  Tesht
  No such test id  template-name-save

Set template name to Test
  Click visible test id  template-name-edit
  Pate fill test id  template-name-input  Test
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
  Pate fill test id  ${tid}  Second condition

Remove the first condition
  ${tid}=  Test id for  conditions  0  remove-condition
  Click visible test id  ${tid}
  No such test id  conditions-1

There is only one condition
  ${tid}=  Test id for  conditions  0  condition-edit
  Test id text is  ${tid}  Second condition

*** Keywords ***

No templates
  Wait until  Element should not be visible  jquery=table.pate-templates-table

Template not published
  Test id disabled  publish-template
  Test id text is  template-state  Päätöspohjaa ei olla vielä julkaistu.
