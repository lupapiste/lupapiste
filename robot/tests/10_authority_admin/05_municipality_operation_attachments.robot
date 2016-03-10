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
  Wait until  Element Should Be Visible  xpath=//section[@id='application']//div[@data-test-id='application-pre-attachments-table']
  Attachment template is visible  paapiirustus.asemapiirros
  Attachment template is visible  paapiirustus.pohjapiirustus
  Attachment template is not visible  rakennuspaikan_hallinta.rasitustodistus
  Logout

Admin removes Pohjapiirustus template and adds Rasitustodistus template
  Sipoo logs in
  Go to page  attachments
  # Open dialog
  ${xpath} =  Set Variable  xpath=//table[@data-test-id='organization-operations-attachments']//tr[@data-op-id='kerrostalo-rivitalo']//a[@data-test-id='add-operations-attachments']
  Wait until  Element should be visible  ${xpath}
  Focus  ${xpath}
  Click element  ${xpath}
  Wait until  Element should be visible  xpath=//div[@id='dialog-edit-attachments']
  # Add Rasitusatodistus
  Click element  xpath=//div[@id='dialog-edit-attachments']//select[@class='selectm-source']//option[contains(text(),'Rasitustodistus')]
  Click element  xpath=//div[@id='dialog-edit-attachments']//button[@data-loc='selectm.add']
  Wait until  Element should be visible  xpath=//select[@data-test-id='selectm-target-list']//option[contains(text(),'Rasitustodistus')]
  # Remove Pohjapiirros
  Click element  xpath=//div[@id='dialog-edit-attachments']//select[@class='selectm-target']//option[contains(text(),'Pohjapiirustus')]
  Click element  xpath=//div[@id='dialog-edit-attachments']//button[@data-loc='selectm.remove']
  Wait until  Element should not be visible  xpath=//select[@data-test-id='selectm-target-list']//option[contains(text(),'Pohjapiirustus')]
  # Save
  Click element  xpath=//div[@id='dialog-edit-attachments']//button[@data-loc='selectm.ok']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-edit-attachments']
  Logout

Now new applications have Asemapiirros and Rasitustodistus, but no Pohjapiirustus
  Mikko logs in
  Create application the fast way  Latokuja 1, Sipoo  753-416-25-30  kerrostalo-rivitalo
  Open tab  attachments
  Wait until  Element Should Be Visible  xpath=//section[@id='application']//div[@data-test-id='application-pre-attachments-table']
  Attachment template is visible  paapiirustus.asemapiirros
  Attachment template is not visible  paapiirustus.pohjapiirustus
  Attachment template is visible  rakennuspaikan_hallinta.rasitustodistus


*** Keywords ***

Attachment template is visible
  [Arguments]  ${id}
  Element Should Be Visible  xpath=//section[@id='application']//div[@data-test-id='application-pre-attachments-table']//a[@data-test-type='${id}']

Attachment template is not visible
  [Arguments]  ${id}
  Element Should Not Be Visible  xpath=//section[@id='application']//div[@data-test-id='application-pre-attachments-table']//a[@data-test-type='${id}']
