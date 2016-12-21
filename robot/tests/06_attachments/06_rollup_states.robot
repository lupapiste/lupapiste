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
  Upload attachment  ${PNG_TESTFILE_PATH}  Julkisivupiirustus  Hello  Asuinkerrostalon tai rivitalon rakentaminen

Sonja approves Julkisivupiirustus
  Wait Until  Element should be visible  jquery=tr[data-test-type='paapiirustus.julkisivupiirustus']
  Approve row  tr[data-test-type='paapiirustus.julkisivupiirustus']

Pääpiirrustukset accordion still rejected since Pohjapiirustus not uploaded
  Rollup rejected  Asuinkerrostalon tai rivitalon rakentaminen
  Rollup rejected  Pääpiirustukset
  Rollup neutral  Muut suunnitelmat

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
