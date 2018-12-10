(ns lupapalvelu.pate-test-util)

(defn make-verdict [& kvs]
  (let [{:keys [id code section modified published replaces]} (apply hash-map kvs)]
    {:id          id
     :category    "r"
     :schema-version 1
     :data        {:verdict-code    code
                   :handler         "Foo Bar"
                   :verdict-section section
                   :verdict-date    876543}
     :modified    (or published modified 1)
     :published   (when published {:published published})
     :replacement (when replaces {:replaces replaces})
     :template    {:inclusions ["verdict-code"
                                "handler"
                                "verdict-date"
                                "verdict-section"
                                "verdict-text"]
                   :giver      "viranhaltija"}
     :references  {:boardname "Broad board abroad"}}))
