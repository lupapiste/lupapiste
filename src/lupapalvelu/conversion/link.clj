(ns lupapalvelu.conversion.link
  "Code for the batchrun that establishes the app-links after the conversions
  themselves are done."
  (:require [lupapalvelu.application :refer [make-mongo-id-for-link-permit]]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.schemas :refer [ConversionConfiguration
                                                    ConversionDocument]
             :as conv-sc]
            [lupapalvelu.db :as db]
            [lupapalvelu.foreman-application-util :refer [foreman-app?]]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]))

(defn- get-conversion-documents [cfg option]
  (conv-doc/find-converted-documents cfg (case option
                                           :relink nil
                                           :repair {:linked      true
                                                    :app-links.0 {$exists true}}
                                           {:linked      {$ne true}
                                            :app-links.0 {$exists true}})))

(defn- link-query [a b]
  {:pre [(string? a) (string? b) (not= a b)]}
  {$and [{:link a} {:link b}]})

(sc/defn ^:private ^:always-validate resolve-app-link
  :- (sc/maybe (sc/conditional
                 :application-id {:application-id                     ssc/ApplicationId
                                  (sc/optional-key :old-backend-link) ssc/NonBlankStr}
                 :backend-id {:backend-id ssc/NonBlankStr}
                 :linked? {:linked?                     (sc/eq true)
                           (sc/optional-key :link-type) (sc/enum :application :backend)}))
  "Returns application or backend id matching the given link value or nil. Nil is returned
  if `app-link` is deemed to be an application id, but no corresponding application is
  found. Otherwise `app-link` is used as fallback backend-id. Also, application cannot
  link to itself and finally, the already existing links are omitted, as well. Thus, the
  function should only return value that can be linked to the converted application.

  Since a conversion can happen in stages, there could be an existing link to backend-id
  before the corresponding application has been converted. Thus, the given backend-id is
  included as `old-backend-link` field.

  If the resolved link already exists, a map with `linked?` true is returned. Optionally,
  the result can then also include `link-type` that is `:application` for fully-fledged
  and `:backend` for backend permit id links."
  [{:keys  [LP-id organization backend-id]
    doc-id :id} :- ConversionDocument
   app-link :- ssc/NonBlankStr]
  (when-let [link (if (validators/application-id? app-link)
                    (when (mongo/any? :applications {:_id          app-link
                                                     :organization organization})
                      {:application-id app-link})
                    ;; We prioritize non-ARK/non-paper applications. The logic is that if
                    ;; there are both ARK/paper and non-ARK/non-paper versions, the
                    ;; latter (probably converted) application will link to the
                    ;; former. The underlying assumption is that there are not both ARK
                    ;; and paper permits with the same backend-id (or it is exceedingly
                    ;; rare and can be ignored).
                    (if-let [[app-id] (or
                                        (db/application-ids-by-kuntalupatunnus
                                          organization
                                          app-link
                                          {:permitType            {$ne :ARK}
                                           :primaryOperation.name {$ne :aiemmalla-luvalla-hakeminen}}
                                          {:limit 1})
                                        ;; The way the eventual query is composed makes it
                                        ;; necessary to have both ARK and paper versions
                                        ;; separate.
                                        (db/application-ids-by-kuntalupatunnus
                                          organization
                                          app-link
                                          {:permitType :ARK}
                                          {:limit 1})
                                        (db/application-ids-by-kuntalupatunnus
                                          organization
                                          app-link
                                          {:primaryOperation.name :aiemmalla-luvalla-hakeminen}
                                          {:limit 1}))]
                      {:application-id   app-id
                       :old-backend-link app-link}
                      {:backend-id app-link}))]
    (let [link-id (or (:application-id link) (:backend-id link))]
      (cond
        ;; Link to self. Ignored, but considered linked.
        (contains? #{LP-id backend-id} link-id)
        (do
          (log/errorf "Self-reference in conversion document %s: %s"
                      doc-id link-id)
          {:linked? true})

        ;; Already linked.
        (mongo/any? :app-links (link-query LP-id link-id))
        {:linked?   true
         :link-type (if (:application-id link) :application :backend)}

        ;; Proper new link
        :else link))))

(defn- get-proper-links
  "Resolves the actual link targets. For example, the application id that matches the
  backend-id link. Ignores already linked and bad links. Returns nil, if no linking
  needed or cannot proceed."
  [{:keys [app-links] :as doc}]
  (when (seq app-links) ;; Could be empty when re-linked.
    (let [links (keep (partial resolve-app-link doc) app-links)]
      (when (= (count (filter :linked? links)) (count app-links))
        ;; Nothing needs to be done now or in the future
        (conv-doc/upsert-document :link (:id doc)))
      (some->> links (remove :linked?) seq))))

(sc/defn ^:private ^:always-validate clean-up-links
  "Removes the previously created links for the converted application. A link is removed
   if it is tagged with `conversion-document-id`. Also the conversion document linked
   status is reset."
  [{conversion-document-id :id} :- ConversionDocument]
  ;; Double check, just in case, since nil value would remove _EVERY_ non-conversion link.
  {:pre [conversion-document-id]}
  (mongo/remove-many :app-links {:conversion-document-id conversion-document-id})
  (mongo/update-by-id :conversion conversion-document-id {$unset {:linked true}}))

(defn- reference-ts
  "For a converted application returns timestamp for the earliest application history state
  if it is earlier than the application creation time. This restriction is needed, since
  the conversion process might add some states (e.g., `verdictGiven`). Nil if no such
  state is found.

  For a non-converted application returns `created` timestamp."
  [{:keys [history created facta-imported]}]
  (if facta-imported
    (some->> history
             (keep (fn [{:keys [state ts]}]
                     (when (and created state ts (< ts created))
                       ts)))
             sort
             first)
    created))

(defn- make-link [doc-id application target]
  (let [app-fn    (fn [a]
                    {:type       :application
                     :apptype    (-> a :primaryOperation :name)
                     :propertyId (:propertyId a)})
        link-fn   (fn [x]
                    (merge {:type :linkpermit}
                           (if (string? x)
                             {:linkpermittype :kuntalupatunnus}
                             {:apptype        (-> x :primaryOperation :name)
                              :linkpermittype :lupapistetunnus})))
        app-ts    (reference-ts application)
        [from to] (cond
                    ;; Backend-id target is always linkpermit
                    (string? target)
                    [application target]

                    ;; Foreman application is always source
                    (foreman-app? application)
                    [application target]

                    (foreman-app? target)
                    [target application]

                    :else
                    (let [target-ts (when app-ts
                                      (reference-ts target))]
                      ;; Later application links to earlier
                      (if (some-> target-ts (> app-ts))
                        [target application]
                        [application target])))
        from-id   (:id from)
        to-id     (:id to to)]
    {;; We use the "legacy method" for id creation in order to avoid any issues with the
     ;; old code.
     :_id                    (make-mongo-id-for-link-permit from-id to-id)
     :link                   [from-id to-id]
     :conversion-document-id doc-id
     from-id                 (app-fn from)
     to-id                   (link-fn to)}))

(defn- insert-link-permits
  "`link-ids` can be either application ids for existing applications or backend ids without
  mongo counterpart. The links have been previously resolved by `get-proper-links`.

  We are circumventing the 'official way' of adding link permits (see command
  `add-link-permit`), conversion linking differs from the typical use case:

  - Converted application is always the 'application' part of the link, whereas for regular
    applications, the foreman applications link to the main application.

  - Converted application can link to digitized (ARK) or previous
    permit (aiemmalla-luvalla-hakeminen) applications.

  - Conversion link documents have `conversion-document-id` property.

  - Conversion linking does not need to adhere the operation schema restrictions (max/min
    outgoing/incoming).

  - If there exists a link between application and backend-id and on this round the
    backend-id corresponds an application, the old link is removed.

  - However, conversion never upserts existing links between applications. The only way to
    update is with `--re-link` option and that only purges the links created by an earlier
    conversion linking (as determined by `conversion-document-id`)."
  [{doc-id :id app-id :LP-id backend-id :backend-id} links]
  {:pre [doc-id app-id]}
  (log/infof "Linking %s to %d permits" app-id (count links))
  (try
    (let [get-app     (fn [id]
                        {:post [%]}
                        (some->> id (mongo/by-id :applications)))
          application (get-app app-id)]
      (doseq [{link-app-id :application-id
               link-ref-id :backend-id
               old-ref-id  :old-backend-link} links]
        ;; Remove deprecated app-id - old-ref-id link, since ref is now a full-fledged
        ;; application.
        (when old-ref-id
          (mongo/remove-many :app-links (link-query app-id old-ref-id)))
        ;; Remove deprecated backend-id - link-app-id link, since `application` supercedes
        ;; `backend-id`.
        (when link-app-id
          (mongo/remove-many :app-links (link-query backend-id link-app-id)))
        (mongo/insert :app-links
                      (make-link doc-id application (if link-app-id
                                                      (get-app link-app-id)
                                                      link-ref-id))))
      (conv-doc/upsert-document :link doc-id))
    (catch Throwable e
      (log/errorf "Linking for conversion document %s failed: %s"
                  doc-id (ex-message e)))))

(defn- rotate-backend-id
  "12-3456-A   -> 3456-12-A
  05-2051-A 98 -> 98-2051-05-A
  foo-12-A     -> nil."
  [backend-id]
  (when-let [[_ a b c _ d] (some->> backend-id
                                    (re-find #"^(\d+)-(\d+)-(\w+)(\s+)?(\w+)?$"))]
    (ss/join-non-blanks "-" [d b a c])))

(defn- good-set?
  "Non-empty set with non-nil values."
  [s]
  (and (set? s) (not-empty s) (not-any? nil? s)))

(defn- good-set
  "Turn `values` into a good set or throws."
  [& values]
  {:post [(good-set? %)]}
  (some->> values (remove nil?) set not-empty))

(defn- remove-links [from toset]
  {:pre [from (good-set? toset)
         (not (contains? toset from))]}
  (log/info "Remove links:" {:from from :to toset})
  (mongo/remove-many :app-links {$and [{:link from}
                                       {:link {$in toset}}]}))

(defn- replace-link
  "Replaces backend app-link with a 'real' one, if a match is found for the backend-id or
  its rotated variant. On replace, returns [old-id new-id] (both can be the same if not
  rotated) vector, otherwise nil."
  [doc {app-id :id :as application} {:keys [link]}]
  (let [bid                         (util/find-first conv-sc/supported-backend-id? link)
        rotated                     (rotate-backend-id bid)
        ;; We try to resolve `bid` first and only fallback to `rotated`.
        resolved-links              (->> (cond-> [bid]
                                           rotated (conj rotated))
                                         (map (partial resolve-app-link doc)))
        {:keys     [old-backend-link]
         target-id :application-id} (util/find-first :application-id
                                                     resolved-links)
        target                      (some->> target-id
                                             (mongo/by-id :applications))
        ;; `old-backend-link` is either `bid` or `rotated`.
        backend-ids                 (good-set bid rotated)
        replace?                    (and application target old-backend-link)]
    ;; Remove the deprecated links, where the other end is `application` and the other is
    ;; a backend-id variant. If a fully resolved (application -> application) link already
    ;; exists, we do the removal just in case (for example, if a and b are both being
    ;; repaired, the b might have a deprecated link even if a has already been repaired).
    (when (or replace?
              (util/find-by-key :link-type :application resolved-links))
      (remove-links app-id backend-ids))

    (when replace?
      (mongo/insert :app-links (make-link (:id doc) application target))
      (if (= old-backend-link bid)
        (log/infof "Link repaired: %s -> %s." bid target-id)
        (log/infof "Link repaired: %s -> %s -> %s." bid rotated target-id))
      [bid old-backend-link])))

(defn- repair-links
  [{:keys [app-links] app-id :LP-id :as doc}]
  (when-let [bids (seq (filter conv-sc/supported-backend-id? app-links))]
    (let [application   (mongo/by-id :applications app-id)
          old-app-links (set app-links)
          {new-app-links :app-links
           repair?       :repair?
           }            (reduce
               (fn [acc bid]
                 (if-let [link (mongo/select-one
                                 :app-links
                                 {$and [{:link app-id}
                                        ;; The rotated variant is included just in case,
                                        ;; although it is hard to come up with a realistic
                                        ;; scenario, where a rotated backend link would
                                        ;; exist.
                                        {:link {$in (good-set bid (rotate-backend-id bid))}}]})]
                   (let [[old-id
                          new-id] (replace-link doc application link)]
                     (cond-> acc
                       old-id (assoc :repair? true)

                       (and new-id (not= new-id bid))
                       (update :app-links (util/fn-> (disj bid)
                                                     (conj new-id)))))
                   acc))
               {:app-links old-app-links}
               bids)]
      (when repair?
        (conv-doc/upsert-document :link-repair doc))
      (when-not (= old-app-links new-app-links)
        (conv-doc/update-document doc {$set {:app-links new-app-links}})))))

(sc/defn ^:always-validate conversion--link
  "The top level link batchrun function.
  Options (mutually exclusive, both optional):

  `relink`: the batchrun will ignore the `linked` attribute on the conversion and replace
  any existing links with the new ones.

  `repair`: the batchrun checks whether backend-id links could be transformed into
  fully-fledged links either directly or by changing the backend-id format."
  [{:keys [organization-id] :as cfg} :- ConversionConfiguration
   option :- (sc/maybe (sc/enum :relink :repair))]
  (let [conversion-docs (get-conversion-documents cfg option)
        batchrun-user   (user/batchrun-user [organization-id])]
    (doseq [doc conversion-docs]
      (log/with-logging-context {:applicationId (:LP-id doc)
                                 :userId        (:id batchrun-user)}
        (when (= option :relink)
          (clean-up-links doc))
        (if (= option :repair)
          (repair-links doc)
          (some->> doc get-proper-links (insert-link-permits doc)))))))
