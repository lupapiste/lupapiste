*** Keywords ***

Approve application
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  xpath=//button[@data-test-id="approve-application-summaryTab"]
  Click enabled by test id  approve-application
  # Confirm warning about designers
  Wait Until  Page should contain  Suunnittelijoiden tietoja hyväksymättä
  Wait Until  Element Should Contain  jquery=#modal-dialog-content-component li:first  Pääsuunnittelija
  Wait Until  Element Should Contain  jquery=#modal-dialog-content-component li:last   Suunnittelija
  Confirm yes no dialog
  Wait until  Application state should be  sent

Accordion approved
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] div.sticky button.positive i.approved
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')
  # Every group is approved or neutral
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] i.rejected


Approve button visible
  [Arguments]  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] button[data-test-id=approve-doc-${name}]

Reject button visible
  [Arguments]  ${name}
  Element should be visible  jquery=section[data-doc-type=${name}] button[data-test-id=reject-doc-${name}]

Approve button not visible
  [Arguments]  ${name}
  Element should not be visible  jquery=section[data-doc-type=${name}] button[data-test-id=approve-doc-${name}]

Reject button not visible
  [Arguments]  ${name}
  Element should not be visible  jquery=section[data-doc-type=${name}] button[data-test-id=reject-doc-${name}]

Sonja accordion approved
  [Arguments]  ${name}
  Accordion approved  ${name}
  Approve button not visible  ${name}
  Reject button visible  ${name}
  # Every group is approved
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}] .accordion_content i.lupicon-check


Accordion rejected
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']
  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status i.rejected
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky .form-approval-status span:contains('Sibbo Sonja')

Sonja accordion rejected
  [Arguments]  ${name}
  Accordion rejected  ${name}
  Approve button visible  ${name}
  Reject button not visible  ${name}

# If a subgroup is rejected, the approved accordion is negated: it is no longer positive, has reject icon
# but both buttons are visible.
Accordion negated
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}] .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']


Sonja accordion negated
  [Arguments]  ${name}
  Accordion negated  ${name}
  Approve button visible  ${name}
  Reject button visible  ${name}
  # Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.approved
  # Element should be visible  jquery=section[data-doc-type=${name}] .sticky .group-buttons button.rejected

Group neutral
  [Arguments]  ${name}
  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group approved
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] i.approved
  Wait Until  Element should not be visible  jquery=div.form-group[id*='${name}'] i.rejected
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('OK')
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] span:contains('Sibbo Sonja')


Sonja group approved
  [Arguments]  ${name}
  Group approved  ${name}
  Wait Until  Element should not be visible  jquery=button[data-test-id=approve-doc-${name}]
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]

Group rejected
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] i.rejected
  Wait Until  Element should not be visible  jquery=div.form-group[id*=${name}] i.approved
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Tarkennettavaa')
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Sibbo Sonja')

Sonja group rejected
  [Arguments]  ${name}
  Group rejected  ${name}
  Wait Until  Element should not be visible  jquery=button[data-test-id=reject-doc-${name}]
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]

# Clickers

Click reject
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]
  Scroll and click test id  reject-doc-${name}

Click approve
  [Arguments]  ${name}
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]
  Scroll and click test id  approve-doc-${name}

Approve accordion
  [Arguments]  ${name}
  Click approve  ${name}
  Sonja accordion approved  ${name}

Reject accordion
  [Arguments]  ${name}
  Click reject  ${name}
  Sonja accordion rejected  ${name}

Approve group
  [Arguments]  ${name}
  Click approve  ${name}
  Sonja group approved  ${name}

Reject group
  [Arguments]  ${name}
  Click reject  ${name}
  Sonja group rejected  ${name}
