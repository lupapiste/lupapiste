*** Settings ***

Documentation   Authority adds couple of neighbors, then we invite them and see how they respond
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko wants to build a water slide
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application with state  ${appname}  753-416-25-22  kerrostalo-rivitalo  open

Mikko sets turvakielto for himself
  Open tab  parties
  Open accordions  parties
  Wait and click  jquery:div#application-parties-tab label[id$="henkilo-henkilotiedot-turvakieltoKytkin"]
  Wait Until  Page Should Contain  Tallennettu
  [Teardown]  Logout

Sonja adds some neighbors
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Click by test id  manage-neighbors
  Wait until  Element should be visible  neighbors-content
  Element should not be visible  jquery=table.application-neighbors
  Add neighbor  753-416-25-22  a  a@example.com
  Add neighbor  753-416-25-22  b  b@example.com
  # Add neighbor "c" with wrong email, ups. Sonja must correct that later.
  Add neighbor  753-416-25-22  c  x@example.com
  # Add neighbor "d". This is a mistake that Sonja must fix later.
  Add neighbor  753-416-25-22  d  d@example.com
  # Check that they are all listed
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-b@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']

Sonja removes mistakenly added neighbor d
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//button[@data-test-id='manage-neighbors-remove']
  Click element  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//button[@data-test-id='manage-neighbors-remove']
  Wait until  Page should contain  Oletko varma että haluat poistaa naapurin 753-416-25-22 d?
  Confirm yes no dialog
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']

Sonja checks that property id and email validation works
  Wait until  Click element  jquery=tr[data-test-id='manage-neighbors-email-x@example.com'] button[data-test-id='manage-neighbors-edit-2']:visible
  Test id OK  neighbors.edit.propertyId
  Test id enabled  modal-dialog-submit-button
  Input text by test id  neighbors.edit.propertyId  ${EMPTY}
  Test id required  neighbors.edit.propertyId
  Test id not invalid  neighbors.edit.propertyId
  Test id disabled  modal-dialog-submit-button
  Input text by test id  neighbors.edit.propertyId  blaah
  Test id invalid  neighbors.edit.propertyId
  Test id not required  neighbors.edit.propertyId
  Test id disabled  modal-dialog-submit-button
  Input text by test id  neighbors.edit.propertyId  753-416-25-22
  Test id OK  neighbors.edit.propertyId
  Test id enabled  modal-dialog-submit-button
  Test id OK  neighbors.edit.email
  Input text by test id  neighbors.edit.email  blaah
  Test id invalid  neighbors.edit.email
  Test id disabled  modal-dialog-submit-button

Property Ids with maaraala are supported
  Input text by test id  neighbors.edit.propertyId  753-416-25-22M12345
  Test id invalid  neighbors.edit.propertyId
  Input text by test id  neighbors.edit.propertyId  753-416-25-22M1234
  Test id OK  neighbors.edit.propertyId

Sonja corrects the email address of neighbor c
  Input text by test id  neighbors.edit.email  c@example.com
  Test id not invalid  neighbors.edit.email
  Click by test id  modal-dialog-submit-button
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//button[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']//button[@data-test-id='manage-neighbors-remove']
  Scroll to top

Sonja adds owners - luonnollinen henkilo
  Mock proxy  property-info-by-wkt  [{"kiinttunnus": "75341600380013"}, {"kiinttunnus": "75341600380014"}]
  Mock datatables  owners  '{"ok":true,"owners":[{"propertyId": "75341600380013","postinumero":"04130","sukunimi":"Lönnroth","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Tage","syntymapvm":-454204800000,"paikkakunta":"SIBBO","jakeluosoite":"Präståkersvägen 1"}]}'
  Sleep  0.5s
  Start drawing a point
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-name')]  Lönnroth, Tage
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-street')]  Präståkersvägen 1
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-zip')]  04130
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-city')]  SIBBO
  Test id visible  no-owner-for-75341600380014
  Test id visible  neighbor-manual-add-hint
  Click by test id  modal-dialog-submit-button
  Click by test id  manage-neighbors-edit-3
  Test id enabled  neighbors.edit.type
  Click by test id  modal-dialog-submit-button
  [Teardown]  Clear mocks

Sonja adds owners - kuolinpesä
  Mock proxy  property-info-by-wkt  [{"kiinttunnus": "75341600380013"}]
  Mock datatables  owners  '{"ok":true,"owners":[{"kuolinpvm":799372800000,"propertyId":"75341600380013","sukunimi":"Palm","ulkomaalainen":false,"henkilolaji":"kuolinpesa","etunimet":"Paul Olavi","syntymapvm":-1642982400000,"yhteyshenkilo":{"postinumero":"70620","sukunimi":"Ruhtinas","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Birgitta","syntymapvm":-599097600000,"paikkakunta":"KUOPIO","jakeluosoite":"Saastamoisenkatu 17"}}]}'
  Scroll to top
  Start drawing a polygon
  Click Element At Coordinates  xpath=//div[@id='neighbors-map']/div   50   50
  Click Element At Coordinates  xpath=//div[@id='neighbors-map']/div   50  -50
  Click Element At Coordinates  xpath=//div[@id='neighbors-map']/div  -50  -50
  Click Element At Coordinates  xpath=//div[@id='neighbors-map']/div  -50   50
  Double Click Element  xpath=//div[@id='neighbors-map']/div
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-nameOfDeceased')]  Palm, Paul Olavi
  Wait until  Element Should Contain  xpath=//span[@class='owner-name']  Ruhtinas, Birgitta
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-street')]  Saastamoisenkatu 17
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-zip')]  70620
  Wait until  Element Should Contain  xpath=//span[contains(@class, 'owner-city')]  KUOPIO
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='modal-dialog-submit-button']
  No such test id  neighbor-manual-add-hint
  Click by test id  modal-dialog-submit-button
  [Teardown]  Clear mocks

property-info-by-wkt error
  Mock proxy error  property-info-by-wkt
  Start drawing a point
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Page Should Contain  Kiinteistötunnuksen haku ei onnistunut.
  Test id visible  neighbor-manual-add-hint
  Click by test id  modal-dialog-cancel-button
  [Teardown]  Clear mocks

Find owners error
  Mock proxy  property-info-by-wkt  [{"kiinttunnus": "75341600380013"}]
  Mock datatables error  owners
  Start drawing a point
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Page Should Contain  Omistajien haku ei onnistunut.
  Page Should Contain  753-416-38-13
  Wait test id visible  no-owner-for-75341600380013
  Test id visible  neighbor-manual-add-hint
  Click by test id  modal-dialog-cancel-button
  [Teardown]  Clear mocks

Sonja checks that everything is ok
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-b@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']
  Wait until  Page Should Contain  Lönnroth, Tage
  Wait until  Page Should Contain  Palm, Paul Olavi -Kuolinpesä
  Wait until  Page Should Contain  Ruhtinas, Birgitta
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']
  Click by test id  manager-neighbors-done
  # Make sure all neihgbors are in state "open":
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Sonja tries to invite user 'a' via bad email
  Click element  jquery=div#application-statement-tab tr[data-test-id='neighbors-row-email-a@example.com'] a[data-test-id=neighbor-row-invite]
  Wait until  Element should be enabled  xpath=//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should be enabled  neighbors-sendemail-email
  # Getting element not interactable errors
  Sleep  1s
  Input text  neighbors-sendemail-email  älämölöÄTexample.org
  Wait until  Element should be disabled  xpath=//button[@data-test-id='neighbors-sendemail-send']
  Click button  jquery=button.btn-dialog.close:visible

Sonja meets user 'a' IRL and marks her as 'done'
  ${a_xpath} =  Set Variable  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Wait until  Element should be visible  ${a_xpath}
  Focus  ${a_xpath}
  Click element  ${a_xpath}
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should not be visible  ${a_xpath}

Sonja opens status details dialog
  Click element  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should be visible  xpath=//div[@data-test-id='neighbor-status']//td[@data-test-id='neighbor-status-firstName']
  Element Text Should Be  xpath=//div[@data-test-id='neighbor-status']//td[@data-test-id='neighbor-status-firstName']  Sonja
  Element Text Should Be  xpath=//div[@data-test-id='neighbor-status']//td[@data-test-id='neighbor-status-lastName']  Sibbo
  Element should not be visible  xpath=//div[@data-test-id='neighbor-status']//td[@data-test-id='neighbor-status-usereid']
  Click element  xpath=//div[@data-test-id='neighbor-status']//button[@data-test-id='neighbor-status-ok']
  Wait until  Element should not be visible  xpath=//div[@data-test-id='neighbor-status']
  Sleep  0.5s
  [Teardown]  Logout

Mikko sees neighbors and their status
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

There is an option for asking municipality to hear all neighbours
  Element should be visible  xpath=//div[@id="application-statement-tab"]//div[@data-test-id="municipality-hears-neighbours"]//label

Mikko can't mark neighbors as done, but can send an email invitation
  Element should not be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']

Mikko sends an email invitation to neighbor 'c' but sends email to different address than c's default email address.
  Element should not be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='email-address-c@example.com']
  Click element   xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  neighbors-sendemail-email
  Sleep  1s
  Focus  neighbors-sendemail-email
  Input text  neighbors-sendemail-email  c@example.fi
  # only available from SeleniumLibrary 3.3.0 ->
  # Press Keys  neighbors-sendemail-email  BACK_SPACE  BACK_SPACE  BACK_SPACE  f  i
  Click element  xpath=//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  neighbors-sendemail-email
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='email-address-c@example.fi']

Mikko sends an email invitation to neighbor 'b'
  Element should not be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='email-address-b@example.com']
  Click element   xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  xpath=//div[@data-test-id='send-neighbor-email']
  Wait until  Input text  xpath=//input[@id='neighbors-sendemail-email']  b@example.com
  Click element  xpath=//div[@data-test-id='send-neighbor-email']//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  xpath=//div[@data-test-id='send-neighbor-email']
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='email-address-b@example.com']
  [Teardown]  Logout

Mail is sent
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  b@example.com

Neighbor clicks on email link and sees application
  Click Element  xpath=(//a[contains(., 'neighbor')])
  Neighbor application address should be  ${appname}
  Element should contain  xpath=//*[@data-test-id='application-property-id']  753-416-25-22
  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen

Hetu is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.henkilotiedot.hetu"]  ${EMPTY}

Address is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.osoite.katu"]  ${EMPTY}

Phone number is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.yhteystiedot.puhelin"]  ${EMPTY}

*** Keywords ***

Start drawing a point
  Wait until  Element should be visible  neighbors-map
  Scroll to top
  # Ignore error, navigation might already be active
  Run Keyword And Ignore Error  Click element  css=.olControlLupapisteEditingToolbar .olControlNavigationItemInactive
  Click element  css=.olControlDrawFeaturePointItemInactive

Start drawing a polygon
  Wait until  Element should be visible  neighbors-map
  Scroll to top
  # Ignore error, navigation might already be active
  Run Keyword And Ignore Error  Click element  css=.olControlLupapisteEditingToolbar .olControlNavigationItemInactive
  Click element  css=.olControlDrawFeaturePolygonItemInactive

Test id has class
  [Arguments]  ${tid}  ${cls}
  Wait until  Element should be visible  jquery=[data-test-id='${tid}'].${cls}

Test id does not have class
  [Arguments]  ${tid}  ${cls}
  Wait until  Element should not be visible  jquery=[data-test-id='${tid}'].${cls}
