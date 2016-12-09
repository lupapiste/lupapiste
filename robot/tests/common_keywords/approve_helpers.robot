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
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) div.sticky button.positive i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status span:contains('Sibbo Sonja')
  # Every group is approved or neutral
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) i.rejected


Approve button visible
  [Arguments]  ${name}  ${idx}=0
  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) button[data-test-id=approve-doc-${name}]

Reject button visible
  [Arguments]  ${name}  ${idx}=0
  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) button[data-test-id=reject-doc-${name}]

Approve button not visible
  [Arguments]  ${name}  ${idx}=0
  Element should not be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) button[data-test-id=approve-doc-${name}]

Reject button not visible
  [Arguments]  ${name}  ${idx}=0
  Element should not be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) button[data-test-id=reject-doc-${name}]

Sonja accordion approved
  [Arguments]  ${name}  ${idx}=0
  Accordion approved  ${name}  ${idx}
  Approve button not visible  ${name}  ${idx}
  Reject button visible  ${name}  ${idx}
  # Every group is approved
  Wait Until  Element should not be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .accordion_content i.lupicon-check


Accordion rejected
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']:eq(${idx})
  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status i.rejected
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status span:contains('Sibbo Sonja')

Sonja accordion rejected
  [Arguments]  ${name}  ${idx}=0
  Accordion rejected  ${name}  ${idx}
  Approve button visible  ${name}  ${idx}
  Reject button not visible  ${name}  ${idx}

# If a subgroup is rejected, the approved accordion is negated: it is no longer positive, has reject icon
# but both buttons are visible.
Accordion negated
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky button.secondary.rejected i.rejected
  Element should not be visible  jquery=button.positve[data-accordion-id='${name}']:eq(${idx})


Sonja accordion negated
  [Arguments]  ${name}  ${idx}=0
  Accordion negated  ${name}  ${idx}
  Approve button visible  ${name}  ${idx}
  Reject button visible  ${name}  ${idx}
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
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=button[data-test-id=reject-doc-${name}]:eq(${idx})
  Scroll and click   button[data-test-id='reject-doc-${name}']:eq(${idx})

Click approve
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]:eq(${idx})
  Scroll and click  button[data-test-id='approve-doc-${name}']:eq(${idx})

Approve accordion
  [Arguments]  ${name}  ${idx}=0
  Click approve  ${name}  ${idx}
  Sonja accordion approved  ${name}  ${idx}

Reject accordion
  [Arguments]  ${name}  ${idx}=0
  Click reject  ${name}  ${idx}
  Sonja accordion rejected  ${name}  ${idx}

Approve group
  [Arguments]  ${name}
  Click approve  ${name}
  Sonja group approved  ${name}

Reject group
  [Arguments]  ${name}
  Click reject  ${name}
  Sonja group rejected  ${name}

# Reject notes and related


Press Key Test Id
  [Arguments]  ${id}  ${key}
  Press Key  jquery=input[data-test-id=${id}]  ${key}

Reject note is
  [Arguments]  ${prefix}  ${text}
  Scroll to test id  ${prefix}-note
  Wait test id visible  ${prefix}-note
  Test id text is  ${prefix}-note  ${text}

Reject and fill note
  [Arguments]  ${button}  ${prefix}  ${text}  ${doc-style}=True
  Run Keyword if  ${doc-style}  Click reject  ${button}
  Run Keyword unless  ${doc-style}  Click button  ${button}
  Wait test id visible  ${prefix}-editor
  Input text by test id  ${prefix}-editor  ${text}  True

Reject with note
  [Arguments]  ${button}  ${prefix}  ${text}
  Reject and fill note  ${button}  ${prefix}  ${text}
  Press Key test id  ${prefix}-editor  \\13
  Reject note is  ${prefix}  ${text}

Reject with note and save
  [Arguments]  ${button}  ${prefix}  ${text}
  Reject and fill note  ${button}  ${prefix}  ${text}
  Scroll and click test id  ${prefix}-save
  Reject note is  ${prefix}  ${text}


Reject with note but cancel
  [Arguments]  ${button}  ${prefix}  ${text}  ${doc-style}=True
  ${old}=  Execute Javascript  return $("[data-test-id=${prefix}-note]").text()
  Reject and fill note  ${button}  ${prefix}  ${text}  ${doc-style}
  Press Key test id  ${prefix}-editor  \\27
  No such test id  ${prefix}-editor
  Reject note is  ${prefix}  ${old}

Reject attachment with note
  [Arguments]  ${selector}  ${prefix}  ${text}
  Reject and fill note  ${selector}  ${prefix}  ${text}  False
  Press Key test id  ${prefix}-editor  \\13
  Reject note is  ${prefix}  ${text}
