*** Settings ***

Documentation   Suti settings
Resource       ../../common_resource.robot
Resource       suti_resource.robot
Resource       ../29_guests/guest_resource.robot
Resource       ../13_statements/statement_resource.robot
Default Tags   suti

*** Variables ***
${suti-url}  http://localhost:8000/dev/suti

*** Test Cases ***

# ----------------------------------
# Admin authority tests
# ----------------------------------

Sipoo admin sets suti server address
  Sipoo logs in
  Go to page  backends

  Suti server  ${suti-url}  sutiuser  sutipassword

Suti is not enabled
  Checkbox wrapper not selected  suti-enabled

Enable Suti
  Toggle Suti
  Checkbox wrapper selected  suti-enabled

Set public www address
  Fill test id  suti-www  http://example.com/$$/suti
  Focus test id  suti-url
  Positive indicator should be visible

Reload page
  Reload Page
  
Url and username are set
  Scroll to test id  suti-send  
  Test id input is  suti-username  sutiuser
  Test id input is  suti-url  http://localhost:8000/dev/suti

Password is not echoed
  Test id input is  suti-password  ${EMPTY}

Suti is still enabled
  Checkbox wrapper selected  suti-enabled

Suti www address is set
  Test id input is  suti-www  http://example.com/$$/suti
  
Disable Suti
  Toggle Suti
  Checkbox wrapper not selected  suti-enabled

Operations not visible
  Go to page  operations
  Element should not be visible  jquery=label[for=suti-kiinteistonmuodostus]

Operations visible when Suti enabled
  Toggle Suti
  Go to page  operations
  Element should be visible  jquery=label[for=suti-kiinteistonmuodostus]

Select one Suti operation
  Toggle Suti operation  kiinteistonmuodostus

Operation selected after reload
  Reload page
  Wait Until  Checkbox wrapper selected  suti-kiinteistonmuodostus

Disable Suti and logout
  Toggle Suti
  [Teardown]  Logout

# ----------------------------------
# Application tests
# ----------------------------------

Pena logs in and creates application
  Set Suite Variable  ${appname}  Kaislikossa sutisee
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

No Suti yet
  Open tab  attachments
  No such test id  suti-display
  [Teardown]  Logout

Sipoo admin enables Suti but not for Pena's operation
  Sipoo logs in
  Toggle Suti
  [Teardown]  Logout

Pena still does not see Suti
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  No such test id  suti-display
  [Teardown]  Logout

Sipoo admin adds kerrostalo-rivitalo to Suti operations
  Sipoo logs in
  Toggle Suti operation  kerrostalo-rivitalo
  [Teardown]  Logout

Pena can now see Suti
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait test id visible  suti-display

Rollup includes property id
    Element should contain  jquery=div.suti-display button.rollup-button span  ${propertyid}

Fields and note are empty
  Test id input is  suti-display-id  ${EMPTY}
  Test id disabled  suti-display-link
  Checkbox wrapper not selected  suti-display-added
  No such test id  suti-display-note
  No such test id  suti-display-products

# Development (mockup) Suti server (/dev/suti) treats suti-ids semantically:
# empty: no products
# bad: 501
# auth: requires username (suti) and password (secret)
# all the other ids return products. See web.clj for details.

No products
  Suti id and note  empty  Ei lähtötietoja.

Backend error
  Suti id and note  bad  Tietojen haku ei onnistunut.  True

Incorrect authorization credentials
  Clear Suti id  
  Suti id and note  auth  Tietojen haku ei onnistunut.
  [Teardown]  Logout

Sipoo admin inputs correct credentials
  Sipoo logs in
  Suti server  ${suti-url}/  suti  secret
  [Teardown]  Logout

Pena can now see auth products
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait test id visible  suti-display
  Check row  0  Four  Vanhentunut  7.7.2016  27.6.2016  
  Check row  1  Five  Vanhentunut  9.7.2016  ${EMPTY}
  Check row  2  Six  Voimassa  ${EMPTY}  ${EMPTY}

Default products
  Set Suti id  1234
  Check default products

Selection added checkbox clears products
  Click label  suti-display-added
  Test id text is  suti-display-note  Lisää suunnittelun lähtötiedot Suunnitelmat ja liitteet -välilehdelle käsin.
  Test id disabled  suti-display-id
  Test id disabled  suti-display-link
  No such test id  suti-display-products

Pena invites Mikko as guest
  Open tab  parties
  Invite application guest  mikko@example.com  See my Suti!

Pena submits application
  Submit application
  [Teardown]  Logout

Sonja logs in and edits Suti
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait test id visible  suti-display
  Click label  suti-display-added
  Check default products

Sonja invites Teppo as statement giver
  Open tab  statement
  Scroll and click test id  add-statement
  Invite 'manual' statement giver  1  Laoban  Teppo  teppo@example.com  ${EMPTY}
  [Teardown]  Logout
      
Mikko logs in, sees Suti but cannot edit
  Mikko logs in
  Readonly Suti
  [Teardown]  Logout

Teppo logs in, sees Suti but cannot edit
  Teppo logs in
  Readonly Suti
  [Teardown]  Logout  

No frontend errors
  There are no frontend errors
  
  






