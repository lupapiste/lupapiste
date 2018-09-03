(ns lupapalvelu.backing-system.krysp.review-reader
  (:require [taoensso.timbre :refer [trace debug info warn error]]
            [lupapalvelu.backing-system.krysp.common-reader :as common]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now def- fail]]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :refer [get-text select select1 under has-text xml->edn]]))


(defn xml->reviews [xml & [strict?]]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (when (not-empty asiat)
      (when (> (count asiat) 1)
        (error "Creating application from previous permit. More than one RakennusvalvontaAsia element were received in the xml message. Count:" (count asiat)))

      (let [asia (first asiat)
            selector (if strict?
                       [:RakennusvalvontaAsia :> :katselmustieto :Katselmus]
                       [:RakennusvalvontaAsia :katselmustieto :Katselmus])
            katselmukset (map cr/all-of (select asia selector))
            massage (fn [katselmus]
                      (-> katselmus
                          (util/ensure-sequential :muuTunnustieto)
                          (util/ensure-sequential :huomautukset)
                          (util/ensure-sequential :katselmuksenRakennustieto)
                          (cr/convert-keys-to-timestamps [:pitoPvm])
                          cr/convert-booleans
                          cr/cleanup))]
        (map massage katselmukset)))))
