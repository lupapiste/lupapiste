d*** Settings ***

Documentation  Attachment related resources

*** Keywords ***

Rollup open
  [Arguments]  ${name}
  jQuery should match X times  rollup-status-button[data-test-name='${name}'] button.rollup-button.toggled  1

Rollup closed
  [Arguments]  ${name}
  jQuery should match X times  rollup-status-button[data-test-name='${name}'] button.rollup-button.toggled  0

Toggle rollup
  [Arguments]  ${name}
  Scroll and click  rollup-status-button[data-test-name='${name}'] button.rollup-button

Rollup approved
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive

Rollup rejected
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button span.lupicon-circle-attention

Rollup neutral
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button
  Element should not be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive
  Element should not be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button span.lupicon-circle-attention

Approve row
  [Arguments]  ${row}
  Scroll and click  ${row} button.approve
  Wait until  Element should be visible  jquery=${row} i[data-test-icon='approved-icon']

Reject row
  [Arguments]  ${row}
  Scroll and click  ${row} button.reject

Remove row
  [Arguments]  ${row}
  Scroll and click  ${row} button[data-test-icon=delete-button]
  Confirm yes no dialog

Delete attachment version
  [Arguments]  ${versionNumber}
  Wait and click  show-attachment-versions
  Wait and click  jquery=tr[data-test-id='version-row-${versionNumber}'] a[data-test-id='delete-version']
  Confirm yes no dialog
  Wait until  Element should not be visible  show-attachment-versions

Unselect post verdict filter
  Checkbox wrapper selected by test id  postVerdict-filter-checkbox
  Scroll and click test id  postVerdict-filter-label
  Wait until  Checkbox wrapper not selected by test id  postVerdict-filter-checkbox

Total attachments row count is
  [Arguments]  ${count}
  Xpath Should Match X Times  //div[contains(@class, 'attachments-accordions')]//table[contains(@class,'attachments-table')]/tbody/tr[not(contains(@class, 'reject-note-row'))]  ${count}

Attachment group should be visible
  [Arguments]  ${nth}  ${group}
  Element Text Should Be  jquery=div#application-attachments-tab rollup[data-test-level=accordion-level-0]:eq(${nth}) span.rollup-status__text  ${group}

Attachment group should not be visible
  [Arguments]  ${nth}
  Element Should Not Be Visible  jquery=div#application-attachments-tab rollup[data-test-level=accordion-level-0]:eq(${nth})

Attachment subgroup should be visible
  [Arguments]  ${nth}  ${sub-nth}  ${group}
  Element Text Should Be  jquery=div#application-attachments-tab rollup[data-test-level=accordion-level-0]:eq(${nth}) rollup[data-test-level=accordion-level-1]:eq(${sub-nth}) span.rollup-status__text  ${group}

Attachment subgroup should not be visible
  [Arguments]  ${nth}  ${sub-nth}
  Element Should Not Be Visible  jquery=div#application-attachments-tab rollup[data-test-level=accordion-level-0]:eq(${nth}) rollup[data-test-level=accordion-level-1]:eq(${sub-nth})
