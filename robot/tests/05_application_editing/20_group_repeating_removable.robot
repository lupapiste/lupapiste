*** Settings ***

Documentation   Makes sure repeating groups have remove button LPK-3537
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource       ../common_keywords/approve_helpers.robot

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

Frontend errors
  There are no frontend errors

