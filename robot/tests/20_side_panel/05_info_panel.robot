*** Settings ***

Documentation   Info panel
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        info_resource.robot
Resource        ../10_authority_admin/authority_admin_resource.robot
Resource       ../29_guests/guest_resource.robot
Resource       ../13_statements/statement_resource.robot

*** Variables ***
${appname}      Linklater
${propertyid}   753-416-88-88
${rakval}       http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta
${sipoo}        http://sipoo.fi
${oopis}        http://oopis.if
${example}      http://www.example.org
${bar}          https://bar.baz
${twitter}      http://twitter.com
${beijing}      http://beijing.cn
${shanghai}     http://shanghai.cn  
${tampere}      http://tampere.fi
${turku}        http://turku.fi
${appname2}     Retalknil
${propertyid2}  753-416-99-99

*** Test Cases ***

# ------------------------
# Pena
# ------------------------

Pena logs in and creates application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

Pena sees star and opens info panel
  Star is shown
  Open info panel

Pena sees two organization links no info links
  Check organization link  0  Sipoo  ${sipoo}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  No such test id  organization-link-2

Pena toggles side panel and stars are now gone
  Close info panel
  Star is not shown
  Open info panel
  Check organization link  0  Sipoo  ${sipoo}
  Check organization link  1  Rakennusvalvonta  ${rakval}

Pena cannot add links
  No such test id  add-info-link

Pena submits application
  Submit application
  [Teardown]  Logout  

# ------------------------
# Sonja
# ------------------------

Sonja logs in and also sees new organization links
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${sipoo}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  No such test id  organization-link-2

Sonja adds info link
  Add info link  0  Example  www.example.org
  Save link  0
  Check info link  0  Example  ${example}  false  true
  
Sonja start editing link but cancels
  Edit info link  0  Example  ${example}
  Fill link  0  Foo  bar.baz
  Cancel link  0
  Check info link  0  Example  ${example}  false  true

Sonja edits and saves
  Edit info link  0  Example  ${example}
  Fill link  0  Foo  ${bar}
  Save link  0
  Check info link  0  Foo  ${bar}  false  true

Sonja adds link but cancels
  Add info link  1  Hii  hii.hoo
  Cancel link  1
  No such test id  view-link-edit-1
  
Sonja deletes link
  Delete info link  0
  No such test id  view-link-edit-0
  [Teardown]  Logout


# ------------------------
# Sipoo
# ------------------------

Sipoo admin modifies organization links
  Sipoo logs in
  Go to page  backends
  Update link  Sipoo  ${oopis}
  Add link  Titityy  ${twitter}
  [Teardown]  Logout

# ------------------------
# Ronja
# ------------------------

Ronja logs in and sees many stars
  Ronja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  Check organization link  2  Titityy  ${twitter}  true
  No such test id  view-link-0

Ronja adds info link
  Add info link  0  Bar  ${bar}
  Save link  0
  [Teardown]  Logout

# ------------------------
# Pena
# ------------------------

Pena logs in and sees new organization and Ronja's links
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}  true
  Check just link  0  Bar  ${bar}  true

Pena visits conversation panel and comes back
  Clear stars and close panel

Pena invites Mikko as guest
  Open tab  parties
  Invite application guest  mikko@example.com  Link guest
  [Teardown]  Logout

# ------------------------
# Sonja
# ------------------------

Sonja logs in and sees some new links
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  false
  Check organization link  2  Titityy  ${twitter}  true
  Check info link  0  Bar  ${bar}  true  true

Sonja edits Ronja's link
  Edit info link  0  Bar  ${bar}
  Fill link  0  Beijing  beijing.cn
  Save link  0
  Check info link  0  Beijing  ${beijing}  false  true  
  Close info panel
  Wait until  Star is not shown
  
Sonja invites Jussi as statement giver
  Open tab  statement
  Scroll and click test id  add-statement
  Invite 'manual' statement giver  1  Official  Jussi  jussi.viranomainen@tampere.fi  ${EMPTY}
  [Teardown]  Logout

# ------------------------
# Ronja
# ------------------------

Ronja logs in and sees Sonja's edit
  Ronja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  # Organization links are now old
  Check organization link  0  Sipoo  ${oopis}
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}
  Check info link  0  Beijing  ${beijing}  true  true

Ronja adds link
  Add info link  1  Shanghai  shanghai.cn
  Save link  1
  Check info link  1  Shanghai  ${shanghai}  false  true
  Close info panel

Ronja invites Teppo as statement giver
  Open tab  statement
  Scroll and click test id  add-statement
  Invite 'manual' statement giver  1  Laoban  Teppo  teppo@example.com  ${EMPTY}
  [Teardown]  Logout
  
# ------------------------
# Teppo (statement giver)
# ------------------------

Teppo logs in and sees stars
  Teppo logs in
  Later visit

Teppo cannot edit existing links
  Cannot edit links

Teppo adds link
  Add info link  2  Tampee  ${tampere}
  Save link  2
  Check info link  2  Tampee  ${tampere}  false  true

Teppo can edit his link
  [Tags]  fail
  Edit info link  2  Tampee  ${tampere}
  Fill link  2  Tampere  ${tampere}
  Save link  2
  Check info link  2  Tampere  ${tampere}  false  true

Teppo logs out
  Logout

# ------------------------
# Jussi (statement giver)
# ------------------------

Jussi logs in and sees stars
  Jussi logs in
  Later visit
  # Change city name and canEdit when statement giver edits work
  # Check info link  2  Tampere  ${tampere}  true  false
  Check info link  2  Tampee  ${tampere}  true  true  
  
Jussi cannot edit existing links
  [Tags]  fail
  Cannot edit links

Jussi adds link
  Add info link  3  Turu  ${turku}
  Save link  3
  Check info link  3  Turu  ${turku}  false  true

Jussi can edit his link
  [Tags]  fail
  Edit info link  3  Turu  ${turku}
  Fill link  3  Turku  ${turku}
  Save link  3
  Check info link  3  Turku  ${turku}  false  true
  [Teardown]  Logout

Jussi logs out
  Logout
  
# ------------------------
# Sonja
# ------------------------

Sonja logs in one more time and sees stars
  Sonja logs in
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}
  Check info link  0  Beijing  ${beijing}  false  true
  Check info link  1  Shanghai  ${shanghai}  true  true
  # Change city names when statement giver edits work
  # Check info link  2  Tampere  ${tampere}  true  true
  # Check info link  3  Turku  ${turku}  true  true
  Check info link  2  Tampee  ${tampere}  true  true
  Check info link  3  Turu  ${turku}  true  true

Sonja can edit statemen giver's links
  [Tags]  fail
  Edit info link  2  Tampere  ${tampere}  
  Fill link  2  Tampere  www.tampere.fi
  Save link  2
  Check info link  2  Tampere  http://www.tampere.fi  false  true
  Edit info link  3  Tukru  ${turku}
  Fill link  3  Turku  www.turku.fi
  Save link  3
  Check info link  3  Turku  http://www.turku.fi  true  true
  [Teardown]  Logout

Sonja can edit statemen giver's links (remove this when statement giver edits work)
  Edit info link  2  Tampee  ${tampere}  
  Fill link  2  Tampere  www.tampere.fi
  Save link  2
  Check info link  2  Tampere  http://www.tampere.fi  false  true
  Edit info link  3  Turu  ${turku}
  Fill link  3  Turku  www.turku.fi
  Save link  3
  Check info link  3  Turku  http://www.turku.fi  false  true
  [Teardown]  Logout

# ------------------------
# Mikko (guest)
# ------------------------

Mikko logs in and sees stars
  Mikko logs in
  Read only visit
  [Teardown]  Logout
    
# --------------------------
# Luukas (reader authority)
# --------------------------

Luukas logs in and sees stars
  Luukas logs in
  Read only visit
  [Teardown]  Logout

# --------------------------
# Pena
# --------------------------

Pena logs in and creates new application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}    kerrostalo-rivitalo

Organization links are old, there are no info links
  Star is not shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}
  Check organization link  1  Rakennusvalvonta  ${rakval}
  Check organization link  2  Titityy  ${twitter}
  No such test id  view-link-0
  No such test id  just-link-0
  Cannot edit anything
  [Teardown]  Logout
  

*** Keywords ***

Later visit
  Open application  ${appname}  ${propertyId}
  Star is shown
  Open info panel
  Check organization link  0  Sipoo  ${oopis}  true
  Check organization link  1  Rakennusvalvonta  ${rakval}  true
  Check organization link  2  Titityy  ${twitter}  true

Read only visit
  Later visit  
  Check just link  0  Beijing  ${beijing}  true
  Check just link  1  Shanghai  ${shanghai}  true
  Check just link  2  Tampere  http://www.tampere.fi  true
  Check just link  3  Turku  http://www.turku.fi  true
  Cannot edit anything
  Close info panel
  Open info panel
  No info panel stars
  
