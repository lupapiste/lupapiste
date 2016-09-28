*** Settings ***

Documentation   3D map support
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot

*** Test Cases ***

Pena logs in and creates application
  Set Suite Variable  ${appname}  Three Dee 3
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo

Open 3D map button is visible
  Wait test id visible  open-3d-map

Open the 3D map view
  Scroll and click test id  open-3d-map
  Select window  new
  Wait until  Title should be  3D Map View
  Close window
  Select window  main

Pena creates inforequest
  Set Suite Variable  ${infoname}  Info Dee 3
  Set Suite Variable  ${infoid}  753-416-25-30
  Create inforequest the fast way  ${infoname}  360603.153  6734222.95  ${infoid}  pientalo  Hen youyisi!

Open 3D map button is visible for inforequest too
  Test id visible  open-3d-map
  [Teardown]  Logout

Solita admin edits organization 753-R
  SolitaAdmin logs in
  Click link  Organisaatiot
  Fill test id  organization-search-term  753-r
  Scroll and click test id  organization-search
  Scroll and Click test id  edit-organization-753-R   

3D map server backend must be https
  Fill test id  3d-map-url  http://example.org
  Scroll and click test id  3d-map-send
  Negative indicator should be visible

Admin edits 3D maps
  Wait until  Checkbox should be selected  3d-map-enabled
  Test id enabled  3d-map-send  
  Unselect checkbox  3d-map-enabled
  Test id disabled  3d-map-send
  [Teardown]  Logout

Pena logs in and no longer sees 3D map button in the application
  Pena logs in
  Open application  ${appname}  ${propertyid}
  No such test id  open-3d-map

... nor in the inforequest
  Open inforequest  ${infoname}  ${infoid}
  No such test id  open-3d-map
  [Teardown]  Logout


