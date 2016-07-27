(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement :refer :all]))

(def test-app-R  {:municipality 753 :permitType "R"})
(def test-app-P  {:municipality 753 :permitType "P"})
(def test-app-YA {:municipality 753 :permitType "YA"})
(def test-app-YM {:municipality 753 :permitType "YM"})
(def statuses-small ["puollettu" "ei-puollettu" "ehdollinen"])
(def statuses-large ["ei-huomautettavaa" "ehdollinen" "puollettu"
                     "ei-puollettu" "ei-lausuntoa" "lausunto"
                     "kielteinen" "palautettu" "poydalle"])

  ;; permit type R


(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
  (possible-statement-statuses test-app-R) => (just statuses-small :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.5" :url "krysp-url"}}}))

(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
  (possible-statement-statuses test-app-R) => (just statuses-large :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.6" :url "krysp-url"}}}))

  ;; permit type P


(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.3"
  (possible-statement-statuses test-app-P) => (just statuses-small :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.1.5" :url "krysp-url"}}}))

(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.5"
  (possible-statement-statuses test-app-P) => (just statuses-large :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.2.0" :url "krysp-url"}}}))

(fact "get-possible-statement-statuses, permit type P, no KRYSP version nor url"
  (possible-statement-statuses test-app-P) => (just statuses-large :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {}))

(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.3 no url"
  (possible-statement-statuses test-app-P) => (just statuses-large :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.1.5"}}}))

  ;; permit type YA


(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
  (possible-statement-statuses test-app-YA) => (just statuses-small :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.1.3" :url "krysp-url"}}}))

(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
  (possible-statement-statuses test-app-YA) => (just statuses-small :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.2.0" :url "krysp-url"}}}))

  ;; permit type YM


(fact "get-possible-statement-statuses, permit type YM, no krysp versions defined"
  (possible-statement-statuses test-app-YM) => (just statuses-small :in-any-order)
  (provided
   (organization/resolve-organization anything anything) => {}))

(defn dummy-application [statement]
  {:statements [statement]})

(defspec validate-statement-owner-pass 5
  (prop/for-all [email (ssg/generator ssc/Email)]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc-in [:person :email] email))
                      command   {:data {:statementId (:id statement)} :user {:email email}
                                 :application (dummy-application statement)}]
                  (nil? (statement-owner command)))))

(defspec validate-statement-owner-fail 5
  (prop/for-all [[email1 email2] (gen/such-that (partial apply not=) (gen/tuple (ssg/generator ssc/Email) (ssg/generator ssc/Email)))]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc-in [:person :email] email1))
                      command   {:data {:statementId (:id statement)} :user {:email email2}
                                 :application (dummy-application statement)}]
                  (-> (statement-owner command)
                      :ok false?))))

(defspec validate-statement-given-pass 5
  (prop/for-all [state (gen/elements [:given :replyable :replied])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (nil? (statement-given {:data {:statementId (:id statement)}
                                          :application (dummy-application statement)})))))

(defspec validate-statement-given-fail 5
  (prop/for-all [state (gen/elements [:requested :draft :unknown-state])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (-> (statement-given {:data {:statementId (:id statement)}
                                        :application (dummy-application statement)})
                      :ok false?))))

(defspec validate-statement-replyable-pass 1
  (prop/for-all [state (gen/elements [:replyable])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (nil? (statement-replyable {:data {:statementId (:id statement)}
                                              :application (dummy-application statement)})))))

(defspec validate-statement-replyable-fail 10
  (prop/for-all [state (gen/elements [:requested :draft :given :replied :unknown-state])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (-> (statement-replyable {:data {:statementId (:id statement)}
                                            :application (dummy-application statement)})
                      :ok false?))))

(def id-1 (ssg/generate ssc/ObjectIdStr))
(def id-2 (ssg/generate ssc/ObjectIdStr))
(def id-a (ssg/generate ssc/ObjectIdStr))
(def id-b (ssg/generate ssc/ObjectIdStr))

(facts "update-statement"
  (fact "update-draft"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :modified)
        (update-draft "some text" "puollettu" id-a id-1))
    => (contains #{[:text "some text"]
                   [:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :draft]
                   [:modified anything]}))

  (fact "update-draft - wrong modify-id"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (update-draft "some text" "puollettu" id-b id-1))
    => (throws Exception))

  (fact "update-draft - updated statement is missing person should produce validation error"
    (-> (ssg/generate Statement)
        (dissoc :person)
        (update-draft "some text" "puollettu" id-b id-1))
    => (throws Exception))

  (fact "give-statement"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :given)
        (give-statement "some text" "puollettu" id-a id-1))
    => (contains #{[:text "some text"]
                   [:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :given]
                   [:given anything]}))

  (fact "update-reply-draft"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :editor-id id-1 :state :announced :text "statement text")
        (assoc-in [:reply :saateText] "saate")
        (dissoc :modified)
        (update-reply-draft "reply text" true id-a id-2))
    => (contains #{[:text "statement text"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :replyable]
                   [:modified anything]
                   [:reply {:editor-id id-2
                            :nothing-to-add true
                            :text "reply text"
                            :saateText "saate"}]}))

  (fact "update-reply-draft - nil values"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :reply {:saateText "saate"})
        (update-reply-draft nil nil id-a id-2))
    => (contains #{[:reply {:editor-id id-2
                            :nothing-to-add false
                            :saateText "saate"}]}))

  (fact "reply-statement"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :state :announced)
        (dissoc :reply)
        (reply-statement "reply text" false id-a id-2))
    => (contains #{[:state :replied]
                   [:reply {:editor-id id-2
                            :nothing-to-add false
                            :text "reply text"}]}))

  (fact "request for reply"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :reply)
        (request-for-reply "covering note for reply" id-1))
    => (contains #{[:reply {:editor-id id-1
                            :nothing-to-add false
                            :saateText "covering note for reply"}]})))
