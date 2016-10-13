*** Settings ***

Documentation  Attachment rollup states
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Resource       attachment_resource.robot
Variables      variables.py


*** Test Cases ***

Sonja logs in and creates application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Rock'n'Roll${secs}
  Sonja logs in
  Create application with state   ${appname}  753-416-25-30  kerrostalo-rivitalo  open

Sonja goes to attachments tab
  Open tab  attachments
  Rollup rejected  Pääpiirustukset
  Rollup rejected  Muut suunnitelmat
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen

Accordion with only not needed is neutral
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Rollup neutral  Muut suunnitelmat
  Click not needed  paapiirustus.pohjapiirustus
  Rollup neutral  Pääpiirustukset
  Rollup neutral  Asuinkerrostalon tai rivitalon rakentaminen
  Click not needed  paapiirustus.pohjapiirustus

Sonja adds another Pääpiirustus
  Add attachment  application  ${PNG_TESTFILE_PATH}  Hello  paapiirustus.julkisivupiirustus  Asuinkerrostalon tai rivitalon rakentaminen
  Return to application

Sonja approves Julkisivupiirustus
  Approve row  tr[data-test-type='paapiirustus.julkisivupiirustus']

Making Pohjapiirustus not needed approves Pääpiirustukset
  Rollup rejected  Pääpiirustukset
  Click not needed  paapiirustus.pohjapiirustus
  Rollup approved  Pääpiirustukset
  Rollup neutral  Asuinkerrostalon tai rivitalon rakentaminen

Hiding not needed does not affect states
  Scroll and click test id  needed-filter-label
  Scroll and click test id  needed-filter-label
  Rollup neutral  Asuinkerrostalon tai rivitalon rakentaminen
  Wait until  Element should not be visible  jquery=rollup-status-button[data-test-name='Muut suunnitelmat'] button.rollup-button

Sonja adds and approves Väestonsuojasuunnitelma
  Scroll and click test id  notNeeded-filter-label
  Click not needed  pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
  Add attachment file  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']  ${PNG_TESTFILE_PATH}
  Return to application
  Approve row  tr[data-test-type='pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma']

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
