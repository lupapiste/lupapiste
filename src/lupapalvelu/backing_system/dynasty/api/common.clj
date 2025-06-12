(ns lupapalvelu.backing-system.dynasty.api.common
  "Common Dynasty schemas

   Note: The quoted Finnish texts in the docstrings are sourced from
   the API specification. They are included to make it easier to find
   the appropriate schema when looking for a particular domain concept
   (like \"tehtäväluokka\") in Finnish."
  (:require [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema SchemaID
  "> Käytetyn asemointimallin ID, jos tyhjä niin käytetään oletusta. ID:n näkee asemointimalli-editorissa url:sta.

   The API provides no access to the schemas so we'll go with the defaults."
  ssc/UUIDStr)

(sc/defschema Owner
  "> Omistaja. Omistaja voi olla organisaatioyksikkö tai toimen/viranhaltija
   For example `\"ympäristöjohtaja\"`."
  (ssc/max-length-string 4000))

(sc/defschema PersonalData
  (sc/enum "Ei sisällä henkilötietoja"
           "Sisältää henkilötietoja"))

(sc/defschema MetaJson
  "> Jätä tämä tyhjäksi. Integraatioiden hankalampien ongelmaskenaarioiden kiertämiseen tarkoitettu tietue.
   > JSON-objekti tekstinä, joka mergetetään Dynastyn tietomalliin.
   > Tarvitaan jos halutaan asettaa dynastyn tarjoamien kenttien arvoja joita ei ole tarjottu tässä luokassa.

   The API is too optimistic; we actually need to provide several parameters using this escape hatch."
  {sc/Any sc/Any})

(sc/defschema TosClass
  "> TOS tehtäväluokka, muoto riippuu TOS-rakenteesta
   For example `\"02.02.02\"`"
  ssc/NonBlankStr)

(sc/defschema Guid
  ssc/UUIDStr)

(sc/defschema ContactType
  "> Kontaktin tyyppi"
  (sc/enum "Person" "Organisation"))

(sc/defschema ContactInfo
  "> Kontaktin osoite-tietoja"
  (st/optional-keys
    {:billing-address     ssc/NonBlankStr ; "Laskutusosoite: Osoite"
     :billing-city        ssc/NonBlankStr ; "Laskutusosoite: Kaupunki"
     :billing-country     ssc/NonBlankStr ; "Laskutusosoite: Maa"
     :billing-postal-code ssc/NonBlankStr ; "Laskutusosoite: Postikoodi"
     :city                ssc/NonBlankStr ; "Kaupunki"
     :country             ssc/NonBlankStr ; "Maa"
     :e-inv-address       ssc/NonBlankStr ; "Verkkolaskutusosoite: Verkkolaskuosoite"
     :e-inv-edi-code      ssc/NonBlankStr ; "Verkkolaskutusosoite: Välittäjätunnus"
     :e-inv-operator-code ssc/NonBlankStr ; "Verkkolaskutusosoite: Operaattoritunnus"
     :email               ssc/NonBlankStr ; "Sähköpostiosoite"
     :mobile-phone        ssc/NonBlankStr ; "Langaton puhelinnumero"
     :phone               ssc/NonBlankStr ; "Puhelinnumero"
     :postal-address      ssc/NonBlankStr ; "Postiosoite"
     :postal-code         ssc/NonBlankStr ; "Postikoodi"
     }))

(sc/defschema ContactRole
  "> Asiaosaisen tai yhteyshenkilön rooli asialla, tai asiakirjalla, roolit ovat ympäristökohtaisia.
   > Tällä arvolla haetaan 'sisältää'-haulla olemassaolevaa arvoa listoista ellei DontSearchRoleOptionValue=true käytetä.
   For example: `\"vireillepanija\"`"
  ssc/NonBlankStr)

(sc/defschema ContactCreateModel
  (st/merge
    {:type ContactType}
    (st/optional-keys
      {:person       (st/optional-keys
                       {:first-name ssc/NonBlankStr
                        :last-name  ssc/NonBlankStr
                        :ssn        ssc/NonBlankStr})
       :org          {:title    ssc/NonBlankStr
                      :trade-no ssc/NonBlankStr}
       :contact-info ContactInfo
       :role         ContactRole
       :channel      (sc/enum "NO_CHANNEL")})))

(sc/defschema ContactModel
  "A contact attached to an entity (a `Case` or a `Document`).
   Note that there are no APIs for modifying an existing contact."
  (st/merge
    ContactCreateModel
    {:guid Guid}))
