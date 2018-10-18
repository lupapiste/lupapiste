(ns lupapalvelu.reports.users
  (:require [monger.operators :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.user :as usr])
  (:import (java.io OutputStream)))

(defn- authorities-report-headers [lang]
  (map (partial i18n/localize lang) ["authorities.report.excel.header.name"
                                     "authorities.report.excel.header.email"
                                     "authorities.report.excel.header.roles"]))

(defn- roles [roles org-id lang]
  (->> ((keyword org-id) roles)
       (map #(i18n/localize lang (str "authorityrole." %)))
       (ss/join ", ")))

(defn- authorities-for-organization [org-id lang]
  (->> (usr/get-users
         {:role "authority" :enabled true (str "orgAuthz." (name org-id)) {$exists true}}
         {:lastName 1 :firstName 1})
       (map (fn [authority] {:name  (ss/trim (str (:firstName authority) \space (:lastName authority)))
                             :email (:email authority)
                             :roles (roles (:orgAuthz authority) org-id lang)}))))

(defn ^OutputStream authorities [org-id lang]
  (let [authorities (authorities-for-organization org-id lang)
        wb          (excel/create-workbook
                      [{:sheet-name (i18n/localize lang "authorities.report.excel.sheet.name")
                        :header     (authorities-report-headers lang)
                        :row-fn     (juxt :name :email :roles)
                        :data       authorities}])]
    (excel/xlsx-stream wb)))
