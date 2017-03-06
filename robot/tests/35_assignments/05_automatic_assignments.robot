*** Settings ***

Documentation   Automatic assignments are created as new attachments are added
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Do
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application with state  ${appname}  ${propertyid}  pientalo  submitted
  Open tab  attachments

Pena uploads an application for which there is an automatic assignment trigger in Sipoo
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Logout

Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments

Automatic assignment with the attachment as the target has been created
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  Aita ja asema, 1
  Logout


Pena uploads two more applications
  As Pena
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Logout

Sonja can see that there are more attachments in the same assignment
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  Aita ja asema, 3

Sonja completes the automatic assignment
  Click by test id  mark-assignment-complete
  Wait until  Element should not be visible  xpath=//div[@data-test-id='automatic-assignment']
  Logout

Pena uploads two more applications belonging in different automatic assignment trigger groups
  As Pena
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Upload attachment  ${TXT_TESTFILE_PATH}  ELY:n tai kunnan poikkeamapäätös  poikkeamapäätös  Yleisesti hankkeeseen
  Logout

Sonja can see two automatic assignments
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  Aita ja asema, 1
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-1']//div[@data-test-id='assignment-text']  ELY ja naapuri, 1

Sonja deletes ELY:n tai kunnan poikkeamapäätös attachment, and the associated automatic assignment is deleted
  Wait Until  Delete attachment  ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos
  Wait until  Element should not be visible  xpath=//div[@data-test-id='automatic-assignment-1']
