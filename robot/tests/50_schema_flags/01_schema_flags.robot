*** Settings ***

Documentation   KuntaGML version vs. UI
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Variables ***

${property-id}          753-416-25-41
${address}              Lipputie 16
${pientalo}             Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)
${hetu-toggle-path}     rakennuksenOmistajat.0.henkilo.henkilotiedot.not-finnish-hetu
${hetu-path}            rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu
${foreign-path}         rakennuksenOmistajat.0.henkilo.henkilotiedot.ulkomainenHenkilotunnus
${tmp-toggle-path}      kaytto.tilapainenRakennusKytkin
${tmp-date-path}        kaytto.tilapainenRakennusvoimassaPvm
${class-path}           kaytto.rakennusluokka


*** Test Cases ***

Pena logs in and creates application
  Pena logs in
  Create application the fast way  ${address}  ${property-id}  pientalo

Only Finnish person id is supported
  Docgen path exists  ${hetu-path}
  No such docgen path  ${hetu-toggle-path}

No rakennusluokka, no temporary building support
  No such docgen path  ${class-path}
  No such docgen path  ${tmp-toggle-path}
  No such docgen path  ${tmp-date-path}

Finnish person id is marked missing
  Check missing  hetu

Set KuntaGML version to 2.2.3
  Set KuntaGML version  2.2.3
  Check missing  hetu

Foreign id is now supported
  Open tab  info
  No such docgen path  ${foreign-path}
  Select docgen checkbox  ${hetu-toggle-path}
  Docgen path exists  ${foreign-path}
  No such docgen path  ${hetu-path}

No rakennusluokka, no temporary building date
  No such docgen path  ${class-path}
  Select docgen checkbox  ${tmp-toggle-path}
  No such docgen path  ${tmp-date-path}

Foreign id is missing but Finnish id is not
  Check missing  ulkomainenHetu
  No such test id  missing-uusiRakennus-hetu

Set KuntaGML version to 2.2.4
  Set KuntaGML version  2.2.4

Temporary building date is now supported, rakennusluokka still is not
  No such test id  missing-uusiRakennus-rakennusluokka
  Open tab  info
  Docgen path exists  ${tmp-date-path}
  Unselect docgen checkbox  ${tmp-toggle-path}
  No such docgen path  ${tmp-date-path}
  No such docgen path  ${class-path}

Enable rakennusluokat for Sipoo
  Enable rakennusluokat

Rakennusluokka finally supported and required
  Docgen path exists  ${class-path}
  Check missing  rakennusluokka


*** Keywords ***

Check missing
  [Arguments]  ${name}
  Open tab  requiredFieldSummary
  Wait test id visible  missing-uusiRakennus-${name}

Set KuntaGML version
  [Arguments]  ${version}
  Go to  ${SERVER}/dev/set-kuntagml-version/753-R/R/${version}
  Wait until  Page should contain  ${version}
  Go back

Enable rakennusluokat
  Go to  ${SERVER}/dev/toggle-rakennusluokat/753-R/enable
  Wait until  Page should contain  Rakennusluokat enabled
  Go back

Docgen path exists
  [Arguments]  ${path}
  Wait until element is visible  jquery=[data-docgen-path='${path}']

No such docgen path
  [Arguments]  ${path}
  Wait until element is not visible  jquery=[data-docgen-path='${path}']

Select docgen checkbox
  [Arguments]  ${path}
  Select checkbox  jquery=input[data-docgen-path='${path}']

Unselect docgen checkbox
  [Arguments]  ${path}
  Unselect checkbox  jquery=input[data-docgen-path='${path}']
