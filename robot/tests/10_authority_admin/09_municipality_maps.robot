*** Settings ***

Documentation  Authority admin edits organization map layers.
Suite Setup     Sipoo logs in
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Open integrations tab
  Go to page  backends

Setup
  # Bitte, see http://ows.terrestris.de/dienste.html for details
  Set Suite Variable  ${url}  http://ows.terrestris.de/osm/service
  Set Suite Variable  ${layer1}  OSM-WMS
  Set Suite Variable  ${layer2}  OSM-Overlay-WMS
  Set Suite Variable  ${layer3}  TOPO-WMS
  Set Suite Variable  ${layer4}  TOPO-OSM-WMS

Admin inputs bad server address
  No error
  No layers
  Input text with jQuery  [data-test-id=server-details-url]  foobar
  Click button  jquery=[data-test-id=server-details-send]
  Yes error
  No layers

Admin inputs good server address
  Input text with jQuery  [data-test-id=server-details-url]  ${url}
  Click button  jquery=[data-test-id=server-details-send]
  No error
  Yes layers

Admin selects fixed layers
  Select layer  0  ${layer1}
  Select layer  1  ${layer2}

Admin adds layer and fills its info
  Add layer
  Select layer  2  ${layer3}
  Name layer  2  Tianjin

Admin adds second (or rather fourth) layer and fills its info
  Add layer
  Select layer  3  ${layer4}
  Name layer  3  Beijing

Admin removes layer
  Remove layer  2

Admin reloads page and makes sure that layers are still present
  Reload page
  Wait until  Yes layers
  Check layer value  0  ${layer1}
  Check layer value  1  ${layer2}
  Check layer value  2  ${layer4}
  Check layer name  2  Beijing
  Element should not be visible  jquery=input[data-test-id=layer3-input]


*** Keywords ***

No error
  Wait until  element should not be visible  jquery=p.munimaps--error

Yes error
  Wait until  element should be visible  jquery=p.munimaps--error

No layers
  Wait until  Element should not be visible  jquery=table[data-test-id=layers-table]

Yes layers
  Wait until  Element should be visible  jquery=table[data-test-id=layers-table]

Select layer
  [Arguments]  ${index}  ${value}
  Select from list by value  jquery=select[data-test-id=layer${index}-select]  ${value}

Add layer
  Click button  jquery=[data-test-id=layers-add]

Name layer
  [Arguments]  ${index}  ${name}
  Input text with jQuery  [data-test-id=layer${index}-input]  ${name}

Remove layer
  [Arguments]  ${index}
  Click link  jquery=[data-test-id=layer${index}-remove]

Check layer value
  [Arguments]  ${index}  ${value}
  Wait until  List selection should be  jquery=select[data-test-id=layer${index}-select]  ${value}

Check layer name
  [Arguments]  ${index}  ${value}
  Wait until  Textfield value should be  jquery=input[data-test-id=layer${index}-input]  ${value}
