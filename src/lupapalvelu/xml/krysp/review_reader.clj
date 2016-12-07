(ns lupapalvelu.xml.krysp.review-reader
  (:require [taoensso.timbre :refer [trace debug info warn error]]
            [lupapalvelu.xml.krysp.common-reader :as common]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now def- fail]]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.validators :as v]
            [sade.xml :refer [get-text select select1 under has-text xml->edn]]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.building :as building]))


(defn xml->reviews [xml]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (when (not-empty asiat)
      (when (> (count asiat) 1)
        (error "Creating application from previous permit. More than one RakennusvalvontaAsia element were received in the xml message. Count:" (count asiat)))

      (let [asia (first asiat)
            katselmukset (map cr/all-of  (select asia [:RakennusvalvontaAsia :> :katselmustieto :Katselmus]))
            massage (fn [katselmus]
                      (-> katselmus
                          (util/ensure-sequential :muuTunnustieto)
                          (util/ensure-sequential :huomautukset)
                          (util/ensure-sequential :katselmuksenRakennustieto)
                          (cr/convert-keys-to-timestamps [:pitoPvm])
                          cr/convert-booleans
                          cr/cleanup))]
        (map massage katselmukset)))))


(defn review-xml-validator [xml]
  (let [reviews (xml->reviews xml)
        pvm-valid-or-absent #(> (util/get-timestamp-ago :day 1)
                                (sade.common-reader/to-timestamp (:pitoPvm % "2011-11-11Z")))]
    (cond
      (not-any? pvm-valid-or-absent (map :pitoPvm reviews))
      (fail :info.???)
      (not-any? empty? (map :katselmuksenLaji reviews))
      (fail :info.???))))
