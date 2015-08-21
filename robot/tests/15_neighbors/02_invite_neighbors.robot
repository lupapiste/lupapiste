*** Settings ***

Documentation   Authority adds couple of neighbors, then we invite them and see how they respond
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Mikko wants to build a water slide
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application the fast way  ${appname}  753-416-25-22  kerrostalo-rivitalo
  Open to authorities  Lapsille vesiliuku

Mikko sets turvakielto for himself
  Open tab  parties
  Open accordions  parties
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
  Click by test id  modal-dialog-submit-button
  Wait until  Element should not be visible  xpath=//tr[@data-test-id='manage-neighbors-email-x@example.com']//a[@data-test-id='manage-neighbors-remove']
  Wait until  Element should be visible  xpath=//tr[@data-test-id='manage-neighbors-email-c@example.com']//a[@data-test-id='manage-neighbors-remove']

Sonja adds owners - luonnollinen henkilo
#  Set selenium speed  ${SLOW_SPEED}
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query  owners  {"ok":true,"owners":[{"postinumero":"04130","sukunimi":"Lönnroth","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Tage","syntymapvm":-454204800000,"paikkakunta":"SIBBO","jakeluosoite":"Präståkersvägen 1"}]}
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Element Should Contain  xpath=//span[@class='owner-name']  Lönnroth, Tage
  Wait until  Element Should Contain  xpath=//span[@class='owner-street']  Präståkersvägen 1
  Wait until  Element Should Contain  xpath=//span[@class='owner-zip']  04130
  Wait until  Element Should Contain  xpath=//span[@class='owner-city']  SIBBO
  Click by test id  modal-dialog-submit-button
  Clear mocks

Sonja adds owners - kuolinpesä
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query  owners  {"ok":true,"owners":[{"kuolinpvm":799372800000,"sukunimi":"Palm","ulkomaalainen":false,"henkilolaji":"kuolinpesa","etunimet":"Paul Olavi","syntymapvm":-1642982400000,"yhteyshenkilo":{"postinumero":"70620","sukunimi":"Ruhtinas","ulkomaalainen":false,"henkilolaji":"luonnollinen","etunimet":"Birgitta","syntymapvm":-599097600000,"paikkakunta":"KUOPIO","jakeluosoite":"Saastamoisenkatu 17"}}]}
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Element Should Contain  xpath=//span[@class='owner-nameOfDeceased']  Palm, Paul Olavi
  Wait until  Element Should Contain  xpath=//span[@class='owner-name']  Ruhtinas, Birgitta
  Wait until  Element Should Contain  xpath=//span[@class='owner-street']  Saastamoisenkatu 17
  Wait until  Element Should Contain  xpath=//span[@class='owner-zip']  70620
  Wait until  Element Should Contain  xpath=//span[@class='owner-city']  KUOPIO
  Wait until  Element Should Be Enabled  xpath=//*[@data-test-id='modal-dialog-submit-button']
  Click by test id  modal-dialog-submit-button
  Clear mocks

Property-id-by-point error
  Mock proxy error  property-id-by-point
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Page Should Contain  Kiinteistötunnuksen haku ei onnistunut.
  Click by test id  modal-dialog-cancel-button
  Clear mocks

Find owners error
  Mock proxy  property-id-by-point  "75341600380013"
  Mock query error  owners
  Click Element At Coordinates  xpath=//*[@id='neighbors-map']/div  100  100
  Wait until  Page Should Contain  Omistajien haku ei onnistunut.
  Page Should Contain  753-416-38-13
  Click by test id  modal-dialog-cancel-button
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
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Sonja meets user 'a' IRL and marks her as 'done'
  ${a_xpath} =  Set Variable  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Wait until  Element should be visible  ${a_xpath}
  Focus  ${a_xpath}
  Click element  ${a_xpath}
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Wait until  Element should not be visible  ${a_xpath}

Sonja opens status details dialog
  Click element  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
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
  Wait until  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-a@example.com']//a[@data-test-id='neighbors-row-status-mark-done']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//span[@data-test-id='neighbors-row-status-open']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-c@example.com']//span[@data-test-id='neighbors-row-status-open']

Mikko can't mark neighbors as done, but can send an email invitation
  Element should not be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-mark-done']
  Element should be visible  xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']

Mikko sends an email invitation to neighbor 'b'
  Click element   xpath=//div[@id='application-statement-tab']//tr[@data-test-id='neighbors-row-email-b@example.com']//a[@data-test-id='neighbor-row-invite']
  Wait until  Element should be visible  xpath=//div[@id='dialog-send-neighbor-email']
  Wait until  Input text  xpath=//input[@id='neighbors-sendemail-email']  b@example.com
  Click element  xpath=//div[@id='dialog-send-neighbor-email']//button[@data-test-id='neighbors-sendemail-send']
  Wait until  Element should not be visible  xpath=//div[@id='dialog-send-neighbor-email']
  [Teardown]  Logout

Mail is sent
  Open last email
  Wait until  Element should contain  xpath=//dd[@data-test-id='to']  b@example.com

Neighbor clicks on email link and sees epplication
  Click element  xpath=//a
  Wait until  Element should be visible  xpath=//*[@data-test-id='application-title']
  Wait until  Element should contain  xpath=//*[@data-test-id='application-title']  ${appname}
  Element should contain  xpath=//*[@data-test-id='application-property-id']  753-416-25-22
  Element should contain  xpath=//*[@data-test-id='test-application-primary-operation']  Asuinkerrostalon tai rivitalon rakentaminen

Hetu is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.henkilotiedot.hetu"]  ${EMPTY}

Address is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.osoite.katu"]  ${EMPTY}

Phone number is not shown to neighbor
  Textfield Value Should Be  xpath=//div[@id="neighborPartiesDocgen"]//input[@data-docgen-path="henkilo.yhteystiedot.puhelin"]  ${EMPTY}
