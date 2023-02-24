*** Settings ***

Documentation   Makes sure repeating groups have remove button LPK-3537
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource       ../../common_keywords/approve_helpers.robot

*** Test Cases ***

Mikko the chosen one
  Mikko logs in
  ${time} =  Get Time  epoch
  Set Suite Variable  ${secs}  ${time}

Testing lannan varastointi groups
  Create application the fast way  RepeatingLanta-${secs}  753-416-18-2  lannan-varastointi
  Element should be visible  poikkeamistapa_tapaD_append
  Click element  poikkeamistapa_tapaD_append
  Wait until  Element should be visible  xpath=//section[@id='application']//div[@data-repeating-id='poikkeamistapa-tapaD']
  Input text with jQuery  input[data-docgen-path='poikkeamistapa.tapaD.0.patterinSijaintipaikka.omistaja.etunimi']  Testaaja1
  Sleep  0.2s
  Reload page and kill dev-box

Check that inputed text is visible
  Wait until  Element should be visible  xpath=//input[@data-docgen-path='poikkeamistapa.tapaD.0.patterinSijaintipaikka.omistaja.etunimi']
  # LP-365559
  Value should be  xpath=//input[@data-docgen-path='poikkeamistapa.tapaD.0.patterinSijaintipaikka.omistaja.etunimi']  Testaaja1

Add another lannan varastointi
  Click element  poikkeamistapa_tapaD_append
  Wait until  Xpath Should Match X Times  //section[@id='application']//div[@data-repeating-id='poikkeamistapa-tapaD']  2
  Input text with jQuery  input[data-docgen-path='poikkeamistapa.tapaD.1.patterinSijaintipaikka.omistaja.etunimi']  Testaaja2
  Reload page and kill dev-box
  Wait until  Element should be visible  xpath=//input[@data-docgen-path='poikkeamistapa.tapaD.1.patterinSijaintipaikka.omistaja.etunimi']
  Wait until  Xpath Should Match X Times  //section[@id='application']//div[@data-repeating-id='poikkeamistapa-tapaD']  2

Approval for non-approvable group is not possible...
  Approve button not visible  poikkeamistapa

But removing repeating groups is possible
  # LPK-3537
  Xpath Should Match X Times  //button[@data-test-class='delete-schemas.tapaD']  2
  # Delete first tapaD
  Scroll to  button[data-test-class='delete-schemas.tapaD']
  Click element  xpath=(//button[@data-test-class='delete-schemas.tapaD'])[1]
  Confirm yes no dialog
  Wait until  Xpath Should Match X Times  //section[@id='application']//div[@data-repeating-id='poikkeamistapa-tapaD']  1
  Value should be  xpath=//input[@data-docgen-path='poikkeamistapa.tapaD.1.patterinSijaintipaikka.omistaja.etunimi']  Testaaja2
  [Teardown]  Logout


# ------------------------------
# Building owners bug LPK-5343
# ------------------------------

Pena wants to build extends his sauna in Sipoo
  Pena logs in
  Create application the fast way  We need bigger sauna  753-423-3-52  sisatila-muutos

Three owners
  Owner count is  1
  Set owner name  0  First
  Add owner  1  Second
  Add owner  2  Third
  Owner count is  3

Remove the first two
  Remove owner  0
  Remove owner  1
  Owner count is  1
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  1

Adding owner now should not crash
  Add owner  3  Fourth
  Owner count is  2
  [Teardown]  Logout

Frontend errors
  There are no frontend errors


*** Keywords ***

Set owner name
  [Arguments]  ${index}  ${name}
  Wait Until  Element Should Be Visible  //div[@id='application-info-tab']//section[@data-doc-type='rakennuksen-muuttaminen']//input[@data-docgen-path='rakennuksenOmistajat.${index}.henkilo.henkilotiedot.etunimi']
  Input text with jQuery  \#application-info-tab section[data-doc-type="rakennuksen-muuttaminen"] input[data-docgen-path="rakennuksenOmistajat.${index}.henkilo.henkilotiedot.etunimi"]  ${name}

Add owner
  [Arguments]  ${index}  ${name}
  Wait Until  Element Should Be Visible  //button[@id="rakennuksenOmistajat_append"]
  Execute Javascript  $("button[id='rakennuksenOmistajat_append']").click();
  Set owner name  ${index}  ${name}

Remove owner
  [Arguments]  ${index}
  Set suite variable  ${selector}  div[data-repeating-id-rakennuksenomistajat=${index}] button[data-test-class="delete-schemas.rakennuksenOmistajat"]
  Scroll to  ${selector}
  Wait until  Click button  jquery=${selector}
  Confirm yes no dialog

Owner count is
  [Arguments]  ${n}
  Xpath Should Match X Times  //div[@id='application-info-tab']//div[@data-repeating-id="rakennuksenOmistajat"]  ${n}
