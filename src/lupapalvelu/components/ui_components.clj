(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]))

(def ui-components
  {:openlayers   {:js ["OpenLayers.js"]}
  
   :jquery       {:css ["jquery.pnotify.default.css"]
                  :js ["jquery-1.8.0.min.js"
                       "jquery-ui-1.9.0.custom.min.js"
                       "jquery.ba-hashchange.js" "jquery.filedrop.js" "jquery.pnotify.min.js" "jquery.metadata-2.1.js" "jquery.tablesorter-2.0.5b.js"]}
   
   :knockout     {:js ["knockout-2.1.0.js" "knockout.mapping-2.3.2.js" "knockout.validation.js"]}
   
   :common       {:depends [:openlayers :jquery :knockout]
                  :js ["log.js" "notify.js" "hub.js" "loc.js" "ajax.js" "map.js" "nav.js" "ko.init.js"]
                  :css ["css/main.css"]
                  :html ["error.html"]}
   
   :buildinfo    {:depends [:jquery]
                  :js ["buildinfo.js"]}
   
   :repository   {:depends [:common]
                  :js ["repository.js"]}
   
   :application  {:depends [:common :repository]
                  :js ["application.js"]
                  :html ["application.html"]}
   
   :applications {:depends [:common :repository]
                  :js ["applications-config.js" "applications.js" "lupapiste.tablesorter.js"]                   :css ["tablesorter.css" "applications.css"]
                  :html ["applications.html"]}
   
   :authority_applications {:depends [:common :repository]
                            :js ["applications-config.js" "applications.js" "lupapiste.tablesorter.js"]
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

   :docgen       {:depends [:common]
                  :js ["accordion.js" "docgen.js"]
                  :css ["docgen.css"]
                  :html ["templates.html"]}
   
   :wizard       {:js ["application-create-wizard.js"]
                  :html (map (partial format "application-create-wizard-%02d.html") (range 1 (inc 3)))}

   :applicant    {:depends [:application :applications :attachment :wizard :buildinfo :docgen]
                  :js ["applicant.js"]
                  :html ["index.html"]}
   
   :authority    {:depends [:application :authority_applications :attachment :buildinfo :docgen]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :welcome      {:depends [:register :jquery :buildinfo]
                  :js ["login.js"]
                  :html ["login.html" "index.html"]}})

; Make sure that all resources are available:

(doseq [c (keys ui-components)
        r (map c/path (mapcat #(c/component-resources ui-components % c) [:js :html :css]))]
  (let [resource (.getResourceAsStream (clojure.lang.RT/baseLoader) r)]
    (if resource
      (.close resource)
      (throw (Exception. (str "Resource missing: " r))))))
