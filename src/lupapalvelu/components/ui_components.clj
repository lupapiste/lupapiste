(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]
            [lupapalvelu.env :as env]))

(def oskari {:depends [:init :jquery]
             :js ["oskarimap.js" "map.js"]
             :css ["oskarimap.css"]
             :html ["map.html"]
             :name "oskari"})

(def dummymap {:depends [:init :jquery]
               :js ["openlayers.2.12.js" "gis.js" "map.js"]
               :name "dummymap"})

(def ui-components
  {:jquery       {:css ["jquery.pnotify.default.css"]
                  :js ["jquery-1.8.0.min.js"
                       "jquery-ui-1.9.0.custom.min.js"
                       "jquery.ba-hashchange.js" "jquery.pnotify.min.js" "jquery.metadata-2.1.js" "jquery.tablesorter-2.0.5b.js"]}

   :knockout     {:js ["knockout-2.1.0.debug.js" "knockout.mapping-2.3.2.js" "knockout.validation.js"]}
   :underscore   {:js ["underscore.js"]}
   :init         {:js ["hub.js" "log.js"]}

   :map          (if (env/dev-mode?) dummymap oskari)

   :common       {:depends [:init :jquery :knockout :underscore]
                  :js ["pageutil.js" "loc.js" "notify.js" "ajax.js" "app.js" "nav.js" "combobox.js" "ko.init.js" "dialog.js" "comment.js" "authorization.js"]
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

   :application  {:depends [:common :repository]
                  :js ["application.js"]
                  :html ["application.html"]}

   :applications-common {:depends [:invites]
                         :js ["applications.js" "lupapiste.tablesorter.js"]
                         :css ["tablesorter.css"]}

   :applications {:depends [:common :repository :applications-common]
                  :js ["applications-config.js"]
                  :css ["applications.css"]
                  :html ["applications.html"]}

   :authority-applications {:depends [:common :repository :applications-common]
                            :js ["applications-config.js"]
                            :html ["applications.html"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js"]
                  :html ["attachment.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docgen.js"]
                  :css ["docgen.css"]}

   :create       {:js ["create-application.js"]
                  :html (map (partial format "create-application-%02d.html") (range 1 (inc 3)))}

   :applicant    {:depends [:common :map :application :applications :attachment :create :buildinfo :docgen]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :map :application :authority-applications :attachment :buildinfo :docgen]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :admin        {:depends [:common :map :buildinfo]
                  :js ["admin.js"]
                  :html ["index.html" "admin.html"]}

   :iframe       {:depends [:common]
                  :css ["iframe.css"]}

   :upload       {:depends [:iframe]
                  :js ["upload.js"]
                  :css ["upload.css"]}

   :welcome      {:depends [:common :register :buildinfo]
                  :js ["login.js"]
                  :html ["login.html" "index.html"]}})

; Make sure that all resources are available:

(doseq [c (keys ui-components)
        r (map c/path (mapcat #(c/component-resources ui-components % c) [:js :html :css]))]
  (let [resource (.getResourceAsStream (clojure.lang.RT/baseLoader) r)]
    (if resource
      (.close resource)
      (throw (Exception. (str "Resource missing: " r))))))
