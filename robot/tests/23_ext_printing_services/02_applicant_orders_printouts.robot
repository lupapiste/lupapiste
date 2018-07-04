*** Settings ***

Documentation   Applicant orders printouts of application attachments
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        printout_resource.robot
Variables      ../06_attachments/variables.py


*** Test Cases ***

Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

Mikko adds an attachment
  Open tab  attachments
  Upload attachment  ${PDF_TESTFILE_PATH}  Asemapiirros  Asemapiirros  Asuinkerrostalon tai rivitalon rakentaminen

Mikko submits application
  Submit application
  Logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja creates verdict
  Go to give new verdict
  Title Should Be  ${appname} - Lupapiste
  Input verdict  123567890  6  01.05.2018  01.06.2018  Kaarina Krysp III

Sonja adds attachment to verdict
  Upload verdict or task attachment  ${TXT_TESTFILE_PATH}  Päätösote  Päätösote  Yleisesti hankkeeseen
  Wait test id visible  targetted-attachments-table
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait for jQuery

Sonja creates RAM attachment to Mikkos's attachment
  Sonja adds RAM attachment  paapiirustus.asemapiirros
  [Teardown]  Logout

Mikko goes to order form
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Click enabled by test id  open-printing-order-form
  Element should be visible by test id  files-table
  Test id disabled  forward-button

There is only one orderable item
  jQuery should match X times  tr.not-in-printing-order  1

Mikko selects a file to be printed out
  Scroll and click  tr[data-test-type='paapiirustus.asemapiirros']:first i.lupicon-circle-plus
  Value should be  jquery=tr[data-test-type='paapiirustus.asemapiirros']:first input  1
  Test id enabled  forward-button

Mikko proceeds to the contact details form
  Click by test id  forward-button
  Wait until  Element should be visible by test id  form-contacts-orderer

Personal details are prefilled in the order form
  Value should be  jquery=input[data-test-id='input-contacts-orderer-firstName']  Mikko
  Value should be  jquery=input[data-test-id='input-contacts-orderer-lastName']  Intonen
  Value should be  jquery=input[data-test-id='input-contacts-orderer-email']  mikko@example.com
  Test id disabled  forward-button

Accept terms and conditions
  Click by test id  checkbox-conditions-accepted
  Test id enabled  forward-button
  Scroll to bottom
  Click by test id  forward-button
  Wait Until  Element should be visible by test id  files-table

Mikko proceeds to order summary
  Element should contain  jquery=tr[data-test-type='paapiirustus.asemapiirros']:first td:last  1

Mikko submits the order
  Click by test id  forward-button
  Wait until page contains  Tilauksen lähettäminen onnistui
  Logout
