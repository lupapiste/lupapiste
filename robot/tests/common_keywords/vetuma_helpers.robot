*** Keywords ***
Cancel via vetuma
  [Arguments]  ${initButton}=vetuma-init
  Wait Until  Click element   ${initButton}
  Wait Until  Click link  << Palaa palveluun
  Wait Until  Click button  Palaa palveluun

Authenticate via Osuuspankki via Vetuma
  [Arguments]  ${initButton}
  Wait Until  Element should be visible  ${initButton}
  Wait Until  Click element   ${initButton}
  Wait Until  Element Should Be Visible  xpath=//img[@alt='Pankkitunnistus']
  Click element  xpath=//img[@alt='Pankkitunnistus']
  Wait Until  Element Should Be Visible  xpath=//a[@class='osuuspankki']
  Click element  xpath=//a[@class='osuuspankki']
  Wait Until  Element Should Be Visible  xpath=//input[@class='login']
  Input text     xpath=//input[@class='login']  123456
  Input text     xpath=//input[@type='PASSWORD']  7890
  Click button   xpath=//input[@name='ktunn']
  Wait Until  Element Should Be Visible  xpath=//input[@name='avainluku']
  Input text     xpath=//input[@name='avainluku']  1234
  Click button   xpath=//input[@name='avainl']
  Wait Until  Element Should Be Visible  xpath=//input[@name='act_hyvaksy']
  Click button   xpath=//input[@name='act_hyvaksy']
  Wait Until  Element Should Be Visible  xpath=//a[contains(text(),'Palaa palveluntarjoajan sivulle')]
  Click link     xpath=//a[contains(text(),'Palaa palveluntarjoajan sivulle')]
  Wait Until  Element Should Be Visible  xpath=//button[@type='submit']
  Click element  xpath=//button[@type='submit']

Authenticate via Nordea via Vetuma
  [Arguments]  ${initButton}=vetuma-init
  Wait Until  Click element   ${initButton}
  Wait Until  Element Should Be Visible  xpath=//img[@alt='Pankkitunnistus']
  Click element  xpath=//img[@alt='Pankkitunnistus']
  Wait Until  Element Should Be Visible  xpath=//a[@class='nordea']
  Click element  xpath=//a[@class='nordea']
  Wait Until  Element Should Be Visible  xpath=//input[@name='Ok']
  Click element  xpath=//input[@name='Ok']
  Wait Until  Element Should Be Visible  xpath=//input[@type='submit']
  Click element  xpath=//input[@type='submit']
  Wait Until  Element Should Be Visible  xpath=//button[@type='submit']
  Click element  xpath=//button[@type='submit']

Start Vetuma authentication but cancel via Nordea
  [Arguments]  ${initButton}=vetuma-init
  Wait Until  Click element   ${initButton}
  Wait Until  Element Should Be Visible  xpath=//a[@title='<< Palaa palveluun: Lupapiste']
  Click element  xpath=//a[@title='<< Palaa palveluun: Lupapiste']
  Wait Until  Element Should Be Visible  xpath=//button[@title='Palaa palveluun: Lupapiste']
  Click element  xpath=//button[@title='Palaa palveluun: Lupapiste']
