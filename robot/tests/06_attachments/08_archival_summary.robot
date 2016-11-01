*** Settings ***

Documentation  Check that attachment information is updated into archival summary tab and attachment metadata can be edited
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py


*** Test Cases ***


Pena creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Guggenheim${secs}
  Set Suite Variable  ${propertyId}  91-3-9906-101

  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  masto-tms

Pena adds attachment
  Open tab  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  type=erityissuunnitelmat.hulevesisuunnitelma  operation=Rakennuspaikka
  Return to application

Pena add another attachment with contents
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  type=paapiirustus.julkisivupiirustus  operation=Maston, piipun, säiliön, laiturin tai vastaavan rakentaminen
  Wait test id visible  attachment-contents-input
  Input text by test id  attachment-contents-input  this is julkisivu
  Return to application

Pena submits the application
  Submit application

Pena logs out
  Log out

Hannu logs in and gives verdict
  Hannu logs in
  Open application  ${appname}  ${propertyId}
  Submit empty verdict


Hannu goes to archival tab
  Open tab  archival
  Toggle additional controls  not-archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus

Archival tab contain three top level groups
  Wait until  Element should be visible  jquery=div.archival-summary-table
  Javascript?  $("div.archival-summary-table").length === 3

Main application documents are not archived
  Group header is  not-archived-application-documents  application-documents  Hankkeen pääasiakirjat

Main application documents contains one row
  Group row count is  not-archived-application-documents  application-documents  1

Pre verdict attachments group contains two sub groups
  Section group count is  not-archived-pre-groups  2

No archived pre verdict attachments
  Section group count is  archived-pre-groups  0

Pre verdict general attachments are in not archived group
  Group header is  not-archived-pre-groups  attachments.general  Yleiset hankkeen liitteet

Pre verdict operation attachments are in not archived group
  Group header is  not-archived-pre-groups  masto-tms  Maston, piipun, säiliön, laiturin tai vastaavan rakentaminen tai muun erillislaitteen sijoittaminen (esim. markiisi, aurinkokeräin)

Julkisivupiirustus contents is visible
  Attachment content is  not-archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus  this is julkisivu

Pre verdict general group contains one attachment
  Group row count is  not-archived-pre-groups  attachments.general  1

Pre verdict operation group contains one attachment
  Group row count is  not-archived-pre-groups  masto-tms  1

No archived post verdict attachments
  Section group count is  archived-post-groups  0

Post verdict attachments group contains one sub group
  Section group count is  not-archived-post-groups  1

Post verdict not archived group header
  Group header is  not-archived-post-groups  attachments.general  Yleiset hankkeen liitteet

Post verdict general group contains one attachment
  Group row count is  not-archived-post-groups  attachments.general  1

There is 4 attachments visible
  Total document count is  4


Hannu adds attachment
  Open tab  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}
  Return to application

Hannu goes back to archival tab and sees added attachment there
  Open tab  archival
  Total document count is  5

Added attachment is in not archived post verdict general attachments group
  Section group count is  not-archived-post-groups  1
  Group row count is  not-archived-post-groups  attachments.general  2


Hannu removes hulevesisuunnitelma file
  Open tab  attachments
  Scroll and click test id  preVerdict-filter-label
  Open attachment details  erityissuunnitelmat.hulevesisuunnitelma
  Delete attachment version  1.0

Hannu goes back to archival tab and does not see removed file anymore
  Return to application
  Open tab  archival
  Total document count is  4

Not archived general attachments group is hidden
  Section group count is  not-archived-pre-groups  1
  Group row count is  not-archived-pre-groups  attachments.general  0


Muut muu type is correct
  Attachment type group is  not-archived-post-groups  attachments.general  muut.muu  Muut liitteet
  Attachment type id is  not-archived-post-groups  attachments.general  muut.muu  Muu liite

Update contents
  Toggle additional controls  not-archived-post-groups  attachments.general  muut.muu
  Wait until  Set contents  rakennesuunnitelmatiedosto

Content is updated
  Attachment content is  not-archived-post-groups  attachments.general  muut.muu  rakennesuunnitelmatiedosto

Change attachment type
  Change type  erityissuunnitelmat.rakennesuunnitelma

Type is changed
  Attachment type group is  not-archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma  Erityissuunnitelmat
  Attachment type id is  not-archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma  Rakennesuunnitelma


Edit rakennesuunnitelma metadata
  Fill in archival metadata  myyntipalvelu=True

Rakennesuunnitelma group is changed
  Wait until  Group row count is  not-archived-post-groups  attachments.general  1
  Group row count is  archived-post-groups  attachments.general  1

Close additional controls, rakennesuunnitelma section is changed to archived
  Toggle additional controls  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma

Rakennesuunnitelma is not archivable
  Attachment archivability error icon is visible  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma

Rakennesuunnitelma retention perioid is entered
  Attachment retention perioid is  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma  Toistaiseksi, laskentaperuste rakennuksen purkamispäivä

Rakennesuunnitelma personal data is entered
  Attachment personal data is  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma  Ei sisällä henkilötietoja

Rakennesuunnitelma is not stamped
  Attachment is stamped  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma  False

Rakennesuunnitelma is marked as will publish
  Attachment will be published  archived-post-groups  attachments.general  erityissuunnitelmat.rakennesuunnitelma


Edit julkisivupiirustus metadata
  Toggle additional controls  not-archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus
  Fill in archival metadata  julkisuusluokka=julkinen  henkilotiedot=sisaltaa-arkaluonteisia  arkistointi=määräajan  nakyvyys=viranomainen  pituus=5

Julkisivupiirustus group is changed
  Wait until  Group row count is  not-archived-pre-groups  masto-tms  0
  Group row count is  archived-pre-groups  masto-tms  1

Close additional controls, julkisivupiirustus section is changed to archived
  Toggle additional controls  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus

Julkisivupiirustus is archivable
  [Tags]  pdfa
  Attachment is archivable  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus

Julkisivupiirustus retention perioid is entered
  Attachment retention perioid is  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus  Määräajan, 5 vuotta

Julkisivupiirustus personal data is entered
  Attachment personal data is  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus  Sisältää arkaluontoisia henkilötietoja

Julkisivupiirustus is not stamped
  Attachment is stamped  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus  False

Julkisivu is marked as will publish
  Attachment will be published  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus  False


Edit application document metadata
  Toggle additional controls  not-archived-application-documents  application-documents  application-document
  Fill in archival metadata

Application document group is changed
  Wait until  Group row count is  not-archived-application-documents  application-documents  0
  Group row count is  archived-application-documents  application-documents  1

Close application document additional controls
  Toggle additional controls  archived-application-documents  application-documents  application-document


Select julkissivupiirustus to be archived
  Element should be disabled  jquery=button[data-test-id=archive-selected]
  Select attachment to be archived  archived-pre-groups  masto-tms  paapiirustus.julkisivupiirustus
  # Documents cannot be archived since there is no TOJ in dev environment
  Element should be enabled  jquery=button[data-test-id=archive-selected]


*** Keywords ***


Group header is
  [Arguments]  ${section}  ${group}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.archival-summary-table div[data-test-id='${group}-header']  ${text}

Section group count is
  [Arguments]  ${section}  ${count}
  Javascript?  $("div[data-test-id=${section}] div.attachment-group-header").length === ${count}

Total document count is
  [Arguments]  ${count}
  Javascript?  $("div.attachment-row").length === ${count}

Group row count is
  [Arguments]  ${section}  ${group}  ${count}
  Javascript?  $("div[data-test-id=${section}] div.attachment-row[data-test-group='${group}']").length === ${count}

Toggle additional controls
  [Arguments]  ${section}  ${group}  ${type}
  Scroll and click  div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.attachment-row-top

Attachment content is
  [Arguments]  ${section}  ${group}  ${type}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.attachment-content-desc  ${text}

Attachment type group is
  [Arguments]  ${section}  ${group}  ${type}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.group-label  ${text}

Attachment type id is
  [Arguments]  ${section}  ${group}  ${type}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.type-id span  ${text}

Attachment is archivable
  [Arguments]  ${section}  ${group}  ${type}
  Element should be visible  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] input[data-test-id=send-to-archive]

Attachment archivability error icon is visible
  [Arguments]  ${section}  ${group}  ${type}
  Element should be visible  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] i.lupicon-circle-attention

Attachment retention perioid is
  [Arguments]  ${section}  ${group}  ${type}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.retention-period  ${text}

Attachment personal data is
  [Arguments]  ${section}  ${group}  ${type}  ${text}
  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.personal-data  ${text}

Attachment is stamped
  [Arguments]  ${section}  ${group}  ${type}  ${value}=True
  Run keyword If  ${value}  Element should be visible  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.stamped i.lupicon-check
  ...  ELSE  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.stamped  Ei

Attachment will be published
  [Arguments]  ${section}  ${group}  ${type}  ${value}=True
  Run keyword if  ${value}  Element should be visible  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.will-publish i.lupicon-check
  ...  ELSE  Element text should be  jquery=div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] div.will-publish  Ei

Change type
  [Arguments]  ${type}
  Select from list  attachment-type-select  ${type}

Set contents
  [Arguments]  ${text}
  Input text by test id  attachment-contents-update  ${text}

Fill in archival metadata
  [Arguments]  ${julkisuusluokka}=salainen  ${salassapitoaika}=2  ${salassapitoperuste}=Koska syy  ${suojaustaso}=ei-luokiteltu  ${turvallisuusluokka}=ei-turvallisuusluokkaluokiteltu  ${kayttajaryhma}=viranomaisryhma  ${kayttajaryhmakuvaus}=muokkausoikeus  ${henkilotiedot}=ei-sisalla  ${kieli}=fi  ${arkistointi}=toistaiseksi  ${pituus}=1  ${laskentaperuste}=rakennuksen_purkamispäivä  ${perustelu}=Siksi  ${myyntipalvelu}=False  ${nakyvyys}=julkinen
  Click by test id  edit-metadata
  Select from list  jquery=select[data-test-id=julkisuusluokka]:visible  ${julkisuusluokka}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Input text  jquery=input[data-test-id=salassapitoaika]:visible  ${salassapitoaika}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Input text  jquery=input[data-test-id=salassapitoperuste]:visible  ${salassapitoperuste}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Select from list  jquery=select[data-test-id=suojaustaso]:visible  ${suojaustaso}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Select from list  jquery=select[data-test-id=turvallisuusluokka]:visible  ${turvallisuusluokka}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Select from list  jquery=select[data-test-id=kayttajaryhma]:visible  ${kayttajaryhma}
  Run Keyword If  '${julkisuusluokka}' != 'julkinen'  Select from list  jquery=select[data-test-id=kayttajaryhmakuvaus]:visible  ${kayttajaryhmakuvaus}
  Select from list  jquery=select[data-test-id=henkilotiedot]:visible  ${henkilotiedot}
  Select from list  jquery=select[data-test-id=kieli]:visible  ${kieli}
  Select from list  jquery=select[data-test-id=arkistointi]:visible  ${arkistointi}
  Run Keyword If  '${arkistointi}' == 'määräajan'  Input text by test id  pituus  ${pituus}
  Run Keyword If  '${arkistointi}' == 'toistaiseksi'  Input text by test id  laskentaperuste  ${laskentaperuste}
  Input text  jquery=input[data-test-id= perustelu]:visible  ${perustelu}
  Run Keyword If  ${myyntipalvelu}  Select Checkbox  jquery=input[data-test-id=myyntipalvelu]:visible
  Select from list  jquery=select[data-test-id=nakyvyys]:visible  ${nakyvyys}
  Wait until  Click by test id  save-metadata

Select attachment to be archived
  [Arguments]  ${section}  ${group}  ${type}
  ${selector}=  Set Variable  div[data-test-id=${section}] div.attachment-row[data-test-group='${group}'][data-test-id='${type}'] input[data-test-id=send-to-archive]
  Element should be visible  jquery=${selector}
  Scroll to  ${selector}
  Select checkbox  jquery=${selector}
