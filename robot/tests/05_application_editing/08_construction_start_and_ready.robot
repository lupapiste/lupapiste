*** Settings ***

Documentation   On application, construction is set started and ready
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
  [Tags]  fail
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Start_ready_app_${secs}
#  Create application  ${appname}  753  753-416-25-23  YA-kaivulupa  # Vaaditut kentät tarvii täyttää!
  Create application the fast way   ${appname}  753  753-416-25-23  ya-katulupa-vesi-ja-viemarityot

Sonja fills in the application fields required by krysp
  [Tags]  fail
  Fill in the application fields required by krysp

Sonja submits the application, approves it and gives it a verdict
  [Tags]  fail
  Submit application
  Click enabled by test id  approve-application
  Throw in a verdict
  Wait Until  Element should not be visible  application-inform-construction-ready-btn

Sonja goes to the Rakentaminen tab and sets construction started via a dialog
  [Tags]  fail
  Open tab  tasks
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  02.06.2018
  Wait until  Application state should be  constructionStarted
  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']

Sonja goes to the Rakentaminen tab and sets construction ready via a dialog
  [Tags]  fail
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-ready-btn  02.07.2018
  #
  # TODO: The obligatory values in application would need to be filled before these would work.
  #       Currently, krysp sending is failing on application approval, and thus e.g. app states are not correct.
  #       Would need e.g. a new fixture that creates a ready-filled application.
  #       How much should be done in ftest, versus itest?
  #
  Wait until  Element should not be visible  //*[@data-test-id='application-inform-construction-ready-btn']
  Wait until  Application state should be  closed


*** Keywords ***

Sets construction started/ready via modal datepicker dialog
  [Arguments]  ${openDialogButtonId}  ${date}
  Click enabled by test id  ${openDialogButtonId}
  Wait until  element should be visible  dialog-modal-datepicker
  Wait Until  Element Should Be Enabled  modal-datepicker-date
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text by test id  modal-datepicker-date  ${date}
  Click enabled by test id  modal-datepicker-continue
  Confirm  dynamic-yes-no-confirm-dialog


Fill in the application fields required by krysp
  Fill tyoaika fields
  Open tab  parties
  Fill required fields for the parties


Fill tyoaika fields
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']//section[@data-doc-type='tyoaika']//h2
  ${tyoaikaDocId} =  Execute Javascript  $("section[data-doc-type='tyoaika'] h2").attr("data-doc-id");

  Execute JavaScript  $(".hasDatepicker").unbind("focus");

  Wait until  Element should be visible  //input[@id='${tyoaikaDocId}-tyoaika-alkaa-pvm']
  Execute Javascript  $("input[id='${tyoaikaDocId}-tyoaika-alkaa-pvm']").val("01.05.2014").change();
  Wait until  Textfield Value Should Be  //input[@id='${tyoaikaDocId}-tyoaika-alkaa-pvm']  01.05.2014

  Wait until  Element should be visible  //input[@id='${tyoaikaDocId}-tyoaika-paattyy-pvm']
  Execute Javascript  $("input[id='${tyoaikaDocId}-tyoaika-paattyy-pvm']").val("02.05.2014").change();
  Wait until  Textfield Value Should Be  //input[@id='${tyoaikaDocId}-tyoaika-paattyy-pvm']  02.05.2014


Fill required fields for the parties
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-parties-tab']
  Execute Javascript  $("input[value='yritys']").click();
  # Maksaja's default is Henkilo, that is why we have to wait its type has changed to Yritys.
  Wait until  Element should be visible  //section[@data-doc-type='yleiset-alueet-maksaja']//input[@data-docgen-path='yritys.yritysnimi']
  Fill in yritys info  hakija-ya
  Fill in yritys info  yleiset-alueet-maksaja
  Fill in yritys info  tyomaastaVastaava
#
# "Radio Button Should Be Set To" kanssa tulisi kayttaa radio inputin namea.
#   _selected radio inputin name ("52d7f896ad59c4ab06598577._selected") on muotoa:  data-doc-id + "._selected"
# Milla jQuerylla tuon data-doc-id:n saisi ulos?  Nainko:
#   //section[@data-doc-type='hakija-ya']//h2[@data-doc-id]
#  $("section[data-doc-type='hakija-ya'] h2").attr("data-doc-id")  -> "52d7f896ad59c4ab06598577"  -> Toimii
# id on muotoa:  "52d7f896ad59c4ab06598577-_selected-yritys"


Fill in yritys info
  [Arguments]  ${dataDocType}
  ${docSectionPath} =  //section[@data-doc-type='${dataDocType}']
  Element should be visible  ${docSectionPath}//input[@data-docgen-path='yritys.yritysnimi']
  Input text  ${docSectionPath}//input[@data-docgen-path='yritys.yritysnimi']  Firma Oy
  Input text  ${docSectionPath}//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.etunimi']  John
  Input text  ${docSectionPath}//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.sukunimi']  Rambo
  Wait until  Textfield Value Should Be  xpath=${docSectionPath}//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.sukunimi']  Rambo

