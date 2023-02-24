*** Settings ***

Documentation   YA application gets tasks based on verdict
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        task_resource.robot
Resource        keywords.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko prepares YA application and fills it to pass KRYSP validation later in test
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${secs}
  Set Suite Variable  ${propertyId}  753-416-18-1
  Set Suite Variable  ${appname-ya}  Taskitesti-YA-${secs}
  Create application the fast way  ${appname-ya}  ${propertyId}  ya-katulupa-vesi-ja-viemarityot
  Tab should be visible  info
  # Alkupvm + loppupvm
  Fill tyoaika fields
  # Osapuolet
  Invite erkki@example.com to application
  # LPK-2915, previous was Solita Oy, which didn't have parties invited to application
  Invite company to application  Esimerkki Oy
  Select yritys  tyomaastaVastaava
  select yritys  yleiset-alueet-maksaja
  Submit application
  [Teardown]  Logout

Katselmus task created in an YA application does not include any Rakennus information (LPK-719)
  Sonja logs in  False
  Open application  ${appname-ya}  ${propertyId}
  Open tab  verdict
  Fetch YA verdict
  Open tab  tasks
  Create katselmus task  task-katselmus-ya  uus muu ya-tarkastus
  Wait until  Element should not be visible  xpath=//div[@id='taskDocgen']//div[@data-repeating-id='rakennus']
  [Teardown]  Logout

Mikko sets started future date for YA application
  Mikko logs in
  Open application  ${appname-ya}  ${propertyId}
  Open tab  tasks
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  ${EMPTY}
  Set date  application-inform-construction-started-btn  10.8.3012

Error dialog is visible
  Integration error is  Aloituspäivä ei voi olla tulevaisuudessa.

Mikko sets started past date
  Wait test id visible  application-inform-construction-started-btn
  Set date and check  application-inform-construction-started-btn  construction-state-change-info-started  10.8.2012
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  Intonen Mikko

Neither edit or ready button are visible
  No such test id  application-inform-construction-started-bth
  No such test id  application-inform-construction-ready-bth
  [Teardown]  Logout

Sonja comes back and finalizes YA review
  Sonja logs in  False
  Open application  ${appname-ya}  ${propertyId}
  Open tab  tasks
  Open task  uus muu ya-tarkastus
  Edit YA katselmus  Aloituskatselmus  14.4.2016  Some Sonja  Description is mandatory for YA reviews.
  Click by test id  review-done
  Confirm yes no dialog
  Wait until  Confirm  dynamic-ok-confirm-dialog
  Return from review

Sonja can edit started date
  Wait test id visible  application-inform-construction-started-btn
  Set date and check  application-inform-construction-started-btn  construction-state-change-info-started  10.8.2013
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-started-by]  Sibbo Sonja

Sonja sets ready future date for YA application
  Set date  application-inform-construction-ready-btn  10.8.3012
  Integration error is  Valmistumispäivä ei voi olla tulevaisuudessa.

Sonja sets ready future date before the started date
  Set date  application-inform-construction-ready-btn  10.8.2000
  Integration error is  Rakennustöitä ei voida aloittaa valmistumisen jälkeen.

Sonja sets proper ready date
  Set date and check  application-inform-construction-ready-btn  construction-state-change-info-closed  10.8.2014
  Wait Until  Element Text Should Be  jquery=[data-test-id=task-closed-by]  Sibbo Sonja

Buttons are no longer visible
  No such test id  application-inform-construction-started-bth
  No such test id  application-inform-construction-ready-bth
  [Teardown]  Logout

No errors so far
  There are no frontend errors

*** Keywords ***

Select yritys
  [Arguments]  ${doc}
  Scroll and click input  section[data-doc-type=${doc}] input[value=yritys]
  Select from list by value  jquery=section[data-doc-type=${doc}] select[name=company-select]:first  esimerkki
  Wait until  Value should be  jquery=section[data-doc-type=${doc}] input[data-docgen-path='yritys.yritysnimi']:first  Esimerkki Oy
  Select from list by value  jquery=section[data-doc-type=${doc}] select[name=company-select]:last  esimerkki
  Wait until  Value should be  jquery=section[data-doc-type=${doc}] input[data-docgen-path='yritys.yritysnimi']:last  Esimerkki Oy

Integration error is
  [Arguments]  ${text}
  Wait until element is visible  integration-error-dialog
  Wait until  Element text should be  jquery=div#integration-error-dialog div.dialog-content p  ${text}
  Confirm  integration-error-dialog
  Wait until element is not visible  integration-error-dialog
