*** Settings ***

Documentation   Notice forms in application
Suite Setup     Apply notice-forms fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       ../39_pate/pate_resource.robot
Resource       ../38_handlers/handlers_resource.robot
Resource       ../35_assignments/assignments_common.robot
Resource       notice_forms_resource.robot
Variables      ../06_attachments/variables.py

*** Variables ***

${property-id}              753-416-25-41
${address}                  Rue de Notice Form
${pientalo}                 Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)
${pientalo-en}              Construction of a detached house (detached house with no more than two dwellings)
${pientalo-schema}          uusiRakennus
${pientalo-desc}            Home sweet home
${sauna}                    Vapaa-ajan asunnon, saunarakennuksen tai vierasmajan rakentaminen
${sauna-schema}             uusi-rakennus-ei-huoneistoa
${sauna-desc}               Poika saunoo!
${laajentaminen-branch}     Rakennuksen laajentaminen (kerrosala, kokonaisala ja/tai tilavuus kasvaa)
${laajentaminen}            Varaston, autotallin tai muun talousrakennuksen laajentaminen
${laajentaminen-schema}     rakennuksen-laajentaminen-ei-huoneistoja
${laajentaminen-desc}       Expanding universe

${building1}  ${pientalo} - ${pientalo-desc} - VTJ-PRT-1
${building1-en}  ${pientalo-en} - ${pientalo-desc} - VTJ-PRT-1
${building2}  ${sauna} - ${sauna-desc} - VTJ-PRT-2
${building3}  ${laajentaminen} - ${laajentaminen-desc} - VTJ-PRT-3

*** Test Cases ***

#-----------------------
# Pena
#-----------------------

Pena logs in and creates application
  Pena logs in
  Create application with state  ${address}  ${property-id}  pientalo  submitted
  Add operation description  ${pientalo-schema}  ${pientalo-desc}

Pena adds vapaa-ajan-asuinrakennus operation
  Add operation  ${sauna}
  Add operation description  ${sauna-schema}  ${sauna-desc}

Pena adds laajentaminen operation
  Add operation  ${laajentaminen}  ${laajentaminen-branch}
  Add operation description  ${laajentaminen-schema}  ${laajentaminen-desc}  True
  Sleep  1s  # Required for making sure that the description is saved
  [Teardown]  Logout

#-----------------------
# Sonja
#-----------------------

Sonja fetches verdict
  Sonja logs in
  Open application  ${address}  ${property-id}
  Open tab  verdict
  Fetch verdict

Fake buildings
  Fetch fake buildings and logout

#-----------------------
# Pena
#-----------------------

Pena can create only terrain and location forms
  Pena logs in
  Open application  ${address}  ${property-id}
  No such test id  new-construction-notice-form
  Wait test id visible  new-terrain-notice-form
  Wait test id visible  new-location-notice-form

Pena invites Mikko as the Vastaava työnjohtaja
  Click button  jquery=table.all-foremen-table tr[data-test-name='Vastaava työnjohtaja'] button:visible
  Input text  invite-foreman-email  mikko@example.com
  Select from list by value  foreman-role  vastaava työnjohtaja
  Click enabled by test id  application-invite-foreman
  Wait until  Click link  jquery=div.finished a.btn

No rollups
  No such test id  construction-notice-form-status-button-0
  No such test id  terrain-notice-form-status-button-0
  No such test id  location-notice-form-status-button-0

Construction form can now be created
  Wait test id visible  new-construction-notice-form
  Scroll and click test id  new-construction-notice-form
  Test id text is  new-notice-form-title  Aloittamisilmoitus
  Test id text is  new-notice-form-info  AVI-ohje
  Test id text is  new-notice-form-foreman-text-0  Ei nimeä, Vastaava työnjohtaja (Luonnos)
  Wait test id visible  new-notice-form-foreman-bad-0
  Element should be visible  jquery=div.foremen-warning
  Test id text is  new-notice-form-building-0-label  ${building1}
  Test id text is  new-notice-form-building-1-label  ${building2}
  Test id text is  new-notice-form-building-2-label  ${building3}
  Test id text is  new-notice-form-message  ${EMPTY}
  Test id disabled  new-notice-form-ok
  No such test id  new-construction-notice-form
  No such test id  new-terrain-notice-form
  No such test id  new-location-notice-form

Pena fills the form
  Toggle toggle  new-notice-form-building-0
  Test id disabled  new-notice-form-ok
  Fill test id  new-notice-form-message  I am ready to build!
  Test id enabled  new-notice-form-ok

Pena uploads attachment and submits the form
  Upload via button or link  new-notice-form-upload  ${TXT_TESTFILE_PATH}
  Click by test id  new-notice-form-ok

Form rollup is created
  Rollup is closed  construction  0
  Toggle rollup  construction  0
  Rollup is open  construction  0
  Form is open  construction  0
  Test id text is  construction-0-notice-form-building-0  ${building1}
  Test id text is  construction-0-notice-form-message  I am ready to build!
  Wait test id visible  construction-0-notice-form-attachment-0
  No such test id  construction-0-notice-form-reject-note
  No such test id  construction-0-notice-form-reject
  No such test id  construction-0-notice-form-approve
  No such test id  construction-form-assignment

The same building cannot be in the two open forms of the same type
  Click by test id  new-construction-notice-form
  Test id text is  new-notice-form-building-0-label  ${building2}
  Test id text is  new-notice-form-building-1-label  ${building3}

Pena submits another construction form for the remaining buildings
  Toggle toggle  new-notice-form-building-0
  Toggle toggle  new-notice-form-building-1
  Fill test id  new-notice-form-message  No more buildings.
  Click by test id  new-notice-form-ok

Another rollup is created
  Rollup is closed  construction  1
  Toggle rollup  construction  1
  Rollup is open  construction  1
  Form is open  construction  1
  Test id text is  construction-1-notice-form-building-0  ${building2}
  Test id text is  construction-1-notice-form-building-1  ${building3}
  Test id text is  construction-1-notice-form-message  No more buildings.
  No such test id  construction-1-notice-form-attachment-0

No available buildings, new construction form does not have buildings either
  Wait test id visible  new-construction-notice-form
  Click by test id  new-construction-notice-form
  Test id text is  new-notice-form-title  Aloittamisilmoitus
  Test id text is  new-notice-form-info  AVI-ohje
  Test id text is  new-notice-form-foreman-text-0  Ei nimeä, Vastaava työnjohtaja (Luonnos)
  Wait test id visible  new-notice-form-foreman-bad-0
  Element should be visible  jquery=div.foremen-warning
  No such test id  new-notice-form-buildings

Pena fills the form without attachments and submits
  Test id disabled  new-notice-form-ok
  Fill test id  new-notice-form-message  Start without buildings!
  Test id enabled  new-notice-form-ok
  Click by test id  new-notice-form-ok

Form rollup is created without buildings
  Rollup is closed  construction  2
  Toggle rollup  construction  2
  Rollup is open  construction  2
  Form is open  construction  2
  No such test id  construction-2-notice-form-buildings

Pena removes the latest construction form
  Click by test id  construction-2-notice-form-delete
  Confirm yes no dialog

Pena creates new terrain form
  Click by test id  new-terrain-notice-form
  Test id text is  new-notice-form-title  Maastoonmerkinnän tilaaminen
  Test id text is  new-notice-form-info  Maasto-ohje
  No such test id  new-notice-form-foreman-text-0
  No such test id  new-notice-form-foreman-bad-0
  Element should not be visible  jquery=div.foremen-warning
  Test id text is  new-notice-form-building-0-label  ${building1}
  Test id text is  new-notice-form-building-1-label  ${building2}
  Test id text is  new-notice-form-building-2-label  ${building3}
  Test id text is  new-notice-form-message  ${EMPTY}
  Test id disabled  new-notice-form-ok

  Toggle toggle  new-notice-form-building-0
  Fill test id  new-notice-form-message  Marking my territory.
  Upload via button or link  new-notice-form-upload  ${PNG_TESTFILE_PATH}

Customer name is prefilled and cannot be empty
  Check prefilled  new-notice-form-customer-name  Pena Panaani

Invalid email shows warning
  Check prefilled  new-notice-form-customer-email  pena@example.com
  Check warning  new-notice-form-customer-email  pena@example.com

Invalid phone number shows warning
  Check prefilled  new-notice-form-customer-email  pena@example.com
  Check warning  new-notice-form-customer-email  pena@example.com

Permit payer is selected and other fields are not visible
  Toggle selected  new-notice-form-permit-payer
  No such test id  new-notice-form-payer-name
  No such test id  new-notice-form-payer-identifier
  No such test id  new-notice-form-payer-street
  No such test id  new-notice-form-payer-zip
  No such test id  new-notice-form-payer-city

Unselecting permit payer shows other payer fields
  Toggle toggle  new-notice-form-permit-payer
  Wait test id visible  new-notice-form-payer-name
  Wait test id visible  new-notice-form-payer-identifier
  Wait test id visible  new-notice-form-payer-street
  Wait test id visible  new-notice-form-payer-zip
  Wait test id visible  new-notice-form-payer-city

Selecting permit payer again hides the fields
  Toggle toggle  new-notice-form-permit-payer
  No such test id  new-notice-form-payer-name
  No such test id  new-notice-form-payer-identifier
  No such test id  new-notice-form-payer-street
  No such test id  new-notice-form-payer-zip
  No such test id  new-notice-form-payer-city
  Toggle toggle  new-notice-form-permit-payer

Payer identifier is empty and OK disabled
  Test id input is  new-notice-form-payer-identifier  ${EMPTY}
  Test id disabled  new-notice-form-ok
  Fill test id  new-notice-form-payer-identifier  180287-877Y
  Test id enabled  new-notice-form-ok

Payer name is prefilled and cannot be empty
  Check prefilled  new-notice-form-payer-name  Pena Panaani
  Fill test id  new-notice-form-payer-name  Pena & co

Payer identifier can be either Finnish person or company id
  Check warning  new-notice-form-payer-identifier  280680-185H
  Check warning  new-notice-form-payer-identifier  8352885-4

Payer street is prefilled and cannot be empty
  Check prefilled  new-notice-form-payer-street  Paapankuja 12

Payer zip code shows warning
    Check prefilled  new-notice-form-payer-zip  10203
    Check warning  new-notice-form-payer-zip  12345

Payer city is prefilled and cannot be empty
  Check prefilled  new-notice-form-payer-city  Piippola

Pena submits terrain notice form
  Click by test id  new-notice-form-ok

Terrain form rollup is created
  Rollup is closed  terrain  0
  Toggle rollup  terrain  0
  Rollup is open  terrain  0
  Form is open  terrain  0
  Test id text is  terrain-0-notice-form-building-0  ${building1}
  Test id text is  terrain-0-notice-form-message  Marking my territory.
  Test id text is  terrain-0-notice-form-customer  Pena Panaani, pena@example.com, 010 203 0405
  Test id text is  terrain-0-notice-form-payer  Pena & co, Y-tunnus 8352885-4\nPaapankuja 12\n12345 Piippola
  Wait test id visible  terrain-0-notice-form-attachment-0
  Wait test id visible  new-location-notice-form
  No such test id  terrain-form-assignment
  [Teardown]  Logout

#-----------------------
# Mikko (foreman)
#-----------------------

Mikko fills his information and submits the foreman application
  Mikko logs in
  Wait test id visible  open-application-button
  Click link  jquery=a[data-test-id=open-application-button]:last
  Confirm yes no dialog
  Open tab  parties
  Select from list by label  permitSubtypeSelect  Ilmoitus
  Click by test id  fill-info-button
  Submit application
  [Teardown]  Logout

#-----------------------
# Sonja
#-----------------------

Sonja logs in and sees the form assignments in the application
  Sonja logs in
  Open application  ${address}  ${property-id}
  Test id text is  construction-form-assignment  2 avointa ilmoitusta aloitusvalmiudesta.
  Test id text is  terrain-form-assignment  Ronja Sibbo: Yksi avoin maastoonmerkinnän tilaus.
  Wait test id visible  new-construction-notice-form
  Wait test id visible  new-terrain-notice-form
  Wait test id visible  new-location-notice-form

Approve the first construction form
  Approve form  construction  0
  # Forms are sorted by status
  Form is approved  construction  1
  Element should be visible  jquery=button[data-test-id=construction-1-notice-form-approve].positive
  No such test id  construction-1-notice-form-delete
  Test id text is  construction-form-assignment  Yksi avoin ilmoitus aloitusvalmiudesta.
  Wait test id visible  new-construction-notice-form

The form attachment is also approved
  Open tab  attachments
  Element should not be visible  jquery=automatic-assignments
  jQuery should match X times  tr[data-test-type='muut.muu'][data-test-state='ok'] span[data-test-id=attachment-contents]:contains(Ilmoitus aloitusvalmiudesta)  1

The terrain form attachment is in open state
  jQuery should match X times  tr[data-test-type='muut.muu'][data-test-state='requires_authority_action'] span[data-test-id=attachment-contents]:contains(Maastoonmerkinnän tilaaminen)  1
  Open tab  tasks

Sonja makes Laura the AVI handler
  Scroll and click test id  edit-handlers
  Click by test id  add-handler
  handlers_resource.Edit handler  0  Laskuttaja Laura  AVI-Käsittelijä
  Save indicated
  Handler is  0  Laskuttaja Laura  AVI-Käsittelijä
  Scroll to top
  Click by test id  edit-handlers-back

Laura is now the construction assignment recipient
  Test id text is  construction-form-assignment  Laura Laskuttaja: Yksi avoin ilmoitus aloitusvalmiudesta.

Sonja starts rejecting the approved construction form, but cancels
  Open rollup  construction  1
  Click by test id  construction-1-notice-form-reject
  Test id input is  construction-1-notice-form-reject-input  ${EMPTY}
  Fill test id  construction-1-notice-form-reject-input  Bad form
  Click by test id  construction-1-notice-form-reject-cancel
  Form is approved  construction  1
  Element should be visible  jquery=button[data-test-id=construction-1-notice-form-approve].positive.no-events

Sonja rejects the form for sure this time
  Click by test id  construction-1-notice-form-reject
  Test id text is  construction-1-notice-form-reject-input  ${EMPTY}
  Fill test id  construction-1-notice-form-reject-input  Basd form
  Click by test id  construction-1-notice-form-reject-button
  Form is rejected  construction  1
  Element should not be visible  jquery=button[data-test-id=construction-1-notice-form-approve].positive.no-events
  Element should be visible  jquery=button[data-test-id=construction-1-notice-form-reject].negative
  No such test id  construction-1-notice-form-delete
  Test id text is  construction-1-notice-form-reject-note  Basd form

Sonja rejects the form again
  Sleep  1s  # Make sure that DOM is refreshed
  Click by test id  construction-1-notice-form-reject
  Test id input is  construction-1-notice-form-reject-input  Basd form
  Fill test id  construction-1-notice-form-reject-input  Bad form
  Click by test id  construction-1-notice-form-reject-button
  # some stale element exceptions.. wait a little bit
  Sleep  1s
  Form is rejected  construction  1
  Element should not be visible  jquery=button[data-test-id=construction-1-notice-form-approve].positive.no-events
  Element should be visible  jquery=button[data-test-id=construction-1-notice-form-reject].negative
  Test id text is  construction-1-notice-form-reject-note  Bad form

The form attachment is also rejected
  Open tab  attachments
  Element should not be visible  jquery=automatic-assignments
  jQuery should match X times  tr[data-test-type='muut.muu'][data-test-state='requires_user_action'] span[data-test-id=attachment-contents]:contains(Ilmoitus aloitusvalmiudesta)  1

Sonja approves the foreman application
  Scroll and click test id  test-application-app-linking-to-us
  Open tab  requiredFieldSummary
  Wait test id visible  approve-application-summaryTab
  Click enabled by test id  approve-application-summaryTab
  Wait until  Application state should be  acknowledged
  Click by test id  test-application-link-permit-lupapistetunnus

Sonja creates a construction form
  Wait test id visible  new-construction-notice-form
  Click by test id  new-construction-notice-form
  Test id text is  new-notice-form-title  Aloittamisilmoitus
  Test id text is  new-notice-form-info  AVI-ohje
  Test id text is  new-notice-form-foreman-text-0  Mikko Intonen, Vastaava työnjohtaja (Ilmoitus hyväksytty)
  Element should not be visible  jquery=div.foremen-warning
  Test id text is  new-notice-form-building-0-label  ${building1}
  No such test id  new-notice-form-building-1-label
  No such test id  new-notice-form-building-2-label
  Test id text is  new-notice-form-message  ${EMPTY}
  Test id disabled  new-notice-form-ok
  No such test id  new-construction-notice-form
  No such test id  new-terrain-notice-form
  No such test id  new-location-notice-form
  Fill test id  new-notice-form-message  Authority construction notice.
  Test id enabled  new-notice-form-ok
  Toggle toggle  new-notice-form-building-0
  Upload via button or link  new-notice-form-upload  ${TXT_TESTFILE_PATH}
  Click by test id  new-notice-form-ok

Deleting form updates assignment
  Test id text is  construction-form-assignment  Laura Laskuttaja: 2 avointa ilmoitusta aloitusvalmiudesta.
  Toggle rollup  construction  1
  Test id text is  construction-1-notice-form-message  Authority construction notice.
  Click by test id  construction-1-notice-form-delete
  Confirm yes no dialog
  No such test id  construction-1-notice-form-delete
  Test id text is  construction-form-assignment  Laura Laskuttaja: Yksi avoin ilmoitus aloitusvalmiudesta.

... and deletes the form attachment
  Open tab  attachments
  jQuery should match X times  tr[data-test-type='muut.muu'][data-test-state='requires_user_action'] span[data-test-id=attachment-contents]:contains(Ilmoitus aloitusvalmiudesta)  1
  Element should not be visible  tr[data-test-type='muut.muu'][data-test-state='requires_authority_action'] span[data-test-id=attachment-contents]:contains(Ilmoitus aloitusvalmiudesta)
  [Teardown]  Logout

#----------------------------------------------
# Laura (construction assignment recipient)
#----------------------------------------------

Laura logs in and checks her assignments
  Laura logs in
  Open assignments search
  Click by test id  search-tab-automatic
  jQuery should match X times  tr.assignment-row  1
  Element text should be  jquery=tr.assignment-row td[data-test-col-name=address]  Rue de Notice Form, Sipoo

Clicking assignment takes Laura to tasks tab
  Click Element  jquery=tr.assignment-row
  Test id text is  construction-form-assignment  Laura Laskuttaja: Yksi avoin ilmoitus aloitusvalmiudesta.

Laura approves form and the assignment is cleared
  Approve form  construction  0
  No such test id  construction-form-assignment
  [Teardown]  Logout

#--------------------------
# Ronja (terrain handler)
#--------------------------

Ronja approves her assignment without visiting the application first
  Ronja logs in
  Open assignments search
  Click by test id  search-tab-automatic
  jQuery should match X times  tr.assignment-row  1
  Click by test id  complete-assignment

Ronja goes to the application and approves the terrain form
  Open applications search
  Open application  ${address}  ${property-id}
  No such test id  terrain-form-assignment
  Approve form  terrain  0
  [Teardown]  Logout

Pena logs in and changes language to English
  Pena logs in
  Language to  EN

Pena opens application and creates location form
  Open application  ${address}  ${property-id}
  Click by test id  new-location-notice-form
  Test id text is  new-notice-form-info  Location help
  No such test id  new-notice-form-foreman-text-0
  No such test id  new-notice-form-foreman-bad-0
  Element should not be visible  jquery=div.foremen-warning
  Test id text is  new-notice-form-building-0-label  ${building1-en}
  No such test id  new-notice-form-building-1-label
  No such test id  new-notice-form-building-2-label
  Test id text is  new-notice-form-message  ${EMPTY}
  Test id disabled  new-notice-form-ok

  Toggle toggle  new-notice-form-building-0
  Fill test id  new-notice-form-message  Location, location, location

Hidden fields do not affect validation
  Toggle toggle  new-notice-form-permit-payer
  Fill test id  new-notice-form-payer-identifier  bad
  Fill test id  new-notice-form-payer-zip  bad
  Fill test id  new-notice-form-payer-name  ${EMPTY}
  Test id disabled  new-notice-form-ok
  Toggle toggle  new-notice-form-permit-payer
  Test id enabled  new-notice-form-ok

Pena submits the location form
  Click by test id  new-notice-form-ok
  Wait test id visible  new-construction-notice-form
  Wait test id visible  new-terrain-notice-form
  Wait test id visible  new-location-notice-form

Payer information is correct in the roll up
  Rollup is closed  location  0
  Toggle rollup  location  0
  Rollup is open  location  0
  Form is open  location  0
  Test id text is  location-0-notice-form-building-0  ${building1-en}
  Test id text is  location-0-notice-form-message  Location, location, location
  Test id text is  location-0-notice-form-customer  Pena Panaani, pena@example.com, 010 203 0405
  Test id text is  location-0-notice-form-payer  Payer is the same as the permit payer
  No such test id  location-0-notice-form-attachment-0
  No such test id  location-form-assignment
  [Teardown]  Logout

#-----------------------
# Sonja
#-----------------------

Sonja logs in and checks form assignments
  Sonja logs in
  Open application  ${address}  ${property-id}
  Test id text is  location-form-assignment  Yksi avoin sijaintikatselmuksen tilaus.
  No such test id  construction-form-assignment
  No such test id  terrain-form-assignment
  [Teardown]  Logout

Frontend sanity check
  [Tags]  fail
  # gettinc errors: ["Missing localization key" "help.undefined.conversationDesc"]
  # ["Missing localization key" "help.undefined.PartiesDesc"]
  # ["Missing localization key" "help.undefined.applicationInfoDesc"]
  There are no frontend errors


*** Keywords ***

Rollup is closed
  [Arguments]  ${type}  ${index}
  No such test id  ${type}-${index}-notice-form-message

Toggle rollup
  [Arguments]  ${type}  ${index}
  Scroll and click test id  ${type}-${index}-notice-form-status-button

Rollup is open
  [Arguments]  ${type}  ${index}
  Wait test id visible  ${type}-${index}-notice-form-message

Form is approved
  [Arguments]  ${type}  ${index}
  Wait until element is visible  jquery=rollup-status-button[data-test-id=${type}-${index}-notice-form-status-button] span.rollup-status__icon.lupicon-check

Open rollup
  [Arguments]  ${type}  ${index}
  ${closed}=  Run keyword and return status  Rollup is closed  ${type}  ${index}
  Run keyword if  ${closed}  Toggle rollup  ${type}  ${index}

Approve form
  [Arguments]  ${type}  ${index}
  Open rollup  ${type}  ${index}
  Click by test id  ${type}-${index}-notice-form-approve

Form is rejected
  [Arguments]  ${type}  ${index}
  Wait until  Element attribute value should be  //rollup-status-button[@data-test-id="${type}-${index}-notice-form-status-button"]//rollup-button  data-rollup-status  requires_user_action

Form is open
  [Arguments]  ${type}  ${index}
  Element should not be visible  jquery=rollup-status-button[data-test-id=${type}-${index}-notice-form-status-button] span.rollup__status-icon.lupicon-check
  Element should not be visible  jquery=rollup-status-button[data-test-id=${type}-${index}-notice-form-status-button] span.rollup__status-icon.lupicon-circle-attention


Check warning
  [Arguments]  ${tid}  ${good}
  Test id enabled  new-notice-form-ok
  No such test id  ${tid}-warning
  Fill test id  ${tid}  Bad
  Wait test id visible  ${tid}-warning
  Test id disabled  new-notice-form-ok
  Fill test id  ${tid}  ${good}
  No such test id  ${tid}-warning
  Test id enabled  new-notice-form-ok

Check prefilled
  [Arguments]  ${tid}  ${text}
  Test id input is  ${tid}  ${text}
  Test id enabled  new-notice-form-ok
  Fill test id  ${tid}  ${EMPTY}
  # No warning for empty field
  No such test id  ${tid}-warning
  Test id disabled  new-notice-form-ok
  Fill test id  ${tid}  ${text}
  Test id enabled  new-notice-form-ok
