(ns lupapalvelu.invoices.shared.util
  (:require [sade.strings :as ss]))

(defn- pair? [x]
  (and (not (string? x))
       (sequential? x)
       (= (count x) 2)
       (string? (first x))
       (string? (second x))))

(defn- get-operations-for-category
  "Get all operations as a flat string collection from an operation tree for a top level
  operation category such as Rakentaminen ja purkaminen"
  [operation-tree category]
  (let [by-category (into {} operation-tree)
        category-operation-tree (get by-category category)
        branch? (fn [x] (and (sequential? x)
                             (not (pair? x))))]
    (if (string? category-operation-tree)
      [category-operation-tree] ;; category has no children. Only one operation
      (->> (tree-seq branch? identity category-operation-tree)
           (filter pair?)
           (map second)))))

(defn get-operations-from-tree [operation-tree categories]
  (mapcat (partial get-operations-for-category operation-tree) categories))

(defn determine-delivery-type [{:keys [local-sftp? integration-url ssh-authentication?]}]
  (let [integration-url (some-> integration-url
                                ss/trim
                                ss/blank-as-nil
                                ss/lower-case)
        sftp-url?       (ss/starts-with? integration-url "sftp://")
        http-url?       (or (ss/starts-with? integration-url "http://")
                            (ss/starts-with? integration-url "https://"))]
    (cond
      local-sftp?               :local-sftp
      http-url?                 :http
      (and sftp-url?
           ssh-authentication?) :sftp-ssh
      sftp-url?                 :sftp-password)))


(defn validate-config-credentials
  "Depending on the resolved delivery type, some credentials are required:
    :http          - no required credentials
    :local-sftp    - no required credentials
    :sftp-ssh      - username required
    :sftp-password - username and password required

    Returns the config if credentials are valid, otherwise returns nil."
  [{:keys [delivery-type credentials] :as cfg}]
  (let [username (some-> credentials :username ss/blank-as-nil)
        password (some-> credentials :password ss/blank-as-nil)]
    (when (or (= delivery-type :http)
              (= delivery-type :local-sftp)
              (and (= delivery-type :sftp-ssh)
                   username)
              (and (= delivery-type :sftp-password)
                   username
                   password))
      cfg)))
