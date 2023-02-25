*** Keywords ***

Approve application
  Open tab  requiredFieldSummary
  Wait Until  Page should contain  Suunnittelijoiden tietoja hyväksymättä
  Wait Until  Element Should Contain  jquery=div[data-test-id="non-approved-designers-list"] li:first  Pääsuunnittelija
  Wait Until  Element Should Contain  jquery=div[data-test-id="non-approved-designers-list"] li:last   Suunnittelija
  Wait until  Element should be visible  //div[@id='application-requiredFieldSummary-tab']
  ${BULLETIN_DESCR_VISIBLE}=  Run Keyword And Return Status  Test id visible  bulletin-op-description-summaryTab
  Run Keyword If  ${BULLETIN_DESCR_VISIBLE}  Fill test id  bulletin-op-description-summaryTab  Toimenpideotsikko julkipanoon
  Wait test id visible  approve-application-summaryTab
  Click enabled by test id  approve-application-summaryTab
  Wait until  Application state should be  sent

Accordion approved
  [Arguments]  ${name}  ${idx}=0
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) div.sticky button.positive i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status i.approved
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status span:contains('Sonja Sibbo')
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
  Wait Until  Element should be visible  jquery=section[data-doc-type=${name}]:eq(${idx}) .sticky .form-approval-status span:contains('Sonja Sibbo')

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
  Wait Until  Element should be visible  jquery=div.form-group[id*='${name}'] span:contains('Sonja Sibbo')


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
  Wait Until  Element should be visible  jquery=div.form-group[id*=${name}] span:contains('Sonja Sibbo')

Sonja group rejected
  [Arguments]  ${name}
  Group rejected  ${name}
  Wait Until  Element should not be visible  jquery=button[data-test-id=reject-doc-${name}]
  Wait Until  Element should be visible  jquery=button[data-test-id=approve-doc-${name}]

# Clickers

Click reject
  [Arguments]  ${name}  ${idx}=0
  ${selector}=  Set variable  button[data-test-id=reject-doc-${name}]:eq(${idx})
  Wait Until  Element should be visible  jquery=${selector}
  Scroll and click   ${selector}

Click approve
  [Arguments]  ${name}  ${idx}=0
  ${selector}=  Set variable  button[data-test-id=approve-doc-${name}]:eq(${idx})
  Wait Until  Element should be visible  jquery=${selector}
  Scroll and click   ${selector}

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

Reject note is
  [Arguments]  ${prefix}  ${text}
  Element should be visible by test id  ${prefix}-note
  Scroll to test id  ${prefix}-note
  Test id text is  ${prefix}-note  ${text}

Reject and fill note
  [Arguments]  ${button}  ${prefix}  ${text}  ${idx}=0  ${doc-style}=True
  Run Keyword if  ${doc-style}  Click reject  ${button}  ${idx}
  Run Keyword unless  ${doc-style}  Click button  ${button}
  Scroll by  100
  Wait test id visible  ${prefix}-editor
  Focus test id  ${prefix}-editor
  Input text by test id  ${prefix}-editor  ${text}  True

Reject with note
  [Arguments]  ${button}  ${prefix}  ${text}  ${idx}=0
  Reject and fill note  ${button}  ${prefix}  ${text}  ${idx}
  Press Key test id  ${prefix}-editor  \\13
  Run keyword if  "${text}"  Reject note is  ${prefix}  ${text}
  RUN keyword unless  "${text}"  No such test id  ${prefix}-note

Reject with note and save
  [Arguments]  ${button}  ${prefix}  ${text}
  Reject and fill note  ${button}  ${prefix}  ${text}
  Scroll and click test id  ${prefix}-save
  Reject note is  ${prefix}  ${text}

Reject with note and lose focus
  [Arguments]  ${button}  ${prefix}  ${text}
  Reject and fill note  ${button}  ${prefix}  ${text}
  Press Key test id  ${prefix}-editor  \\9
  Reject note is  ${prefix}  ${text}


Reject with note but cancel
  [Arguments]  ${button}  ${prefix}  ${text}  ${doc-style}=True  ${idx}=0
  ${old}=  Get Text  xpath=//*[@data-test-id="${prefix}-note"]
  Reject and fill note  ${button}  ${prefix}  ${text}  ${idx}  ${doc-style}
  Press Key test id  ${prefix}-editor  \\27
  No such test id  ${prefix}-editor
  Reject note is  ${prefix}  ${old}

Reject attachment with note
  [Arguments]  ${selector}  ${prefix}  ${text}  ${idx}=0
  Reject and fill note  ${selector}  ${prefix}  ${text}  ${idx}  False
  Press Key test id  ${prefix}-editor  \\13
  Reject note is  ${prefix}  ${text}
