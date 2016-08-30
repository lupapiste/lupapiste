*** Settings ***

Documentation   Authority admin edits organization specific operation attachments
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

New applications have Asemapiirros, Pohjapiirustus and no Rasitustodistus
  Mikko logs in
  Create application the fast way  Latokuja 1, Sipoo  753-416-25-30  kerrostalo-rivitalo
  Open tab  attachments
  Attachment template is visible  paapiirustus.asemapiirros
  Attachment template is visible  paapiirustus.pohjapiirustus
  Attachment template is not visible  rakennuspaikan_hallinta.rasitustodistus
  Logout

Admin removes Pohjapiirustus template and adds Rasitustodistus template
  Sipoo logs in
  Go to page  attachments
  # Open dialog
  ${path} =  Set Variable  jquery=table[data-test-id=organization-operations-attachments] tr[data-op-id=kerrostalo-rivitalo] a[data-test-id=add-operations-attachments]
  Wait until  Element should be visible  ${path}
  Focus  ${path}
  Click element  ${path}
  Wait until  Element should be visible  jquery=div#dialog-edit-attachments
  # Add Rasitusatodistus
  Click element  jquery=div#dialog-edit-attachments select.selectm-source option:contains('Rasitustodistus')
  Click element  jquery=div#dialog-edit-attachments button[data-loc='selectm.add']
  Wait until  Element should be visible  jquery=select[data-test-id=selectm-target-list] option:contains('Rasitustodistus')
  # Remove Pohjapiirros
  Click element  jquery=div#dialog-edit-attachments select.selectm-target option:contains('Pohjapiirustus')
  Click element  jquery=div#dialog-edit-attachments button[data-loc='selectm.remove']
  Wait until  Element should not be visible  jquery=select[data-test-id=selectm-target-list] option:contains('Pohjapiirustus')
  # Save
  Click element  jquery=div#dialog-edit-attachments button[data-loc='selectm.ok']
  Wait until  Element should not be visible  jquery=div#dialog-edit-attachments
  Logout

Now new applications have Asemapiirros and Rasitustodistus, but no Pohjapiirustus
  Mikko logs in
  Create application the fast way  Latokuja 1, Sipoo  753-416-25-30  kerrostalo-rivitalo
  Open tab  attachments
  Attachment template is visible  paapiirustus.asemapiirros
  Attachment template is not visible  paapiirustus.pohjapiirustus
  Attachment template is visible  rakennuspaikan_hallinta.rasitustodistus


*** Keywords ***

Attachment template is visible
  [Arguments]  ${type}
  Element Should Be Visible  jquery=div#application-attachments-tab tr[data-test-type='${type}']

Attachment template is not visible
  [Arguments]  ${type}
  Element Should Not Be Visible  jquery=div#application-attachments-tab tr[data-test-type='${type}']
