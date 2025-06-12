(ns lupapalvelu.ryhti.datamodel.attachment
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [sade.date :as date]
            [sade.util :as util]))

(def ^{:schema gen/BuildingAttachmentDocument} attachment-mapping
  ;; Liiteasiakirja?
  ;; 1.60. Rakennusvalvonnan liiteasiakirja?
  ;; are the the same?
  [{:ryhti     [:attachmentDocumentKey]
    :lupapiste [:attachment :ryhti :attachmentDocumentKey]
    :->ryhti   (fn [k _] k)}

   {:ryhti       [:attachmentType]
    :lupapiste   [:attachment :type]
    :->ryhti     (fn [attachment-type _]
                   (let [type-id    (keyword (:type-id attachment-type))
                         type-group (keyword (:type-group attachment-type))]
                     (or (get mappings/asiakirjan-laji type-id)
                         (do
                           (log/debugf "No asiakirjan laji defined for '%s/%s'." type-group type-id)
                           (get mappings/asiakirjan-laji :muu)))))
    :description "Laji, 1"}

   {:ryhti       [:documentIdentifier]
    :lupapiste   [:attachment :documentIdentifier]
    :->ryhti     (fn [v _] (or v ""))
    ;; NOTE: Seems to be required on validation, though docs say 0..1
    ;; NOTE: Docs say localizedString, but OpenAPI just regular String.
    :description "Asiakirjan tunnus, 0..1"}

   {:ryhti       [:name]
    :lupapiste   [:attachment :contents]
    :->ryhti     (fn [v _] {:fin (or v "")})
    :description "Nimi, 1"}

   {:ryhti       [:personalDataContent]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO:
                   ;; Ei sisällä henkilötietoja
                   "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1")
    :description "Henkilötietosisältö, 1"}

   {:ryhti       [:categoryOfPublicity]
    :lupapiste   [:attachment :metadata :nakyvyys]
    :->ryhti     (fn [v _]
                   ;; LP has a) julkinen, b), liitteen lisääjä ja viranomaiset, and c) osapuolet ja viranomaiset
                   ;; These do not map cleanly to Ryhti enums, so make a compromise.
                   (if (util/=as-kw :julkinen v)
                     (:julkinen mappings/asiakirjan-julkisuusluokka)
                     (:salassapidettava mappings/asiakirjan-julkisuusluokka)))
    :description "Julkisuusluokka, 1"}

   {:ryhti       [:accessibility]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO
                   false)
    :description "Saavutettavuus, 1"}

   {:ryhti       [:retentionTime]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO:
                   ;; 0205 - Rakennuksen purkamiseen asti
                   "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205")
    :description "Säilytysaika, 1"}

   {:ryhti       [:confirmationDate]
    ;; :attachment :approvals?
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   (date/iso-date (date/plus (date/today) :days 2) :local))
    :description "Vahvistuspäivämäärä, 1"}

   {:ryhti       [:fileKey]
    :lupapiste   [:attachment :latestVersion :ryhti :fileKey]
    :->ryhti     (fn [k _] k)
    :required?   true
    :description "Tiedosto, 1"}

   #_
   {:ryhti       [:documentCreatorPersonalIdentityCode]
    :lupapiste   nil
    :description "asiakirjanLaatijanHenkilötunnus, 0..1"}

   #_
   {:ryhti       [:documentCreatorOtherId]
    :lupapiste   nil
    :description "asiakirjanLaatijanMuutunnus, 0..1"}

   ;; :attachment :latestVersion :user ?
   ;; NOTE: No hetu available
   #_
   {:ryhti       [:documentCreatorFirstName]
    :lupapiste   nil
    :description "asiakirjanLaatijanEtunimi, 0..1"}

   #_
   {:ryhti       [:documentCreatorFamilyName]
    :lupapiste   nil
    :description "asiakirjanLaatijanSukunimi, 0..1"}

   ;; FIXME: Rest are not on BuildingAttachmentDocument?
   ;; Might have been renamed?

   ;; Maybe recordingTime?
   {:ryhti       [:documentDate]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO:
                   (date/iso-date (date/today) :local))
    :description ""}

   ;; Maybe language
   {:ryhti       [:languages]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO: Koodisto
                   ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"])
    :description ""}

   #_
   {:ryhti       [:arrivedDate]
    :lupapiste   nil
    :description ""}

   #_
   {:ryhti       [:descriptors]
    :lupapiste   nil
    :description ""}

   ])
