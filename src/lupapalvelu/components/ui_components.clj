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
   
   :common       {:depends [:jquery :knockout :bootstrap]
                  :js ["log.js" "notify.js" "hub.js" "loc.js" "ajax.js" "map.js" "main.js" "nav.js"]
                  :css ["main.css"]
                  :html ["error.html"]}
   
   :buildinfo    {:depends [:jquery]
                  :js ["buildinfo.js"]}
   
   :repository   {:depends [:common]
                  :js ["repository.js"]}
   
   :application  {:depends [:common :repository]
                  :js ["application.js"]
                  :html ["application.html"]}
   
   :applications {:depends [:common :repository]
                  :js ["applications.js" "lupapiste.tablesorter.js"]
                  :css ["tablesorter.css"]
                  :html ["applications.html"]}
   
   :attachment   {:depends [:common :repository]
                  :js ["attachment.js" "upload.js"]
                  :css ["upload.css"]
                  :html ["attachment.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html"]}

   :wizard       {:js ["application-create-wizard.js"]
                  :html (map (partial format "application-create-wizard-%02d.html") (range 1 (inc 3)))}

   :applicant    {:depends [:application :applications :attachment :wizard]
                  :html ["index.html"]}
   
   :authority    {:depends [:application :applications :attachment]
                  :html ["index.html"]}

   :welcome      {:depends [:register :jquery]
                  :js ["login.js"]
                  :html ["login.html" "index.html"]}})

; Make sure that all resources are available:

(doseq [c (keys ui-components)
        resource (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
  (let [r (.getResourceAsStream (clojure.lang.RT/baseLoader) (str "components/" resource))]
    (if r
      (.close r)
      (throw (Exception. (str "Resource missing: " (str "components/" resource)))))))
