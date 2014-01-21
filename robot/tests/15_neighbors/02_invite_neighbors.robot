*** Settings ***

Documentation   Authority adds couple of neighbors, then we invite them and see how they respond
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko wants to build a water slide
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application the fast way  ${appname}  753  753-416-25-22  asuinrakennus
  Add comment  Lapsille vesiliuku

Mikko sets turvakielto for himself
  Open tab  parties
  Wait and click  xpath=//div[@id="application-parties-tab"]//input[@data-docgen-path="henkilo.henkilotiedot.turvakieltoKytkin"]
  Wait Until  Page Should Contain  Tallennettu
  [Teardown]  Logout

Sonja adds some neighbors
  Sonja logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Click by test id  manage-neighbors
  Add neighbor  1-2-3-4  a  a@example.com
  Add neighbor  1-2-3-4  b  b@example.com
  # Add neighbor "c" with wrong email, ups. Sonja must correct that later.
  Add neighbor  1-2-3-4  c  x@example.com
  # Add neighbor "d". This is a mistake that Sonja must fix later.
  Add neighbor  1-2-3-4  d  d@example.com
  # Check that they are all listed
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-a@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-b@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']

Sonja removes mistakenly added neighbor d
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']
  Click element  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//div[@id='dynamic-yes-no-confirm-dialog']
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-d@example.com']//a[@data-test-id='manage-neighbors-remove']

Sonja corrects the email address of neighbor c
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-edit']
  Click element  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-edit']
  Input text by test id  neighbors.edit.email  c@example.com
  Click by test id  neighbors.edit.ok
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']//a[@data-test-id='manage-neighbors-remove']

Sonja adds owners - luonnollinen henkilo
#  Set selenium speed  ${SLOW_SPEED}
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query  owners  {"ok":true,"owners":[{"postinumero":"04130","sukunimi":"Lönnroth","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Tage","syntymapvm":-454204800000,"paikkakunta":"SIBBO","jakeluosoite":"Präståkersvägen 1"}]}
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  20  20
  Wait until  Page Should Contain  Lönnroth, Tage
  Wait until  Page Should Contain  Präståkersvägen 1
  Wait until  Page Should Contain  04130 SIBBO
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='neighbors.select.ok']
  Click element  xpath=//*[@data-test-id='neighbors.select.ok']
  Clear mocks

Sonja adds owners - kuolinpesä
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query  owners  {"ok":true,"owners":[{"kuolinpvm":799372800000,"sukunimi":"Palm","ulkomaalainen":false,"henkilolaji":"kuolinpesa","etunimet":"Paul Olavi","syntymapvm":-1642982400000,"yhteyshenkilo":{"postinumero":"70620","sukunimi":"Ruhtinas","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Birgitta","syntymapvm":-599097600000,"paikkakunta":"KUOPIO","jakeluosoite":"Saastamoisenkatu 17"}}]}
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  20  20
  Wait until  Page Should Contain  Palm, Paul Olavi -Kuolinpesä
  Wait until  Page Should Contain  Ruhtinas, Birgitta
  Wait until  Page Should Contain  Saastamoisenkatu 17
  Wait until  Page Should Contain  70620 KUOPIO
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='neighbors.select.ok']
  Click element  xpath=//*[@data-test-id='neighbors.select.ok']
  Clear mocks

Property-id-by-point error
  Mock proxy error  property-id-by-point
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  20  20
  Wait until  Page Should Contain  Kiinteistötunnuksen haku ei onnistunut.
  Click element  xpath=//button[@data-test-id='neighbors.edit.cancel']
  Clear mocks

Find owners error
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query error  owners
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  20  20
  Wait until  Page Should Contain  Omistajien haku ei onnistunut.
  Page Should Contain  753-416-38-13
  Click element  xpath=//button[@data-test-id='neighbors.edit.cancel']
  Clear mocks

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
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Sonja meets user 'a' IRL and marks her as 'done'
  ${a_xpath} =  Set Variable  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Wait until  Element should be visible  ${a_xpath}
  Focus  ${a_xpath}
  Click element  ${a_xpath}
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should not be visible  ${a_xpath}

Sonja opens status details dialog
  Click element  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-firstName']  Sonja
  Element Text Should Be  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-lastName']  Sibbo
  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']//td[@data-test-id='neighbor-status-usereid']
  Click element  xpath=//div[@id='dialog-neighbor-status']//button[@data-test-id='neighbor-status-ok']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-neighbor-status']
  [Teardown]  Logout

Mikko sees neighbors and their status
  Mikko logs in
  Open application  ${appname}  753-416-25-22
  Open tab  statement
  Wait until  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Mikko can't mark neighbors as done, but can send an email invitation
  Element should not be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Element should be visible  xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']

Mikko sends an email invitation to neighbor 'b'
  Click element   xpath=//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  xpath=//div[@id='dialog-send-neighbor-email']
  Wait until  Input text  xpath=//input[@id='neighbors-sendemail-email']  b@example.com
  Click element  xpath=//div[@id='dialog-send-neighbor-email']//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-send-neighbor-email']
  [Teardown]  Logout

Mail is sent
  Go to  ${SERVER}/api/last-email
  Wait until  Element should contain  id=to  b@example.com

Neighbor clicks on email link and sees epplication
  Click element  xpath=//a
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-title']
  Wait until  Element should contain  xpath=//*[@data-test-id='application-title']  ${appname}
  Element should contain  xpath=//*[@data-test-id='application-property-id']  753-416-25-22
  Element should contain  xpath=//*[@data-test-id='test-application-operation']  Asuinrakennuksen rakentaminen

Hetu is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.henkilotiedot.hetu"]  ${EMPTY}

Address is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.osoite.katu"]  ${EMPTY}

Phone number is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.yhteystiedot.puhelin"]  ${EMPTY}
