(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]))

(def ui-components
  {:openlayers   {:js ["OpenLayers.js"]}
  
   :jquery       {:css ["jquery.pnotify.default.css"]
                  :js ["jquery-1.8.0.min.js" "jquery.ba-hashchange.js" "jquery.filedrop.js"
                       "jquery.pnotify.min.js" "jquery.metadata-2.1.js" "jquery.tablesorter-2.0.5b.js"]}
   
   :bootstrap    {:css ["bootstrap.css"	"bootstrap-responsive.css" "addedStyles.css"]
                  :js ["bootstrap-dropdown.js" "bootstrap-collapse.js"]}
   
   :knockout     {:js ["knockout-2.1.0.js" "knockout.mapping-2.3.2.js" "knockout.validation.js" "ko.init.js"]}
   
   :common       {:js ["log.js" "notify.js" "hub.js" "loc.js" "ajax.js" "map.js" "main.js" "nav.js"]
                  :css ["main.css"]
                  :html ["error.html"]
                  :depends [:jquery :knockout :bootstrap]}
   
   :buildinfo    {:js ["buildinfo.js"]
                  :depends [:jquery]}
   
   :repository   {:js ["repository.js"]
                  :depends [:common]}
   
   :application  {:js ["application.js"]
                  :html ["application.html"]
                  :depends [:common :repository]}
   
   :applications {:js ["applications.js" "lupapiste.tablesorter.js"]
                  :css ["tablesorter.css"]
                  :html ["applications.html"]
                  :depends [:common :repository]}
   
   :attachment   {:js ["attachment.js" "upload.js"]
                  :css ["upload.css"]
                  :html ["attachment.html"]
                  :depends [:common :repository]}

   :register     {:css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html"]
                  :depends [:common]}

   :wizard       {:js ["application-create-wizard.js"]
                  :html (map (partial format "application-create-wizard-%02d.html") (range 1 4))}

   :applicant    {:depends [:application :applications :attachment :wizard]}
   
   :authority    {:depends [:application :applications :attachment]}

   :welcome      {:js ["login.js"]
                  :html ["login.html"]
                  :depends [:register :jquery]}})

(defn get-ui-resources [kind component]
  (c/get-resources ui-components kind component))

; Make sure that all resources are available:

(doseq [c (keys ui-components)]
  (doseq [resource (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
    (let [r (.getResourceAsStream (clojure.lang.RT/baseLoader) (str "components/" resource))]
      (if r
        (.close r)
        (throw (Exception. (str "Resource missing: " (str "components/" resource))))))))

