(ns lupapalvelu.components.ui-components
  (:use [clojure.tools.logging])
  (:require [lupapalvelu.components.core :as c]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]))

(def debugjs {:depends [:init :jquery]
              :js ["debug.js"]
              :name "common"})

(defn- conf []
  (let [js-conf {:maps (:maps env/config)
                 :fileExtensions mime/allowed-extensions
                 :passwordMinLength (get-in env/config [:password :minlength]) }
        data (json/generate-string js-conf)]
    
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " data ";")))

(defn loc->js []
  (str ";loc.setTerms(" (json/generate-string (i18n/get-localizations)) ");"))

(def ui-components
  {:cdn-fallback {:js ["jquery-1.8.0.min.js" "jquery-ui-1.10.1.custom.min.js" "jquery.dataTables.min.js" "knockout-2.1.0.js"]}
   :jquery       {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.autocomplete.js" "jquery.cookie.js"]}
   :knockout     {:js ["knockout.mapping-2.3.2.js" "knockout.validation.js" "knockout-repeat-1.4.2.js"]}
   :underscore   {:js ["underscore-1.4.4-min.js" "underscore.string.min.js" "underscore.string.init.js"]}
   :moment       {:js ["moment.min.js"]}

   :init         {:js [conf "hub.js" "log.js"]
                  :depends [:underscore]}

   :map          {:depends [:init :jquery]
                  :js ["openlayers.2.12.js" "gis.js"]}

   :debug        (if (env/dev-mode?) debugjs {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" loc->js]}

   :selectm      {:js   ["selectm.js"]
                  :html ["selectm.html"]
                  :css  ["selectm.css"]}

   :common       {:depends [:init :jquery :knockout :underscore :moment :i18n :selectm]
                  :js ["util.js" "event.js" "pageutil.js" "notify.js" "ajax.js" "app.js" "nav.js" "combobox.js"
                       "ko.init.js" "dialog.js" "comment.js" "authorization.js" "datepicker.js" "municipalities.js"]
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
                  :js ["applications.js"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js" "attachmentTypeSelect.js"]
                  :html ["attachment.html" "upload.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html" "register3.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docgen.js"]}

   :create       {:depends [:common]
                  :js ["create.js"]
                  :html ["create.html"]}

   :applicant    {:depends [:common :map :applications :application :attachment
                            :buildinfo :docgen :create :mypage :debug]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :map :applications :application :attachment
                            :buildinfo :docgen :create :mypage :debug]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :authority-admin {:depends [:common :buildinfo :mypage :debug]
                     :js ["admin.js"]
                     :html ["index.html" "admin.html"]}

   :tree    {:depends [:jquery]
             :js ["tree.js"]
             :html ["tree.html"]
             :css ["tree.css"]}

   :admin   {:depends [:common :map :buildinfo :mypage :debug]
             :js ["admin.js"]
             :html ["index.html" "admin.html"]}

   :iframe  {:depends [:common]
             :css ["iframe.css"]}

   :upload  {:depends [:iframe]
             :js ["upload.js"]
             :css ["upload.css"]}

   :welcome {:depends [:common :register :buildinfo :debug]
             :js ["welcome.js" "login.js"]
             :html ["login.html" "index.html"]}

   :mypage  {:depends [:common]
             :js ["mypage.js"]
             :html ["mypage.html"]
             :css ["mypage.css"]}

   :about {:depends [:common :buildinfo :debug]
           :js ["about.js"]
           :html ["terms.html" "index.html"]}})

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
