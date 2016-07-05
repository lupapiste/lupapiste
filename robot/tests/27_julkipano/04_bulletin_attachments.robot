*** Settings ***

Documentation   Bulletin has published attachemnts in attachments tab
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        ./27_common.robot
Variables        ../21_stamping/variables.py


*** Test Cases ***

Create bulletin with attachment
  As Olli
  Create application with attachment and publish it as bulletin
  Logout

Unlogged user sees bulletin
  Go to bulletins page
  Bulletin list should have rows and text  1  Vaalantie 540

Bulletin has attachments tab, and uploaded attachments are visible
  Open bulletin by index  1
  Open bulletin tab  info
  Bulletin tab should be visible  info
  Open bulletin tab  attachments
  Bulletin attachments count is  2
  Element should be visible  //section[@id='bulletins']//table[@data-test-id='bulletin-attachments-template-table']/tbody/tr/td//a[contains(., '${PDF_TESTFILE_NAME1}')]
  Element should be visible  //section[@id='bulletins']//table[@data-test-id='bulletin-attachments-template-table']/tbody/tr/td//a[contains(., '${PDF_TXT_TESTFILE_NAME}')]
  Vetuma signin is visible

