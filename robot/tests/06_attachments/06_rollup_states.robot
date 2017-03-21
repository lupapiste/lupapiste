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
  Rollup rejected  PÄÄPIIRUSTUKSET
  Rollup rejected  MUUT SUUNNITELMAT
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Every accordion is open
  Rollup open  HAKEMUKSEN LIITTEET
  Rollup open  OSAPUOLET
  Rollup open  PÄÄPIIRUSTUKSET
  Rollup open  MUUT SUUNNITELMAT
  Rollup open  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Close Muut suunnitelmat
  Toggle rollup  MUUT SUUNNITELMAT
  Rollup closed  MUUT SUUNNITELMAT

Toggle all opens all
  Scroll and click test id  toggle-all-accordions
  Rollup open  HAKEMUKSEN LIITTEET
  Rollup open  OSAPUOLET
  Rollup open  PÄÄPIIRUSTUKSET
  Rollup open  MUUT SUUNNITELMAT
  Rollup open  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Toggle all closes all
  Scroll and click test id  toggle-all-accordions
  Rollup closed  HAKEMUKSEN LIITTEET
  Rollup closed  OSAPUOLET
  Rollup closed  PÄÄPIIRUSTUKSET
  Rollup closed  MUUT SUUNNITELMAT
  Rollup closed  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Scroll and click test id  toggle-all-accordions

Close Osapuolet and toggle all
  Toggle rollup  MUUT SUUNNITELMAT
  Rollup closed  MUUT SUUNNITELMAT
  Scroll and click test id  toggle-all-accordions
  Rollup open  HAKEMUKSEN LIITTEET
  Rollup open  OSAPUOLET
  Rollup open  PÄÄPIIRUSTUKSET
  Rollup open  MUUT SUUNNITELMAT
  Rollup open  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Close Hakemuksen liitteet and toggle all
  Toggle rollup  HAKEMUKSEN LIITTEET
  Rollup closed  HAKEMUKSEN LIITTEET
  Scroll and click test id  toggle-all-accordions
  Rollup open  HAKEMUKSEN LIITTEET
  Rollup open  OSAPUOLET
  Rollup open  PÄÄPIIRUSTUKSET
  Rollup open  MUUT SUUNNITELMAT
  Rollup open  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Accordion with only not needed is neutral
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Rollup neutral  MUUT SUUNNITELMAT
  Click not needed  paapiirustus.pohjapiirustus
  Click not needed  paapiirustus.asemapiirros
  Rollup neutral  PÄÄPIIRUSTUKSET
  Rollup neutral  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Click not needed  paapiirustus.pohjapiirustus

Sonja adds another Pääpiirustus
  Upload attachment  ${PNG_TESTFILE_PATH}  Julkisivupiirustus  Hello  Asuinkerrostalon tai rivitalon rakentaminen

Pääpiirrustukset accordion still rejected since Pohjapiirustus not uploaded
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Rollup rejected  PÄÄPIIRUSTUKSET

Hiding not needed does not affect states
  Scroll and click test id  needed-filter-label
  Scroll and click test id  needed-filter-label
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  Rollup rejected  PÄÄPIIRUSTUKSET
  Wait until  Element should not be visible  jquery=rollup-status-button[data-test-name='Muut suunnitelmat'] button.rollup-button

Making Pohjapiirustus not needed approves Pääpiirustukset
  Rollup rejected  PÄÄPIIRUSTUKSET
  Click not needed  paapiirustus.pohjapiirustus
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup approved  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Hiding not needed again does not affect states
  Scroll and click test id  needed-filter-label
  Scroll and click test id  needed-filter-label
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup approved  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja adds and approves Väestonsuojasuunnitelma
  Scroll and click test id  notNeeded-filter-label
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Add attachment file  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']  ${PNG_TESTFILE_PATH}  VSS1234
  Click by test id  batch-ready

Operation rollups are now approved
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup approved  MUUT SUUNNITELMAT
  Rollup approved  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja rejects Väestonsuojasuunnitelma
  Reject row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']
  Rollup approved  PÄÄPIIRUSTUKSET
  Rollup rejected  MUUT SUUNNITELMAT
  Rollup rejected  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN

Sonja removes Väestonsuojasuunnitelma
  Remove row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']
  Rollup approved  PÄÄPIIRUSTUKSET
  Wait until  Element should not be visible  jquery=rollup-status-button[data-test-name='Muut suunnitelmat'] button.rollup-button
  Rollup approved  ASUINKERROSTALON TAI RIVITALON RAKENTAMINEN
  [Teardown]  Logout
