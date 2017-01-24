*** Settings ***

Documentation  Attachment rollup states
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py


*** Test Cases ***

Sonja logs in and creates application
  Set Suite Variable  ${appname}  Rock'n'Rollup
  Sonja logs in
  Create application with state   ${appname}  753-416-25-30  kerrostalo-rivitalo  open

Sonja goes to attachments tab
  Open tab  attachments
  Rollup rejected  Pääpiirustukset
  Rollup rejected  Muut suunnitelmat
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen

Every accordion is open
  Rollup open  Hakemuksen liitteet
  Rollup open  Osapuolet
  Rollup open  Pääpiirustukset
  Rollup open  Muut suunnitelmat
  Rollup open  Asuinkerrostalon tai rivitalon rakentaminen

Close Muut suunnitelmat
  Toggle rollup  Muut suunnitelmat
  Rollup closed  Muut suunnitelmat

Toggle all opens all
  Scroll and click test id  toggle-all-accordions
  Rollup open  Hakemuksen liitteet
  Rollup open  Osapuolet
  Rollup open  Pääpiirustukset
  Rollup open  Muut suunnitelmat
  Rollup open  Asuinkerrostalon tai rivitalon rakentaminen

Toggle all closes all
  Scroll and click test id  toggle-all-accordions
  Rollup closed  Hakemuksen liitteet
  Rollup closed  Osapuolet
  Rollup closed  Pääpiirustukset
  Rollup closed  Muut suunnitelmat
  Rollup closed  Asuinkerrostalon tai rivitalon rakentaminen
  Scroll and click test id  toggle-all-accordions

Close Osapuolet and toggle all
  Toggle rollup  Muut suunnitelmat
  Rollup closed  Muut suunnitelmat
  Scroll and click test id  toggle-all-accordions
  Rollup open  Hakemuksen liitteet
  Rollup open  Osapuolet
  Rollup open  Pääpiirustukset
  Rollup open  Muut suunnitelmat
  Rollup open  Asuinkerrostalon tai rivitalon rakentaminen

Close Hakemuksen liitteet and toggle all
  Toggle rollup  Hakemuksen liitteet
  Rollup closed  Hakemuksen liitteet
  Scroll and click test id  toggle-all-accordions
  Rollup open  Hakemuksen liitteet
  Rollup open  Osapuolet
  Rollup open  Pääpiirustukset
  Rollup open  Muut suunnitelmat
  Rollup open  Asuinkerrostalon tai rivitalon rakentaminen

Accordion with only not needed is neutral
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Rollup neutral  Muut suunnitelmat
  Click not needed  paapiirustus.pohjapiirustus
  Click not needed  paapiirustus.asemapiirros
  Rollup neutral  Pääpiirustukset
  Rollup neutral  Asuinkerrostalon tai rivitalon rakentaminen
  Click not needed  paapiirustus.pohjapiirustus

Sonja adds another Pääpiirustus
  Upload attachment  ${PNG_TESTFILE_PATH}  Julkisivupiirustus  Hello  Asuinkerrostalon tai rivitalon rakentaminen

Pääpiirrustukset accordion still rejected since Pohjapiirustus not uploaded
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen
  Rollup rejected  Pääpiirustukset

Hiding not needed does not affect states
  Scroll and click test id  needed-filter-label
  Scroll and click test id  needed-filter-label
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen
  Rollup rejected  Pääpiirustukset
  Wait until  Element should not be visible  jquery=rollup-status-button[data-test-name='Muut suunnitelmat'] button.rollup-button

Making Pohjapiirustus not needed approves Pääpiirustukset
  Rollup rejected  Pääpiirustukset
  Click not needed  paapiirustus.pohjapiirustus
  Rollup approved  Pääpiirustukset
  Rollup approved  Asuinkerrostalon tai rivitalon rakentaminen

Hiding not needed again does not affect states
  Scroll and click test id  needed-filter-label
  Scroll and click test id  needed-filter-label
  Rollup approved  Pääpiirustukset
  Rollup approved  Asuinkerrostalon tai rivitalon rakentaminen

Sonja adds and approves Väestonsuojasuunnitelma
  Scroll and click test id  notNeeded-filter-label
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Add attachment file  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']  ${PNG_TESTFILE_PATH}
  Return to application

Operation rollups are now approved
  Rollup approved  Pääpiirustukset
  Rollup approved  Muut suunnitelmat
  Rollup approved  Asuinkerrostalon tai rivitalon rakentaminen

Sonja rejects Väestonsuojasuunnitelma
  Reject row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']
  Rollup approved  Pääpiirustukset
  Rollup rejected  Muut suunnitelmat
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen

Sonja removes Väestonsuojasuunnitelma
  Remove row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']
  Rollup approved  Pääpiirustukset
  Wait until  Element should not be visible  jquery=rollup-status-button[data-test-name='Muut suunnitelmat'] button.rollup-button
  Rollup approved  Asuinkerrostalon tai rivitalon rakentaminen
  [Teardown]  Logout
