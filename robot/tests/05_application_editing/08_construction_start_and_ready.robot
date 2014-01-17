*** Settings ***

Documentation   On application, construction is set started and ready
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja prepares the application
#  [Tags]  fail
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Start_ready_app_${secs}
#  Create application  ${appname}  753  753-416-25-23  YA-kaivulupa  # Vaaditut kentät tarvii täyttää!
  Create application the fast way   ${appname}  753  753-416-25-23  ya-katulupa-vesi-ja-viemarityot

Sonja fills in the application fields required by krysp
#  [Tags]  fail
  Fill tyoaika fields
  Open tab  parties
  Fill required fields for the parties

Sonja submits the application, approves it and gives it a verdict
#  [Tags]  fail
  Submit application
  Click enabled by test id  approve-application
  Throw in a verdict
  Wait Until  Element should not be visible  application-inform-construction-ready-btn

Sonja goes to the Rakentaminen tab and sets construction started via a dialog
#  [Tags]  fail
  Open tab  tasks
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  02.06.2018
  Wait until  Application state should be  constructionStarted
  Wait until  Element should be visible  //*[@data-test-id='application-inform-construction-ready-btn']

Sonja goes to the Rakentaminen tab and sets construction ready via a dialog
#  [Tags]  fail
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


Fill tyoaika fields
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']
  Execute JavaScript  $(".hasDatepicker").unbind("focus");

  Wait until  Element should be visible  //input[contains(@id,'tyoaika-alkaa-pvm')]
  Execute Javascript  $("input[id*='tyoaika-alkaa-pvm']").val("01.05.2014").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'tyoaika-alkaa-pvm')]  01.05.2014

  Wait until  Element should be visible  //input[contains(@id,'tyoaika-paattyy-pvm')]
  Execute Javascript  $("input[id*='tyoaika-paattyy-pvm']").val("02.05.2014").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'tyoaika-paattyy-pvm')]  02.05.2014


Fill required fields for the parties
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-parties-tab']
  Execute Javascript  $("input[value='henkilo']").click();
  # Maksaja's default is Henkilo, that is why we have to wait its type has changed to Yritys.
  Wait until  Element should be visible  //section[@data-doc-type='yleiset-alueet-maksaja']//input[@data-docgen-path='henkilo.henkilotiedot.etunimi']
  Fill in yritys info  hakija-ya
  Fill in yritys info  yleiset-alueet-maksaja
  Fill in yritys info  tyomaastaVastaava


Fill in yritys info
  [Arguments]  ${dataDocType}
  ## NOTE: When using another variable (i.e. ${dataDocType}) to set the value of a variable, the keyword "Set Variable" must be used.
  ${docSectionPath} =  Set Variable  //section[@data-doc-type='${dataDocType}']
  Element should be visible  ${docSectionPath}//input[@data-docgen-path='henkilo.henkilotiedot.etunimi']
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.henkilotiedot.etunimi']  John
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.henkilotiedot.sukunimi']  Rambo
  Wait until  Textfield Value Should Be  ${docSectionPath}//input[@data-docgen-path='henkilo.henkilotiedot.sukunimi']  Rambo
  ## tyomaastaVastaava goes to vastuuhenkilotieto in krysp, which has more required fields. Let's fill them up.
  Run Keyword If  '${dataDocType}' == 'tyomaastaVastaava'  Fill in additional yritys info for tyomaasta vastaava


Fill in additional yritys info for tyomaasta vastaava
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.osoite.katu']  Katu
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.osoite.postinumero']  98765
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.osoite.postitoimipaikannimi']  Sipoo
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.yhteystiedot.puhelin']  0401234567
  Input text  ${docSectionPath}//input[@data-docgen-path='henkilo.yhteystiedot.email']  john.rambo@cia.com
## TODO: Miksi tama ei toimi ?
#  Wait until  Textfield Value Should Be  ${docSectionPath}//input[@data-docgen-path='henkilo.yhteystiedot.email']  john.rambo@cia.com


