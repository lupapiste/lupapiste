*** Settings ***

Documentation  Attachment archivability status shown in the attachments table
Suite Setup    Apply minimal fixture now
Resource       ../../common_resource.robot
Variables      variables.py

*** Variables ***

${appname}        Alexandria
${propertyid}     753-423-2-41
${not-validated}  Liitteen arkistokelpoisuutta ei ole tarkastettu.
${invalid}        Tiedostotyyppi ei sovellu pysyvään säilytykseen
${permanent-archive-disabled}  Kunnan PDF/A-konversio ei ollut käytettävissä tiedoston lisäyshetkellä.

*** Test Cases ***

# --------------------
# Applicant
# --------------------

Pena logs in and creates application
  Pena logs in
  Create application the fast way  ${appname}  ${propertyid}  pientalo

Pena adds PDF attachment
  Open tab  attachments
  Upload attachment  ${PDF_TESTFILE_PATH}  Hulevesisuunnitelma  Water  Rakennuspaikka

No archive error marked
  No archive error  erityissuunnitelmat.hulevesisuunnitelma
  [Teardown]  Logout

# --------------------
# Admin admin
# --------------------

Admin logs in and edits Sipoo
  Admin edits Sipoo

Admin enables permanent archive for Sipoo
  Wait until  Element should be visible  permanentArchiveEnabled
  Select checkbox  permanentArchiveEnabled
  [Teardown]  Logout

# --------------------
# Applicant
# --------------------

Pena logs in again
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments

Attachment now has archive error
  Archive error  erityissuunnitelmat.hulevesisuunnitelma  ${permanent-archive-disabled}

Pena adds PDF attachment that becomes archivable
  Upload attachment  ${PDF_TESTFILE_PATH}  Johtokartta  Map  ${EMPTY}
  No archive error  ennakkoluvat_ja_lausunnot.johtokartta

Pena adds PNG attachment and it is now archivable
  Upload attachment  ${PNG_TESTFILE_PATH}  Suunnittelutarveratkaisu  Plan  ${EMPTY}
  No archive error   ennakkoluvat_ja_lausunnot.suunnittelutarveratkaisu
  [Teardown]  Logout

# --------------------
# Admin admin
# --------------------

Admin logs in and edits Sipoo again
  Admin edits Sipoo

Admin disables permanent archive for Sipoo
  Wait until  Element should be visible  permanentArchiveEnabled
  Unselect checkbox  permanentArchiveEnabled
  [Teardown]  Logout

# --------------------
# Applicant
# --------------------

Pena logs in once more
  Pena logs in
  Open application  ${appname}  ${propertyid}
  Open tab  attachments

No archive problems
  Wait until  Element should not be visible  jquery=i.archive.problem
  [Teardown]  Logout


*** Keywords ***

Icon selector
  [Arguments]  ${type}
  Set Suite Variable  ${icon-selector}  tr[data-test-type='${type}'] i[data-test-id=archive-problem-icon]

Text selector
  [Arguments]  ${type}
  Set Suite Variable  ${text-selector}  tr[data-test-type='${type}'] i[data-test-id=archive-problem-icon] span[data-test-id=archive-problem-text]

No archive error
  [Arguments]  ${type}
  Icon selector  ${type}
  Wait until  Element should not be visible  jquery=${icon-selector}

Archive error
  [Arguments]  ${type}  ${title}
  Icon selector  ${type}
  Mouse over  jquery=${icon-selector}
  Text selector  ${type}
  Wait until  Element should be visible  jquery=${text-selector}
  Element should contain  jquery=${text-selector}  ${title}

Admin edits Sipoo
  SolitaAdmin logs in
  Go to page  organizations
  Fill test id  organization-search-term  753-r
  Scroll and Click test id  edit-organization-753-R
