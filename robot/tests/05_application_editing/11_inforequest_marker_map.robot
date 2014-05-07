*** Settings ***

Documentation   Behaving of the inforequest marker map
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Setting maps enabled for these tests
  Set integration proxy on

Mikko as applicant does not see the inforequest marker map
  #TODO
  Mikko logs in
  #User role should be  applicant
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-first}  ir-first-${secs}
  Set Suite Variable  ${propertyId-first}  433-405-3-427
  #Set Suite Variable  ${address-first}  Holvitie 4
  Create inforequest the fast way  ${inforequest-first}  433  ${propertyId-first}  asuinrakennus  Jiihaa-first
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Mikko Intonen
  Element should not be visible  //div[@id='inforequest-marker-map']
  Element should not be visible  //div[@id='marker-map-contents']
  Logout

Arto (authority) sees Mikko's new inforequest as a marker on map
  Arto logs in
  Open inforequest  ${inforequest-first}  ${propertyId-first}
  Wait until  Element should be visible  //div[@id='inforequest-marker-map']
  Total marker count is  1
  Marker count by type is  1  0  0  0

Arto clicks on the marker and the marker contents window is opened
  # TODO: Talle parempi matchaus jotenkin?
  #Click element  xpath=//div[@id='inforequest-marker-map']/image
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should be visible  //div[@id='marker-map-contents']
  Total Inforequest info card count on map is  1
  Total inforequest comment count on the info cards is  1

Arto clicks on the marker, and the opened marker contents window has correct info about the inforequest
  ${current-app-id} =  Get Text  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']
  Verify info card by app id  ${current-app-id}  ${inforequest-first}  Asuinrakennuksen rakentaminen

Arto clicks on the marker again and the marker contents window is closed
  #Click element  xpath=//div[@id='inforequest-marker-map']/image
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should not be visible  //div[@id='marker-map-contents']

Arto adds comment and it is visible in the marker contents window
  Add comment and Mark answered  Oletko miettinyt askeesia?
  #Click element  xpath=//div[@id='inforequest-marker-map']/image
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should not be visible  //div[@id='marker-map-contents']
  Total Inforequest info card count on map is  1
  Total inforequest comment count on the info cards is  2
  #Click element  xpath=//div[@id='inforequest-marker-map']/image
  Click element  xpath=//div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]
  Wait until  Element should not be visible  //div[@id='marker-map-contents']

Arto creates three new inforequests, and checks types of the created markers
  #
  # TODO: Testaa tama!
  #

  # 1 with same location, 1 with same operation and 1 other

  # Same location
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-same-loc}  ir-same-loc-${secs}
  Set Suite Variable  ${propertyId-same-loc}  433-405-3-427
  #Set Suite Variable  ${address-same-loc}  Holvitie 4
  Create inforequest the fast way  ${inforequest-same-loc}  433  ${propertyId-same-loc}  asuinrakennus  Jiihaa-loc

  # Same operation
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-same-op}  ir-same-op-${secs}
  Set Suite Variable  ${propertyId-same-op}  433-405-57-8
  #Set Suite Variable  ${address-same-op}  Tupatie 3
  Create inforequest the fast way  ${inforequest-same-op}  433  ${propertyId-same-op}  asuinrakennus  Jiihaa-op

  # Other
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-other}  ir-other-${secs}
  Set Suite Variable  ${propertyId-other}  433-405-78-0
  #Set Suite Variable  ${address-other}  Kauppatie 4
  Create inforequest the fast way  ${inforequest-other}  433  ${propertyId-other}  vapaa-ajan-asuinrakennus  Jiihaa-other


  Total Inforequest info card count on map is  4
  Total inforequest comment count on the info cards is  5
  Total marker count is  3
  Marker count by type is  0  1  1  1

Follow the link displayed in an info card
  ${current-app-id} =  Get Text  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']
  Click element  xpath=//div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']/a[@data-test-id='inforequest-link']
  Wait until  Element text should not be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']  ${current-app-id}

  Logout

Setting maps enabled again after the tests
  Set integration proxy off


*** Keywords ***

Total marker count is
  [Arguments]  ${amount}
  #Xpath Should Match X Times  //div[@id='inforequest-marker-map']//image[contains(@id, 'OpenLayers_Geometry_Point_')]  ${amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_')]  ${amount}

Marker count by type is
  [Arguments]  ${current-location-amount}  ${same-operation-amount}  ${others-amount}  ${cluster-amount}

  # TODO: Miksi tassa //image... ei matchaa mihinkaan?
  # TODO: Miksi "xlink:href" aiheuttaa virheen "... contains unresolvable namespaces"?

  #Xpath Should Match X Times  //div[@id='inforequest-marker-map']//image[@xlink:href='/img/map-marker.png']  ${current-location-amount}
  #Xpath Should Match X Times  //div[@id='inforequest-marker-map']//image[@xlink:href='/img/map-marker-red.png']  ${same-operation-amount}
  #Xpath Should Match X Times  //div[@id='inforequest-marker-map']//image[@xlink:href='/img/map-marker-green.png']  ${others-amount}
  #Xpath Should Match X Times  //div[@id='inforequest-marker-map']//image[@xlink:href='/img/map-marker-group.png']  ${cluster-amount}

  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/img/map-marker.png']  ${current-location-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/img/map-marker-red.png']  ${same-operation-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/img/map-marker-green.png']  ${others-amount}
  Xpath Should Match X Times  //div[@id='inforequest-marker-map']//*[contains(@id, 'OpenLayers_Geometry_Point_') and @*='/img/map-marker-group.png']  ${cluster-amount}

Total Inforequest info card count on map is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //div[@id='marker-map-contents']//div[@class='inforequest-card']  ${amount}

Verify info card by app id
  [Arguments]  ${current-app-id}  ${title}  ${operation}
  Element text should be  //div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']/h2[@data-test-id='inforequest-title']  ${title}
  Element text should be  //div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']/h3[@data-test-id='inforequest-operation']  ${operation}
  Element text should be  //div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']/div[@class='inforequest-comment']/blockquote  Jiihaa
  Element should not be visible  //div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']/a[@data-test-id='inforequest-link']

Comment count on the info card of current inforequest is
  [Arguments]  ${amount}
  ${current-app-id} =  Get Text  //section[@id='inforequest']//span[@data-test-id='inforequest-application-id']
  Xpath Should Match X Times  //div[@id='marker-map-contents']//div[@data-test-id='inforequest-card-${current-app-id}']//div[@class='inforequest-comment']  ${amount}

Total inforequest comment count on the info cards is
  [Arguments]  ${amount}
  Xpath Should Match X Times  //div[@id='marker-map-contents']//div[@class='inforequest-card']//div[@class='inforequest-comment']  ${amount}


