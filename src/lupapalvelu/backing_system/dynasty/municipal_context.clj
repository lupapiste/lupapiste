(ns lupapalvelu.backing-system.dynasty.municipal-context
  "Municipalities have unique requirements regarding the D10 parameters,
   such as the contents of the `:meta-json` field."
  (:require [clojure.set :as set]
            [lupapalvelu.attachment.metadata :as attachment-metadata]
            [lupapalvelu.backing-system.dynasty.api.common :as common-api]
            [lupapalvelu.backing-system.dynasty.api.document :as document-api]
            [lupapalvelu.backing-system.schema :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.permit :as permit]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defmulti permit-config-for
  "Return the permit config map for a organization"
  {:private true}
  identity)

(defmethod permit-config-for :default
  [org-id]
  (log/warnf "No municipal permit config for '%s'!" org-id)
  {})

(defn- config->permit-types
  [permit-config]
  (-> permit-config
      keys
      set
      (disj ::default)))

(defn- permit-types-for
  "Return a set of supported permit types"
  [org-id]
  (-> org-id permit-config-for config->permit-types))

(defn supported-permit-types
  "Set of permit types that are supported both by the given organization and D10
  integration."
  [{:keys [id scope]}]
  (->> (map :permitType scope)
       set
       (set/intersection (permit-types-for id))))

(defn- config->operations
  [permit-config]
  (->> permit-config
       vals
       (mapcat keys)
       (remove qualified-keyword?)
       set))

(defn operations-for
  "Returns the supported operations (set of keywords) for an application"
  [{org-id :organization}]
  (-> org-id permit-config-for config->operations))

(defn operation-supported?
  "Check if the primary operation of the application is supported"
  [{:keys [primaryOperation] :as application}]
  (let [op (-> primaryOperation :name keyword)]
    (contains? (operations-for application) op)))

(sc/defschema Options
  (st/optional-keys
    {;; "Tehtäväluokka"
     ::tos-class                   common-api/TosClass
     ;; "Asiakirjan otsikko"
     ::document-title              document-api/DocumentTitle
     ;; Note that this is NOT the same as `document-api/DocumentType`,
     ;; this goes in "metaJSON.DocumentType"
     ::document-type               ssc/NonBlankStr
     ;; "Asiakirjatyypin tarkenne"
     ::document-tos-title          document-api/TosTitle
     ;; Public attachments seem to always use the same TOS Title as the main document
     ;; so we don't need a specific `::attachment-tos-title` option here
     ::attachment-tos-title-secret document-api/TosTitle
     ;; Unfortunately we also need to specify phase & subphase on a per permit type
     ;; or per operation level. The UI only provides a configurable (sub)phase for all
     ;; messages. The UI values will be ignored if these keys are specified.
     ::document-phase              ssc/NonBlankStr
     ::document-subphase           ssc/NonBlankStr
     ::arrival-type                ssc/NonBlankStr}))

(sc/defschema PermitConfig
  {(sc/cond-pre (sc/eq ::default)
                permit/PermitType) (st/merge Options
                                             {(sc/optional-key ::default) Options
                                              sc/Keyword                  Options})})

(def valid-config! (sc/validator PermitConfig))

(defn- get-value
  [permit-config application field]
  (let [permit-type (permit/permit-type application)
        operation   (-> application :primaryOperation :name keyword)]
    (or (get-in permit-config [permit-type operation field])
        (get-in permit-config [permit-type ::default field])
        (get-in permit-config [::default field]))))

(defn- operation-name
  [application]
  (->> application
       :primaryOperation
       :name
       (str "operations.")
       i18n/loc))

(defn common-case-fn
  [permit-config]
  (fn [{{:keys [firstName lastName]} :creator
        :as                          application}
       {{:keys [owner
                case-base-id
                organization-name]} :options}]
    {:title        (operation-name application)
     :tos-class    (get-value permit-config application ::tos-class)
     :owner        owner
     :organisation {:name organization-name}
     :case-base    {:id case-base-id}
     ;; At the moment the `Initiator` field is mandatory, but it's not
     ;; visible to the user and in later Dynasty versions this requirement will
     ;; be removed
     :meta-json    {:initiator (format "%s %s" firstName lastName)}}))

(defn common-contact-fn
  [_permit-config]
  (fn [_applicant
       {{:keys [applicant-role]} :options}]
    {:role applicant-role}))

(defn common-document-fn
  [permit-config]
  (fn [{:keys [created] :as application}
       {{:keys [handler-id
                document-phase
                document-subphase
                organization-hierarchy-id]} :options}]
    (let [;; Either from the hard-coded config or from the integration config (from the UI)
          document-phase    (or (get-value permit-config application ::document-phase)
                                document-phase)
          document-subphase (or (get-value permit-config application ::document-subphase)
                                (ss/blank-as-nil document-subphase))
          arrival-type      (get-value permit-config application ::arrival-type)]
      (cond-> {:title                   (get-value permit-config application ::document-title)
               :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector (cond-> [{:title document-phase}]
                                                                                                  document-subphase (conj {:title document-subphase}))}}
                                         :tos-title      (get-value permit-config application ::document-tos-title)}
               :meta-json               {:document-type             (get-value permit-config application ::document-type)
                                         :handler-id                handler-id
                                         :organisation-hierarchy-id organization-hierarchy-id
                                         :received-date             (date/iso-date created :local)}}
        arrival-type (assoc :arrival-type arrival-type)))))

(defn common-attachment-fn
  [permit-config]
  (fn [application
       {{:keys [created]} :latestVersion
        :as               attachment}
       {{:keys [owner
                handler-id
                document-type
                document-phase
                document-subphase
                organization-hierarchy-id]} :options}]
    (let [;; Either from the hard-coded config or from the integration config (from the UI)
          document-phase    (or (get-value permit-config application ::document-phase)
                                document-phase)
          document-subphase (or (get-value permit-config application ::document-subphase)
                                (ss/blank-as-nil document-subphase))
          secret?           (not (attachment-metadata/public-attachment? attachment))
          arrival-type      (get-value permit-config application ::arrival-type)]
      (cond-> {:owner                   owner
               :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector (cond-> [{:title document-phase}]
                                                                                                  document-subphase (conj {:title document-subphase}))}}
                                         :tos-title      (or (when secret?
                                                               (get-value permit-config application ::attachment-tos-title-secret))
                                                             (get-value permit-config application ::document-tos-title))}
               :meta-json               {:document-type             document-type
                                         :handler-id                handler-id
                                         :organisation-hierarchy-id organization-hierarchy-id
                                         :received-date             (date/iso-date created :local)}}
        arrival-type (assoc :arrival-type arrival-type)))))

(sc/defschema MunicipalContext
  {::application   {sc/Any sc/Any}
   ::attachment-fn (sc/pred fn? "callback")
   ::case-fn       (sc/pred fn? "callback")
   ::contact-fn    (sc/pred fn? "callback")
   ::document-fn   (sc/pred fn? "callback")
   ::integration   schemas/CommonIntegrationConfig
   ::lang          (apply sc/enum i18n/languages)
   ::now           date/ZonedArg})

(sc/defn build-municipal-context :- MunicipalContext
  "Build the municipal context used to create Dynasty messages."
  {:always-validate true}
  [integration {org-id :organization :as application} lang now]
  (let [permit-config (permit-config-for org-id)]
    (when-not (seq permit-config)
      (fail! :error.integration.dynasty-not-enabled))
    {::application   application
     ::attachment-fn (common-attachment-fn permit-config)
     ::case-fn       (common-case-fn permit-config)
     ::contact-fn    (common-contact-fn permit-config)
     ::document-fn   (common-document-fn permit-config)
     ::integration   integration
     ::lang          lang
     ::now           now}))


;;; Customization per municipality

;; Could/should this config be stored in TOJ and fetched here?
;; => Not really, this needs to work even without the TOJ.

(def ^:private permit-config-kuopio
  (-> {::default   {::document-tos-title "Hakemus liitteineen"
                    ::document-type      "Hakemus"
                    ;; "0" => "Sähköisesti (sähköposti, asiointi)"
                    ::arrival-type       "0"}
       permit/MAL  {::default {::tos-class "11.02.00"}
                    ;; Maa-ainesten ottaminen (Maa-aineslupa, MAL 4 §)
                    :maa-aineslupa
                    {::document-title "Maa-aineslupahakemus"}
                    ;; Hakemus luvan jatkamiseksi (MAL 21 §)
                    :maa-aineslupa-jatkoaika
                    {::document-title "Hakemus maa-ainesluvan jatkamiseksi"}}
       permit/VVVL {::default
                    {::tos-class                   "11.02.01"
                     ::attachment-tos-title-secret "Hakemuksen liite (salassa pidettävä)"}
                    ;; Vapautus vesijohtoon liittymisestä (VHL 11 §)
                    :vvvl-vesijohdosta
                    {::document-title "Vapautus vesijohtoon liittymisestä"}
                    ;; Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä (VHL 11 §)
                    :vvvl-vesijohdosta-ja-viemarista
                    {::document-title "Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä"}
                    ;; Vapautus jätevesiviemäriin liittymisestä (VHL 11 §)
                    :vvvl-viemarista
                    {::document-title "Vapautus jätevesiviemäriin liittymisestä"}
                    ;; Note: Kuopio does not support :vvvl-hulevesiviemarista
                    }
       permit/YI   {::default
                    {::document-type "Ilmoitus"}
                    ;; Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa
                    :jatteiden-hyodyntaminen-maarakentamisessa
                    {::tos-class          "11.01.04"
                     ::document-title     "Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa"
                     ::document-tos-title "Hakemus liitteineen (poikkeaminen)"
                     ::document-type      "Hakemus"}
                    ;; Kirjallinen vireillepano (YSL 186 §, JL 134 §, VL 11:2 ja 14:14)
                    :kirjallinen-vireillepano
                    {::tos-class          "11.01.04"
                     ::document-title     "Kirjallinen vireillepano (YSL 186 §, JL 134 §, VL 11:2 ja 14:14)"
                     ::document-tos-title "Ympäristöhaittailmoitus liitteineen"}
                    ;; Ilmoitus lannan levittämisestä poikkeustilanteessa (10 § 2 mom.)
                    :lannan-levittamisesta-poikkeustilanteessa
                    {::tos-class      "11.02.07"
                     ::document-title "Ilmoitus lannan levittämisestä poikkeustilanteessa"
                     ::document-type  "Hakemus"}
                    ;; Leirintäalueilmoitus
                    :leirintaalueilmoitus
                    {::tos-class          "11.02.06"
                     ::document-title     "Leirintäalueilmoitus"
                     ::document-tos-title "Leirintäalueilmoitus liitteineen"}
                    ;; Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)
                    :meluilmoitus
                    {::tos-class          "11.01.02"
                     ::document-title     "Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)"
                     ::document-tos-title "Ilmoitus liitteineen"}
                    ;; Rekisteröinti-ilmoitus (YSL 116§)
                    :rekisterointi-ilmoitus
                    {::tos-class          "11.01.03"
                     ::document-title     "Rekisteröinti-ilmoitus (YSL 116§)"
                     ::document-tos-title "Ilmoitus liitteineen"}
                    ;; Yleinen ilmoitus (YSL 115 a §)
                    :yleinen-ilmoitus
                    {::tos-class          "11.01.01"
                     ::document-title     "Ympäristösuojelulain mukainen yleinen ilmoitusmenettely"
                     ::document-tos-title "Ilmoitus liitteineen"}}
       permit/YL   {::default
                    {::tos-class                   "11.01.00"
                     ::attachment-tos-title-secret "Hakemuksen liite (salassa pidettävä)"}
                    ;; Ympäristöluvanvaraisen toiminnan jatkaminen
                    :yl-olemassa-oleva-toiminta
                    {::document-title     "Ympäristöluvan muutoshakemus"
                     ::document-tos-title "Muutoshakemus liitteineen"}
                    ;; Ympäristöluvanvaraisen toiminnan muutos
                    :yl-toiminnan-muutos
                    {::document-title     "Ympäristöluvan muutoshakemus"
                     ::document-tos-title "Muutoshakemus liitteineen"}
                    ;; Uusi ympäristöluvanvarainen toiminta (YSL 27 §)
                    :yl-uusi-toiminta
                    {::document-title     "Ympäristölupahakemus"
                     ::document-tos-title "Hakemus liitteineen (myös yhteislupa)"}}
       permit/YM   {;; Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)
                    :ilmoitus-poikkeuksellisesta-tilanteesta
                    {::tos-class          "11.01.02"
                     ::document-title     "Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)"
                     ::document-tos-title "Ilmoitus liitteineen"
                     ::document-type      "Ilmoitus"}
                    ;; Ilmoitus jätteen ammattimaisesta keräystoiminnasta (JL 100 §)
                    :jatteen-keraystoiminta
                    {::tos-class          "11.02.03"
                     ::document-title     "Ilmoitus jätteen ammattimaisesta keräystoiminnasta"
                     ::document-tos-title "Ilmoitus liitteineen"
                     ::document-type      "Ilmoitus"}
                    ;; Käytöstä poistetun öljy- tai kemikaalisäiliön jättäminen maaperään
                    :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan
                    {::tos-class          "11.01.04"
                     ::document-title     "Käytöstä poistetun öljy- tai kemikaalisäiliön jättäminen maaperään"
                     ::document-tos-title "Hakemus liitteineen (poikkeaminen)"}
                    ;; Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)
                    :koeluontoinen-toiminta
                    {::tos-class          "11.01.02"
                     ::document-title     "Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)"
                     ::document-tos-title "Ilmoitus liitteineen"
                     ::document-type      "Ilmoitus"}
                    ;; Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus) (9§)
                    :lannan-varastointi-v2
                    {::tos-class      "11.02.07"
                     ::document-title "Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus)"}
                    ;; Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus) (9§)
                    :lannan-varastointi-v3
                    {::tos-class      "11.02.07"
                     ::document-title "Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus)"}
                    ;; Maastoliikennelain (1710/1995) 30 §:n mukainen lupa kilpailuihin ja harjoituksiin
                    :maastoliikennelaki-kilpailut-ja-harjoitukset
                    {::tos-class      "11.02.02"
                     ::document-title "Maastoliikennelain mukainen lupa"}
                    ;; Luonnonmuistomerkin rauhoittaminen (LSL 26 §)
                    :muistomerkin-rauhoittaminen
                    {::tos-class      "11.02.05"
                     ::document-title "Luonnonmuistomerkin rauhoittaminen"}
                    ;; Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)
                    :talousjatevesien-kasittelysta-poikkeaminen
                    {::tos-class          "11.01.04"
                     ::document-title     "Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)"
                     ::document-tos-title "Hakemus liitteineen (poikkeaminen)"}
                    ;; Vesiliikennelain 106§:n mukainen lupa kilpailuihin ja harjoituksiin
                    :vesiliikennelaki-kilpailut-ja-harjoitukset
                    {::tos-class      "11.02.02"
                     ::document-title "Vesiliikennelain mukainen lupa"}
                    ;; Maa-ainesluvan ja ympäristöluvan yhteiskäsittelyhakemus (MAL 4a § ja YSL 47a §)
                    :yhteiskasittely-maa-aines-ja-ymparistoluvalle
                    {::tos-class                   "11.01.00"
                     ::document-title              "Ympäristö- ja maa-ainesluvan yhteislupahakemus"
                     ::document-tos-title          "Hakemus liitteineen (myös yhteislupa)"
                     ::attachment-tos-title-secret "Hakemuksen liite (salassa pidettävä)"}
                    ;; Ympäristöluvan selventäminen (YSL 92 §)
                    :ymparistoluvan-selventaminen
                    {::tos-class                   "11.01.00"
                     ::document-title              "Ympäristöluvan muutoshakemus"
                     ::document-tos-title          "Muutoshakemus liitteineen"
                     ::attachment-tos-title-secret "Hakemuksen liite (salassa pidettävä)"}
                    ;; Ilmoitus maa-ainesten kotitarveotosta (MAL 23a §)
                    :maa-ainesten-kotitarveotto
                    {::tos-class      "11.02.00"
                     ::document-title "Ilmoitus maa-ainesten kotitarveotosta"}}}
      valid-config!))

(defmethod permit-config-for "297-YMP"
  [_]
  permit-config-kuopio)

(def ^:private permit-config-salo
  (-> {::default   {::attachment-tos-title-secret "Hakemuksen liite"
                    ::document-phase              "Vireillepano/-tulo"
                    ::document-subphase           "Ilmoituksen vastaanottaminen"
                    ::document-tos-title          "Hakemus liitteineen"
                    ::document-type               "Hakemus"
                    ;; "0" => "Sähköisesti (sähköposti, asiointi)"
                    ::arrival-type                "0"}
       permit/MAL  {::default                {::document-subphase "Hakemuksen vastaanottaminen"
                                              ::document-title    "Maa-ainesluvan käsittely"
                                              ::tos-class         "10.03.00.05.00"}
                    ;; Maa-ainesten ottaminen (Maa-aineslupa, MAL 4 §)
                    :maa-aineslupa           {}
                    ;; Hakemus luvan jatkamiseksi (MAL 21 §)
                    :maa-aineslupa-jatkoaika {}}
       permit/VVVL {::default                        {::document-subphase "Hakemuksen vastaanottaminen"
                                                      ::document-title    "Vesihuoltolain mukainen verkostoon liittämisen valvonta"
                                                      ::tos-class         "11.01.05.00"}
                    ;; Vapautus vesijohtoon liittymisestä (VHL 11 §)
                    :vvvl-vesijohdosta               {}
                    ;; Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä (VHL 11 §)
                    :vvvl-vesijohdosta-ja-viemarista {}
                    ;; Vapautus jätevesiviemäriin liittymisestä (VHL 11 §)
                    :vvvl-viemarista                 {}
                    ;; Vapautus hulevesiviemäriin liittymisestä (VHL 17c §)
                    :vvvl-hulevesiviemarista         {}}
       permit/YI   {::default                                  {::document-type "Ilmoitus"}
                    ;; Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa
                    :jatteiden-hyodyntaminen-maarakentamisessa {::document-subphase  "Hyödyntämisilmoituksen vastaanottaminen"
                                                                ::document-title     "Jätteen hyödyntäminen maarakentamisessa"
                                                                ::document-tos-title "Jätteen hyödyntämisilmoitus liitteineen"
                                                                ::tos-class          "11.03.07.01"}
                    ;; Kirjallinen vireillepano (YSL 186 §, JL 134 §, VL 11:2 ja 14:14) => Not in use

                    ;; Ilmoitus lannan levittämisestä poikkeustilanteessa (10 § 2 mom.)
                    :lannan-levittamisesta-poikkeustilanteessa {::document-title "Nitriittiasetuksen valvonta"
                                                                ::tos-class      "11.03.03.00"}
                    ;; Leirintäalueilmoitus
                    :leirintaalueilmoitus                      {::document-title     "Leirintäalueet"
                                                                ::document-tos-title "Ilmoitus liitteineen"
                                                                ::tos-class          "11.03.08"}
                    ;; Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)
                    :meluilmoitus                              {::attachment-tos-title-secret "Ilmoituksen liite, liikesalaisuuksia sisältävä"
                                                                ::document-title              "Ilmoitusmenettely"
                                                                ::document-tos-title          "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
                                                                ::tos-class                   "11.01.00.02"}
                    ;; Rekisteröinti-ilmoitus (YSL 116§)
                    :rekisterointi-ilmoitus                    {::document-subphase  "Rekisteröinti-ilmoituksen vastaanottaminen"
                                                                ::document-title     "Rekisteröintimenettely"
                                                                ::document-tos-title "Rekisteröinti-ilmoitus liitteineen"
                                                                ::tos-class          "11.01.00.03"}
                    ;; Yleinen ilmoitus (YSL 115 a §)
                    :yleinen-ilmoitus                          {::attachment-tos-title-secret "Ilmoituksen liite, liikesalaisuuksia sisältävä"
                                                                ::document-title              "Yleinen ilmoitusmenettely"
                                                                ::document-tos-title          "Ilmoitus liitteineen"
                                                                ::tos-class                   "11.01.00.07"}}
       permit/YL   {::default                   {::attachment-tos-title-secret "Hakemuksen liite, liikesalaisuuksia sisältävä"
                                                 ::document-subphase           "Hakemuksen vastaanottaminen"
                                                 ::document-title              "Ympäristöluvan käsittely"
                                                 ::document-tos-title          "Ympäristölupahakemus liitteineen"
                                                 ::tos-class                   "11.01.00.00.01"}
                    ;; Ympäristöluvanvaraisen toiminnan jatkaminen
                    :yl-olemassa-oleva-toiminta {}
                    ;; Ympäristöluvanvaraisen toiminnan muutos
                    :yl-toiminnan-muutos        {}
                    ;; Uusi ympäristöluvanvarainen toiminta (YSL 27 §)
                    :yl-uusi-toiminta           {}}
       permit/YM   {::default                                                         {::attachment-tos-title-secret "Hakemuksen liite, liikesalaisuuksia sisältävä"
                                                                                       ::document-type               "Ilmoitus"}
                    ;; Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)
                    :ilmoitus-poikkeuksellisesta-tilanteesta                          {::attachment-tos-title-secret "Ilmoituksen liite, liikesalaisuuksia sisältävä"
                                                                                       ::document-title              "Ilmoitusmenettely"
                                                                                       ::document-tos-title          "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
                                                                                       ::tos-class                   "11.01.00.02"}
                    ;; Ilmoitus jätteen ammattimaisesta keräystoiminnasta (JL 100 §)
                    :jatteen-keraystoiminta                                           {::document-subphase  "Ilmoituksen vastaanottaminen ammattimaisesta keräystoiminnasta"
                                                                                       ::document-title     "Jätteen ammattimainen keräystoiminta"
                                                                                       ::document-tos-title "Ilmoitus ammattimaisesta keräystoiminnasta liitteineen"
                                                                                       ::tos-class          "11.01.06.02"}
                    ;; Käytöstä poistetun öljy- tai kemikaalisäiliön jättäminen maaperään
                    :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan {::document-subphase  "Poikkeamishakemuksen vastaanottaminen"
                                                                                       ::document-title     "Poikkeaminen ympäristönsuojelumääräyksistä"
                                                                                       ::document-tos-title "Poikkeamishakemus liitteineen"
                                                                                       ::document-type      "Hakemus"
                                                                                       ::tos-class          "11.01.00.04"}
                    ;; Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)
                    :koeluontoinen-toiminta                                           {::attachment-tos-title-secret "Ilmoituksen liite, liikesalaisuuksia sisältävä"
                                                                                       ::document-title              "Ilmoitusmenettely"
                                                                                       ::document-tos-title          "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
                                                                                       ::tos-class                   "11.01.00.02"}
                    ;; Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus) (9§)
                    :lannan-varastointi-v2                                            {::document-title     "Nitriittiasetuksen valvonta"
                                                                                       ::document-tos-title "Aumausilmoitus liitteineen"
                                                                                       ::tos-class          "11.03.03.00"}
                    ;; Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus) (9§)
                    :lannan-varastointi-v3                                            {::document-title     "Nitriittiasetuksen valvonta"
                                                                                       ::document-tos-title "Aumausilmoitus liitteineen"
                                                                                       ::tos-class          "11.03.03.00"}
                    ;; Maastoliikennelain (1710/1995) 30 §:n mukainen lupa kilpailuihin ja harjoituksiin
                    :maastoliikennelaki-kilpailut-ja-harjoitukset                     {::document-subphase "Hakemuksen vastaanottaminen"
                                                                                       ::document-title    "Kunnan päätökset"
                                                                                       ::tos-class         "11.01.03.00"}
                    ;; Luonnonmuistomerkin rauhoittaminen (LSL 26 §)
                    :muistomerkin-rauhoittaminen                                      {::document-subphase  "Hakemus luonnonmuistomerkin rauhoittamiseksi tai rauhoituksen lakkauttamiseksi"
                                                                                       ::document-title     "Luonnonmuistomerkin rauhoitus"
                                                                                       ::document-tos-title "Hakemus liitteineen"
                                                                                       ::document-type      "Hakemus"
                                                                                       ::tos-class          "11.01.04.00"}
                    ;; Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)
                    :talousjatevesien-kasittelysta-poikkeaminen                       {::document-subphase           "Hakemuksen vastaanottaminen"
                                                                                       ::attachment-tos-title-secret "Salaisia henkilötietoja sisältävä liite"
                                                                                       ::document-title              "Talousjätevesien käsittelyvaatimuksista poikkeaminen"
                                                                                       ::tos-class                   "11.01.00.05"}
                    ;; Vesiliikennelain 106§:n mukainen lupa kilpailuihin ja harjoituksiin
                    :vesiliikennelaki-kilpailut-ja-harjoitukset                       {::document-subphase "Hakemuksen vastaanottaminen"
                                                                                       ::document-title    "Kunnan ympäristönsuojeluviranomaisen päätökset"
                                                                                       ::tos-class         "11.01.02.00"}
                    ;; Maa-ainesluvan ja ympäristöluvan yhteiskäsittelyhakemus (MAL 4a § ja YSL 47a §)
                    :yhteiskasittely-maa-aines-ja-ymparistoluvalle                    {::document-subphase  "Hakemuksen vastaanottaminen"
                                                                                       ::document-title     "Yhteiskäsittelyluvan käsittely"
                                                                                       ::document-tos-title "Yhteiskäsittelylupahakemus liitteineen"
                                                                                       ::document-type      "Hakemus"
                                                                                       ::tos-class          "10.03.00.05.05"}
                    ;; Ympäristöluvan selventäminen (YSL 92 §)
                    :ymparistoluvan-selventaminen                                     {::document-subphase  "Hakemuksen vastaanottaminen"
                                                                                       ::document-title     "Ympäristöluvan käsittely"
                                                                                       ::document-tos-title "Ympäristölupahakemus liitteineen"
                                                                                       ::tos-class          "11.01.00.00.01"}
                    ;; Ilmoitus maa-ainesten kotitarveotosta (MAL 23a §)
                    :maa-ainesten-kotitarveotto                                       {::document-subphase  "Ilmoituksen pyytäminen ja vastaanottaminen"
                                                                                       ::document-title     "Kotitarveotto"
                                                                                       ::document-tos-title "Kotitarveottoilmoitus liitteineen"
                                                                                       ::tos-class          "10.03.00.05.02"}}}
      valid-config!))

(defmethod permit-config-for "734-YMP"
  [_]
  permit-config-salo)
