(ns lupapalvelu.xml.krysp.vesihuolto-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.permit :as permit]))

(def vesihuolto-to-krysp [{:tag :ymv:VesihuoltolakiType
                           :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                                   {:tag :vapatukset
                                    :child [{:tag :Vapautus
                                             :child [{:tag :kasittelytietotieto :child [mapping-common/ymp-kasittelytieto-children]}
                                                     {:tag :luvanTunnistetiedot
                                                      :child [mapping-common/lupatunnus]}
                                                     {:tag :lausuntotieto
                                                      :child [mapping-common/lausunto]}]}]}
                                   {:tag :vapautushakemustieto :child []}
                                   {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children}]}
                                   ]}])