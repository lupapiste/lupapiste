*** Settings ***

Documentation   Authority marks sent review as faulty
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        task_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py

*** Variables ***

${appname}     Faulty Towers
${propertyId}  753-416-18-1

*** Test Cases ***

Pena creates and submits application
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  pientalo
  Submit application
  [Teardown]  Logout

Sonja logs in and fetches verdict
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110

Sonja starts having Aloituskokous
  Open tab  tasks
  Wait Until  Page should contain  Kokoukset, katselmukset ja tarkastukset
  Task count is  task-katselmus  3

Add attachment
  Open task  Aloituskokous
  Wait Until  Title Should Be  ${appname} - Lupapiste
  Test id disabled  review-done
  Review active
  Review checkboxes disabled
  Upload verdict or task attachment  ${PDF_TESTFILE_PATH}  Tarkastusasiakirja  Check  Yleisesti hankkeeseen

Fill fields and finalize
  Edit R katselmus  lopullinen  12.9.2017  Sonja Sibbo  Let us begin
  Finalize review

There are two review-related attachments
  Check attachments  1

Sonja cancels marking review as faulty
  Open tab  tasks
  Open task  Aloituskokous
  Fill faulty dialog  Bad!
  Click by test id  confirm-no

Sonja marks review as faulty for real
  Fill faulty dialog  This review is bu hao.
  Click by test id  confirm-yes
  Wait until  Element should be visible  jquery=div.review-done.help-box:contains(virheelliseksi)
  No such test id  mark-faulty

Notes have been updated
  Test id disabled  katselmus.huomautukset.kuvaus
  Test id input is  katselmus.huomautukset.kuvaus  This review is bu hao.
  Test id disabled  katselmus.huomautukset.maaraAika
  Test id disabled  katselmus.huomautukset.toteaja
  Test id disabled  katselmus.huomautukset.toteamisHetki
  Test id disabled  katselmus.lasnaolijat
  Test id disabled  katselmus.poikkeamat
  Return from review

The review is marked as faulty in the reviews table
  Review row check  0  Aloituskokous  12.9.2017  Sonja Sibbo  Lopullinen  Kyllä
  Review row note  0  This review is bu hao.
  Review row does not have attachments  0
  Element should be visible  jquery=tr[data-test-index=0].faulty

Attachments are gone
  Check attachments  0
  [Teardown]  Logout


*** Keywords ***

Fill faulty dialog
  [Arguments]  ${text}
  Click by test id  mark-faulty
  Wait test id visible  faulty-review-notes
  Test id input is  faulty-review-notes  Let us begin
  Fill test id  faulty-review-notes  ${text}

Check attachments
  [Arguments]  ${count}
  Open tab  attachments
  jQuery should match X times  [data-test-type='katselmukset_ja_tarkastukset.aloituskokouksen_poytakirja']  ${count}
  jQuery should match X times  [data-test-type='katselmukset_ja_tarkastukset.tarkastusasiakirja']  ${count}
