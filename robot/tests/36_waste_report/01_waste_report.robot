*** Settings ***

Documentation  Extended waste report
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo authority enables extended waste report
  Sipoo logs in
  Go to page  applications
  Checkbox wrapper not selected by test id  extended-construction-waste-report-enabled
  Click label by test id  extended-construction-waste-report-enabled-label
  Wait until  Positive indicator should be visible
  Checkbox wrapper selected by test id  extended-construction-waste-report-enabled
  Logout

Pena logs in and creates application
  Pena logs in
  Create application the fast way  Wasted  753-88-88-88  pientalo
  Wait test id visible  laajennettuRakennusjateselvitys-accordion-title-text

The other text field for mineral waste is not visible
  No such test id  'purkaminen.mineraalisenPurkujatteenKasittely.muuKasittelytapa'

Pena selects other for mineral waste
  Test id select is  'purkaminen.mineraalisenPurkujatteenKasittely.kasittelytapa'  ${EMPTY}
  Select from test id  'purkaminen.mineraalisenPurkujatteenKasittely.kasittelytapa'  muu
  Scroll to xpath  //*[@data-test-id="purkaminen.mineraalisenPurkujatteenKasittely.muuKasittelytapa"]
  Element should be visible by test id  purkaminen.mineraalisenPurkujatteenKasittely.muuKasittelytapa

Pena fills dangerous materials table first row
  Scroll to  table.muuJate-table
  Dangerous sum is  0 tonnia
  Dangerous input  0  12  kg
  Dangerous sum is  12 kg

... second row
  Dangerous add  1  2  kg
  Dangerous sum is  14 kg
  Dangerous unit  1  tonnia
  Dangerous sum is  2012 kg

... third row
  Dangerous add  2  0  ${EMPTY}
  Dangerous sum is  2012 kg
  Dangerous input  2  100  kg
  Dangerous sum is  2112 kg

Pena removes the second row
  Click element  jquery=tr[data-test-id='vaarallisetJatteet-row-1'] td.action-column i
  Confirm yes no dialog
  Dangerous sum is  112 kg

Pena copies the last row
  Click by test id  vaarallisetJatteet-copy-button
  Dangerous sum is  212 kg

Dangerous error: bad input
  Dangerous input  0  10s  kg
  Dangerous error

Dangerous error: no unit
  Dangerous input  0  1  kg
  Dangerous sum is  201 kg
  Select from test id  'rakennusJaPurkujate.vaarallisetJatteet.0.yksikko'  ${EMPTY}
  Dangerous error

Digging table
  Scroll to test id  sum-kaivettavaMaa-ainekset-hyodynnetaan

Selections survive reload
  Dig select  0  louhe
  Dig new row
  Dig select  1  jaykkaSavi
  Dig reload
  Dig select is  0  louhe
  Dig select is  1  jaykkaSavi

Remove second dig row
  Scroll to test id  ainekset-row-1
  Click element  jquery=tr[data-test-id=ainekset-row-1] td.action-column i
  Confirm yes no dialog
  No such test id  'kaivettavaMaa.ainekset.1.aines-group.aines'

Dig other shows text field
  No such test id  kaivettavaMaa.ainekset.0.aines-group.muu
  Dig select  0  muu
  Wait test id visible  kaivettavaMaa.ainekset.0.aines-group.muu

Other selection survives reload
  Dig reload
  Wait test id visible  kaivettavaMaa.ainekset.0.aines-group.muu

Pena fills the first dig row
  Dig input  0  4  5  9
  Dig footer sums  4  5  9

... second dig row
  Dig add  1  6.8  2,4  9.2
  Dig footer sums  10.8  7.4  18.2

... third dig row
  Dig add  2  1.226  2,411  3.64
  Dig footer sums  12.03  9.81  21.84

Dig errors
  Dig input  0  ${EMPTY}  4  4
  Dig footer sums  8.03  8.81  16.84
  Fill test id  kaivettavaMaa.ainekset.0.hyodynnetaan  foo
  Wait until  Element should be visible  jquery=tr[data-test-id=ainekset-row-0] i.calculation-error
  Wait test id visible  sum-kaivettavaMaa-ainekset-hyodynnetaan-error
  No such test id  sum-kaivettavaMaa-ainekset-poisajettavia-error
  Wait test id visible  sum-kaivettavaMaa-ainekset-yhteensa-error

No frontend errors
  There are no frontend errors

*** Keywords ***

Dangerous sum is
  [Arguments]  ${text}
  Test id text is  sum-rakennusJaPurkujate-vaarallisetJatteet-maara  ${text}

Dangerous error
  Scroll to  table.vaarallisetJatteet-table i.footer-sum--error
  Wait until  Element should be visible  jquery=table.vaarallisetJatteet-table i.footer-sum--error

Dangerous unit
  [Arguments]  ${index}  ${unit}
  Select from test id  'rakennusJaPurkujate.vaarallisetJatteet.${index}.yksikko'  ${unit}

Dangerous input
  [Arguments]  ${index}  ${amount}  ${unit}
  ${selector}=  Set variable  rakennusJaPurkujate.vaarallisetJatteet.${index}.maara
  Fill test id  ${selector}  ${amount}
  Dangerous unit  ${index}  ${unit}

Dangerous add
  [Arguments]  ${newIndex}  ${amount}  ${unit}
  Click by test id  vaarallisetJatteet-append-button
  Dangerous input  ${newIndex}  ${amount}  ${unit}

Dig input
  [Arguments]  ${index}  ${use}  ${discard}  ${sum}
  Fill test id  kaivettavaMaa.ainekset.${index}.hyodynnetaan  ${use}
  Fill test id  kaivettavaMaa.ainekset.${index}.poisajettavia  ${discard}
  Test id text is  kaivettavaMaa-ainekset-${index}-yhteensa  ${sum}

Dig footer sums
  [Arguments]  ${useSum}  ${discardSum}  ${total}
  Test id text is  sum-kaivettavaMaa-ainekset-hyodynnetaan  ${useSum} tonnia
  Test id text is  sum-kaivettavaMaa-ainekset-poisajettavia  ${discardSum} tonnia
  Test id text is  sum-kaivettavaMaa-ainekset-yhteensa  ${total}

Dig new row
  Scroll and click  button[data-test-id=ainekset-append-button]:first

Dig add
  [Arguments]  ${newIndex}  ${use}  ${discard}  ${sum}
  Dig new row
  Dig input  ${newIndex}  ${use}  ${discard}  ${sum}

Dig select
  [Arguments]  ${index}  ${option}
  Select from test id  'kaivettavaMaa.ainekset.${index}.aines-group.aines'  ${option}

Dig select is
  [Arguments]  ${index}  ${option}
  Test id select is  'kaivettavaMaa.ainekset.${index}.aines-group.aines'  ${option}

Dig reload
  Reload page
  Element should be visible by test id  laajennettuRakennusjateselvitys-accordion-title-text
  Kill dev-box
  Scroll to test id  sum-kaivettavaMaa-ainekset-hyodynnetaan
