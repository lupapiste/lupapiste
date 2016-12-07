*** Settings ***
Resource   ../../common_resource.robot
Library    DateTime
Variables  ../21_stamping/variables.py
Variables  ../../common_variables.py

*** Keywords ***
Create bulletins
  [Arguments]  ${count}
  As Olli
  Create bulletins the fast way  ${count}

Create a bulletin and go to bulletin page
  Create bulletins  1
  Go to bulletins page
  Open bulletin by index  1

Bulletin list should have no rows
  Element should not be visible  //table[@id="application-bulletins-list"]/tbody/tr

Bulletin list should have rows
  [Arguments]  ${rows}
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]/tbody/tr
  Table with id should have rowcount  application-bulletins-list  ${rows}

Bulletin list should have rows and text
  [Arguments]  ${rows}  ${text}
  Wait until  Element should be visible  //table[@id='application-bulletins-list']//td[contains(text(), "${text}")]
  Bulletin list should have rows  ${rows}

Bulletin list should not have text
  [Arguments]  ${text}
  Element should not be visible  //table[@id='application-bulletins-list']//td[contains(text(), "${text}")]

Bulletin button should have bulletins left to fetch
  [Arguments]  ${elements}
  Wait until  Element text should be  xpath=//span[@data-test-id='bulletins-left']  ${elements} kpl

Load more bulletins
  ${initallyBulletinsLeft}=  Get text  //span[@data-test-id='bulletins-left']
  Click by test id  load-more-bulletins
  Wait until  Element should not be visible  //span[@data-test-id='bulletins-left'][contains(text(), '${initallyBulletinsLeft}')]

Publish bulletin
  Open tab  bulletin
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${MONTH_FROM_NOW} =    Add time to date  ${CURRENT_DATETIME}  30 days  %d.%m.%Y
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Input text with jQuery  input[name="proclamationStartsAt"]  ${TODAY_DD_MM_YYYY}
  Input text with jQuery  input[name="proclamationEndsAt"]  ${MONTH_FROM_NOW}
  Input text with jQuery  textarea[name="proclamationText"]  foobar
  Wait until  Element should be enabled  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin

Create application and publish bulletin
  [Arguments]  ${address}  ${propertyId}
  Create application with state  ${address}  ${propertyId}  lannan-varastointi  sent
  Open tab  bulletin
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${MONTH_FROM_NOW} =    Add time to date  ${CURRENT_DATETIME}  30 days  %d.%m.%Y
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Input text with jQuery  input[name="proclamationStartsAt"]  ${TODAY_DD_MM_YYYY}
  Input text with jQuery  input[name="proclamationEndsAt"]  ${MONTH_FROM_NOW}
  Input text with jQuery  textarea[name="proclamationText"]  foobar
  Wait until  Element should be enabled  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin
  Wait until  Element text should be  xpath=//p[@data-test-id='bulletin-state-paragraph']//span[@class='bulletin-state']  Kuulutettavana

Search bulletins by text
  [Arguments]  ${text}
  Input text  //div[@data-test-id='bulletin-search-field']//input[@type='text']  ${text}

Open bulletin by index
  [Arguments]  ${idx}
  # Indices are > 0 in XPath
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]/tbody/tr[${idx}]
  ${address}=  Get text  //table[@id='application-bulletins-list']//tr[${idx}]/td[3]
  ${address}=  Convert to upper case  ${address}
  Click element  //table[@id='application-bulletins-list']/tbody/tr[${idx}]
  Wait until  Element text should be  //div[@id='bulletin-component']//*[@data-test-id='bulletin-address']  ${address}

Bulletin state is
  [Arguments]  ${state}
  ${elemStateVal}=  Get Element Attribute  //div[@id='bulletin-component']//div[@data-test-id='bulletin-state']@data-test-state
  Should Be Equal As Strings  ${state}  ${elemStateVal}

Create application with attachment and publish it as bulletin
  [Arguments]  ${address}=Vaalantie 540  ${propertyId}=564-404-26-102
  Create application with state  ${address}  ${propertyId}  koeluontoinen-toiminta  sent
  Open tab  attachments
  Add attachment  application  ${PDF_TESTFILE_PATH1}  ${EMPTY}  operation=Koeluontoinen toiminta
  Return to application
  Add attachment  application  ${TXT_TESTFILE_PATH}  ${EMPTY}  operation=Koeluontoinen toiminta
  Return to application
  Publish bulletin
  Logout

Bulletin tab should be visible
  [Arguments]  ${tab}
  Wait until  Element should be visible  bulletin-${tab}-tab-component

Open bulletin tab
  [Arguments]  ${tab}
  Click by test id  bulletin-open-${tab}-tab
  Bulletin tab should be visible  ${tab}

Bulletin attachments count is
  [Arguments]  ${count}
  Element should be visible  xpath=//section[@id='bulletins']//table[@data-test-id='bulletin-attachments-template-table']
  Xpath Should Match X Times  //section[@id='bulletins']//table[@data-test-id='bulletin-attachments-template-table']/tbody/tr  ${count}

Vetuma signin is visible
  Element should be visible  vetuma-init

Create sent application
  [Arguments]  ${address}=Vaalantie 540  ${propertyId}=564-404-26-102
  Create application with state  ${address}  ${propertyId}  koeluontoinen-toiminta  sent

Bulletin shows as proclaimed
  Open tab  bulletin
  Wait until  Element Text Should Be  xpath=//p[@data-test-id='bulletin-state-paragraph']  Hakemuksen tila Julkipano-sivustolla: Kuulutettavana

Bulletin shows as proclaimed and can be moved to verdict given
  Open tab  bulletin
  Wait until  Element Text Should Be  xpath=//p[@data-test-id='bulletin-state-paragraph']  Hakemuksen tila Julkipano-sivustolla: Kuulutettavana  Hakemus julkaistaan seuraavaksi tilaan: Päätös annettu

Move bulletin to verdict given
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  ${WEEK_FROM_NOW} =     Add time to date  ${CURRENT_DATETIME}  7 days  %d.%m.%Y
  ${MONTH_FROM_NOW} =     Add time to date  ${CURRENT_DATETIME}  30 days  %d.%m.%Y
  Input text with jQuery  input[name="verdictGivenAt"]  ${TODAY_DD_MM_YYYY}
  Input text with jQuery  input[name="appealPeriodStartsAt"]  ${WEEK_FROM_NOW}
  Input text with jQuery  input[name="appealPeriodEndsAt"]  ${MONTH_FROM_NOW}
  Input text with jQuery  textarea[name="verdictGivenText"]  foobar
  Wait until  Element should be enabled  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin

Bulletin shows as verdict given and ce be moved to final
  Open tab  bulletin
  Wait until  Element Text Should Be  xpath=//p[@data-test-id='bulletin-state-paragraph']  Hakemuksen tila Julkipano-sivustolla: Päätös annettu  Hakemus julkaistaan seuraavaksi tilaan: Lainvoimainen

Bulletin verdict detail list should have rows
  [Arguments]  ${rows}
  Wait until  Element should be visible  //table[@id="application-verdict-details"]/tbody/tr
  Table with id should have rowcount  application-verdict-details  ${rows}

Move bulletin to final
  ${MONTH_FROM_NOW} =  Add time to date  ${CURRENT_DATETIME}  30 days
  ${OFFICIAL_DATE} =   Add time to date  ${MONTH_FROM_NOW}  1 days  %d.%m.%Y
  Input text with jQuery  input[name="officialAt"]  ${OFFICIAL_DATE}
  Wait until  Element should be enabled  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin

Bulletin shows as final
  Wait until  Element Text Should Be  xpath=//p[@data-test-id='bulletin-state-paragraph']  Hakemuksen tila Julkipano-sivustolla: Lainvoimainen

Write comment for bulletin
  [Arguments]  ${commentText}
  Wait until  Element should be visible  //textarea[@data-test-id='bulletin-comment-field']
  Input text  //textarea[@data-test-id='bulletin-comment-field']  ${commentText}

Send comment
  Wait until  Element should be enabled  //button[@data-test-id='send-comment']
  Click by test id  send-comment

Fill out alternate receiver form
  Wait until  Element should be visible  //div[@data-test-id='other-receiver-row']
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='firstName']  Keeko
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='lastName']  Valaskala
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='street']  Valaskatu 1
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='zip']  00001
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='city']  Äetsä
  Fill out alternate receiver email field

Fill out alternate receiver email field
  Wait until  Element should be visible  //div[@data-test-id='other-receiver-row']
  Input text  //div[@data-test-id='other-receiver-row']//input[@data-test-id='email']  keeko@valaskala.com
  Wait until  Checkbox Should Be Selected  //div[@data-test-id='other-receiver-row']//input[@data-test-id='emailPreferred']
