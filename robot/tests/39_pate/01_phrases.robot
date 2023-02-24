*** Settings ***

Documentation   Adding and editing phrases
Suite Setup     Apply pate-enabled fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       pate_resource.robot

*** Test Cases ***

Sipoo admin logs in
  Sipoo logs in
  Go to page  pate-verdict-templates

There are no phrases
  Phrase count  0

Phrase support is not visible on a new template
  Click visible test id  add-template
  Wait until  Phrase text is  paatosteksti  ${EMPTY}
  No such test id  paatosteksti-category
  No such test id  paatosteksti-autocomplete
  No such test id  paatosteksti-clear
  No such test id  paatosteksti-undo
  Click back

Start adding new phrase
  Click visible test id  add-phrase
  Test id empty  phrase-tag
  Textarea is empty  edit-phrase
  Test id select is  select-phrase-category  paatosteksti
  Test id disabled  save-phrase

Add tag and phrase
  Edit text  phrase-tag  Foo
  Test id disabled  save-phrase
  Edit text  edit-phrase  *Hello world!*
  Test id enabled  save-phrase

Tag or phrase cannot be blank
  Edit text  phrase-tag  ${EMPTY}
  Test id disabled  save-phrase
  Edit text  phrase-tag  Foo
  Test id enabled  save-phrase
  Edit text  edit-phrase  ${EMPTY}
  Test id disabled  save-phrase
  Edit text  edit-phrase  *Hello world!*
  Test id enabled  save-phrase

Check markup
  Click link  jquery=[data-test-id=preview-phrase-tab] a
  Wait until  Element should not be visible  jquery=[data-test-id=preview-phrase-tab] a
  Wait until  Element text should be  jquery=span.markup.phrase-preview span strong  Hello world!
  Click link  jquery=[data-test-id=edit-phrase-tab] a
  Wait test id visible  edit-phrase

Cancel editing
  Click visible test id  cancel-phrase

There are still no phrases
  Phrase count  0

Add new phrase for real this time
  Add phrase  sopimus  SOP  this is a binding contract.

Table has one phrase
  Phrase count  1
  Check phrase  0  Sopimus  SOP  this is a binding contract.

Edit the created phrase
  Click by test id  phrase-text-0
  Wait test id visible  phrase-tag
  Edit text  phrase-tag  A
  Click visible test id  save-phrase
  Wait test id visible  add-phrase
  Check phrase  0  Sopimus  A  this is a binding contract.

Add two more phrases
  Add phrase  yleinen  B  _General information_
  Add phrase  paatosteksti  C  Some kind of verdict.

There are now three phrases
  Phrase count  3

Default order is ascending tags
  Check order  A  B  C

Sort by tag
  Sort by  tag  up
  Check order  A  B  C
  Sort by  tag  down
  Check order  C  B  A

Sort by category
  Sort by  category  up
  Check order  C  A  B
  Sort by  category  down
  Check order  B  A  C

Sort by phrase
  Sort by  phrase  up
  Check order  B  C  A
  Sort by  phrase  down
  Check order  A  C  B

Delete phrase
  Click by test id  phrase-text-0
  Click visible test id  delete-phrase
  Confirm yes no dialog
  Wait test id visible  add-phrase

Order has not changed
  Phrase count  2
  Check order  C  B

Create new verdict template
  Click visible test id  add-template

Categories are listed
  Phrase categories  paatosteksti  paatosteksti  yleinen

Select general phrase
  Test id disabled  paatosteksti-undo
  Fill test id  paatosteksti-edit  Hello
  Select phrase category  paatosteksti  yleinen
  Select phrase  paatosteksti  general
  Test id text is  paatosteksti-autocomplete  B - _General information_
  Phrase text is  paatosteksti  Hello\n_General information_

Check phrase markup
  Click visible test id  paatosteksti-preview-tab
  Wait until  Element text should be  jquery=span.markup.phrase-preview span em  General information
  Test id disabled  paatosteksti-undo
  Test id disabled  paatosteksti-clear
  Click visible test id  paatosteksti-edit-tab

Undo phrase
  Click visible test id  paatosteksti-undo
  Phrase text is  paatosteksti  Hello
  Test id text is  paatosteksti-autocomplete  ${EMPTY}
  Test id disabled  paatosteksti-undo

Clear phrase text
  Click visible test id  paatosteksti-clear
  Wait until  Phrase text is  paatosteksti  ${EMPTY}

Select verdict phrase
  ${sel}=  Set variable  [data-test-id=paatosteksti-autocomplete] i.ac--clear
  Element should not be visible  jquery=${sel}
  Select phrase category  paatosteksti  paatosteksti
  Select phrase  paatosteksti  verdict
  Test id text is  paatosteksti-autocomplete  C - Some kind of verdict.
  Phrase text is  paatosteksti  Some kind of verdict.
  Test id enabled  paatosteksti-undo
  Click element  jquery=${sel}
  Test id text is  paatosteksti-autocomplete  ${EMPTY}
  Test id disabled  paatosteksti-undo
  Element should not be visible  jquery=${sel}
  Phrase text is  paatosteksti  Some kind of verdict.

*** Keywords ***

# Adds given text + space
Edit text
  [Arguments]  ${tid}  ${text}
  Fill test id  ${tid}  ${text}
  # Key press needed for triggering the change with robot, so we add
  # and remove space character
  Press key test id  ${tid}  \\32
  Press key test id  ${tid}  \\8

Phrase count
  [Arguments]  ${count}
  jQuery should match X times  table.pate-phrases-table tbody tr  ${count}

Add phrase
  [Arguments]  ${category}  ${tag}  ${text}
  Click visible test id  add-phrase
  Wait test id visible  select-phrase-category
  Select from test id  select-phrase-category  ${category}
  Edit text  phrase-tag  ${tag}
  Edit text  edit-phrase  ${text}
  Click visible test id  save-phrase
  Wait test id visible  add-phrase

Check phrase
  [Arguments]  ${index}  ${category}  ${tag}  ${text}
  Test id text is  phrase-category-${index}  ${category}
  Test id text is  phrase-tag-${index}  ${tag}
  Test id text is  phrase-text-${index}  ${text}

Check order
  [Arguments]  ${tag1}  ${tag2}  ${tag3}=ignore
  Test id text is  phrase-tag-0  ${tag1}
  Test id text is  phrase-tag-1  ${tag2}
  Run keyword unless  '${tag3}' == 'ignore'  Test id text is  phrase-tag-2  ${tag3}

Column icon
  [Arguments]  ${column}  ${icon}
  Wait until  Element should be visible  jquery=th[data-test-id=sort-by-${column}] i.${icon}

Sort by
  [Arguments]  ${column}  ${icon}
  Click visible test id  sort-by-${column}
  Column icon  ${column}  lupicon-chevron-small-${icon}

  Run keyword if  '${column}' != 'tag'  Column icon  tag  icon-placeholder
  Run keyword if  '${column}' != 'category'  Column icon  category  icon-placeholder
  Run keyword if  '${column}' != 'phrase'  Column icon  phrase  icon-placeholder
