*** Settings ***

Documentation   Applicant orders printouts of application attachments
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot
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
  Go to give new legacy verdict
  Input legacy verdict  123567890  Kaarina Krysp III  Myönnetty  01.05.2018

Sonja adds attachment to verdict
  Pate upload  0  ${TXT_TESTFILE_PATH}  Päätösote  Päätösote
  Pate batch ready

Sonja publishes verdict
  Publish verdict
  Click back
  [Teardown]  Logout

Mikko goes to order form
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  attachments
  Click enabled by test id  open-printing-order-form
  Element should be visible by test id  files-table
  Test id disabled  forward-button

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

Mikko proceeds to order summary
  Click by test id  forward-button
  Logout
