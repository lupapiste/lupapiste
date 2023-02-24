*** Settings ***

Documentation   Applicant orders printouts of application attachments
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        printout_resource.robot
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
  Value should be  jquery=input[data-test-id='contacts-orderer-firstName']  Mikko
  Value should be  jquery=input[data-test-id='contacts-orderer-lastName']  Intonen
  Value should be  jquery=input[data-test-id='contacts-orderer-email']  mikko@example.com
  Test id disabled  forward-button

Mikko can edit the order details
  Fill test id  contacts-orderer-lastName  Kumkvatti
  Fill test id  contacts-orderer-companyName  Firma Oy

Accept terms and conditions
  Toggle toggle  conditions-accepted
  Test id enabled  forward-button

Mikko changes payer and adds order details
  No such test id  contacts-payer-firstName
  Toggle toggle  payer-false
  Test id disabled  forward-button
  Fill test id  contacts-payer-firstName  Moomin
  Fill test id  contacts-payer-lastName  Moneymaker
  Fill test id  contacts-payer-streetAddress  Printterinpiennar 8
  Fill test id  contacts-payer-postalCode  12345
  Fill test id  contacts-payer-city  Humppila
  Fill test id  contacts-payer-email  moneymaker@example.net
  Fill test id  contacts-billingReference  pointer
  Fill test id  contacts-deliveryInstructions  Hurry up!

Mikko proceeds to order summary
  Scroll to bottom
  Click by test id  forward-button
  Wait Until  Element should be visible by test id  files-table
  Element should contain  jquery=tr[data-test-type='paapiirustus.asemapiirros']:first td:last  1
  Summary twice  Firma Oy
  Summary twice  Mikko Kumkvatti
  Summary twice  Rambokuja 6
  Summary twice  55550 Sipoo
  Summary twice  mikko@example.com

  Summary once  Moomin Moneymaker
  Summary once  Printterinpiennar 8
  Summary once  12345 Humppila
  Summary once  moneymaker@example.net

  Summary once  pointer
  Summary once  Hurry up!

Mikko submits the order
  Click by test id  forward-button
  Wait until page contains  Tilauksen lähettäminen onnistui

*** Keywords ***

Summary twice
  [Arguments]  ${text}
  jquery should match X times  span.order-summary-line:visible:contains(${text})  2

Summary once
  [Arguments]  ${text}
  jquery should match X times  span.order-summary-line:visible:contains(${text})  1
