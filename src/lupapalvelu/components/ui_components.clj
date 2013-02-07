(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]
            [lupapalvelu.env :as env]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.util :as util]
            [cheshire.core :as json]))

(def debugjs {:depends [:init :jquery]
              :js ["debug.js"]
              :name "common"})

(defn- conf []
  (let [js-conf (util/sub-map env/config [:maps])
        data (json/generate-string js-conf)]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " data ";")))

(def ui-components
  {:jquery       {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.autocomplete.js"]}
   :knockout     {:js ["knockout.mapping-2.3.2.js" "knockout.validation.js"]}
   :underscore   {:js ["underscore.js" "underscore.string.min.js" "underscore.string.init.js"]}
   :moment       {:js ["moment.min.js"]}
   
   :init         {:js [conf "hub.js" "log.js"]}

   :map          {:depends [:init :jquery]
                  :js ["openlayers.2.12.js" "gis.js"]}

   :debug        (if (env/dev-mode?) debugjs {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" i18n/loc->js]}
   
   :common       {:depends [:init :jquery :knockout :underscore :moment :debug :i18n]
                  :js ["event.js" "pageutil.js" "notify.js" "ajax.js" "app.js" "nav.js" "combobox.js"
                       "ko.init.js" "dialog.js" "comment.js" "authorization.js"]
                  :css ["css/main.css"]
                  :html ["error.html"]}

   :buildinfo    {:depends [:jquery]
                  :js ["buildinfo.js"]}

   :invites      {:depends [:common]
                  :js ["invites.js"]}

   :repository   {:depends [:common]
                  :js ["repository.js"]}

   :accordion    {:depends [:jquery]
                  :js ["accordion.js"]
                  :css ["accordion.css"]}

   :application  {:depends [:common :repository :tree]
                  :js ["application.js" "add-operation.js"]
                  :html ["application.html" "inforequest.html" "add-operation.html"]}

   :applications {:depends [:common :repository :invites]
                  :html ["applications.html"]
                  :js ["applications.js"]
                  :css ["applications.css"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js" "attachmentTypeSelect.js"]
                  :html ["attachment.html" "upload.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html" "register3.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docgen.js"]
                  :css ["docgen.css"]}

   :create       {:depends [:common]
                  :js ["create.js"]
                  :html ["create.html"]}

   :applicant    {:depends [:common :map :applications :application :attachment
                            :buildinfo :docgen :create :mypage]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :map :applications :application :attachment
                            :buildinfo :docgen :mypage]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :authority-admin {:depends [:common :buildinfo :mypage]
                     :js ["admin.js"]
                     :html ["index.html" "admin.html"]}

   :tree    {:depends [:jquery]
             :js ["tree.js"]
             :css ["tree.css"]}

   :admin   {:depends [:common :map :buildinfo :mypage]
             :js ["admin.js"]
             :html ["index.html" "admin.html"]}

   :iframe  {:depends [:common]
             :css ["iframe.css"]}

   :upload  {:depends [:iframe]
             :js ["upload.js"]
             :css ["upload.css"]}

   :welcome {:depends [:common :register :buildinfo]
             :js ["welcome.js" "login.js"]
             :html ["login.html" "index.html"]}

   :mypage  {:depends [:common]
             :js ["mypage.js"]
             :html ["mypage.html"]
             :css ["mypage.css"]}})

; Make sure all dependencies are resolvable:
(doseq [[component {dependencies :depends}] ui-components
        dependency dependencies]
  (if-not (contains? ui-components dependency)
    (throw (Exception. (format "Component '%s' has dependency to missing component '%s'" component dependency)))))

; Make sure that all resources are available:
(doseq [c (keys ui-components)
        r (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
  (if (not (fn? r))
    (let [resource (.getResourceAsStream (clojure.lang.RT/baseLoader) (c/path r))]
      (if resource
        (.close resource)
        (throw (Exception. (str "Resource missing: " r)))))))
