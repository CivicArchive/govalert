(ns govalert.elastic.search
  (:require [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query         :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-time.coerce :as coerce-time]
            [govalert.harvest.types :refer (make-agenda)]
            [govalert.elastic.db :as db]))

(defn search-hits 
  "Collection of all hits matching the query, lazy paging"
  [index type & {query :query start :from after :after}]
  (let [size 10
        query (if after 
                (q/filtered :query query
                            :filter {:range {:timestamp {:from (coerce-time/to-long after) :include_lower false}}})
                query)
        page (fn [from size]
                 (esd/search index  type :from from :size size :query query))
        initial (page (or start 0) size)
        total (esrsp/total-hits initial)
        hits (esrsp/hits-from initial)]
    (apply concat hits
      (for [from (range (+ (or start 0) size) total size)]
        (let [r (page from size)]
          (esrsp/hits-from r))))))

(defn harvesters    
  ([index]
    (if (= index :all)
      (mapcat harvesters (db/indices))
      (map :_source (search-hits (or index db/current-index) "harvester" :query (q/match-all)))))
  ([] 
    (harvesters db/current-index)))

(defn subscriptions 
  ([index] 
     (if (= index :all)
       (mapcat subscriptions (db/indices))
       (for [item (search-hits index "subscription" :query (q/match-all))]
         (update-in (:_source item) [:updated]
                    #(if (integer? %) (coerce-time/from-long %))))))
  ([]
    (subscriptions db/current-index)))

(defn href-itemid [url]
  (let [[org id] (first (re-seq #"(\d+)" url))]
    id))

(defn get-doc [id]
  (esd/get db/current-index "attachment" id))

(defn agenda-from-hit [a]
  (assoc
   (make-agenda
    :title (:title a)
    :uri (list (:url a))
    :date (:date a)
    :summary (:summary a))
   :id (:_id a)))

(defn get-agenda [id]
  (esd/get db/current-index "agenda" id))

(defn dated-agendas [date] ;; no matching???
  (esd/search db/current-index "agenda" :query (q/text :date date)))

(defn search-agendas [index query & {after :after}]
  (for [m (search-hits index "agenda" :after after :query query)]
     (merge (:_source m) (dissoc m :_source))))

(defn all-agendas []
  (search-agendas db/current-index (q/match-all)))

(defn agenda-documents [id]
  (esd/search db/current-index "attachment" :query (q/text :_parent id)))
 
(defn count-all-docs-raw []
  (doc/count db/current-index "attachment" (q/match-all)))

(defn count-all-docs []
  (reduce
    (fn [total index]
      (db/with-index index
        (esi/refresh index)
        (:count (count-all-docs-raw))))
    0
    (db/indices)))

(defn count-all-agendas-raw []
  (doc/count db/current-index "agenda"))

(defn count-all-agendas []
  (reduce 
    (fn [total index]
      (db/with-index index
        (:count (count-all-agendas-raw))))
    0
    (db/indices)))

(def max-query-size 10)

(defn query-hits
  "Collection of document hit records matching the query, transparently paged"
  [qstring & {agenda :agenda after :after}]
  (search-hits db/current-index "attachment"
    :query ;; ## simplify as search-hits now has :after
    (if after
       (if agenda
         (q/filtered :query (q/query-string :query qstring)
                     :filter {:prefix {:id agenda}})
         (q/filtered :query (q/query-string :query qstring)
                     :filter {:range {:timestamp {:from (coerce-time/to-long after) :include_lower false}}}))
       (q/query-string :query qstring))))

(defn document-from-hit 
  "Converts an attachment hit record into a document structure"
  [item]
  (let [src (:_source item)]
      {:score (:_score item)
       :agenda (:agenda item)
       :parent (:_parent src)
       :timestamp (coerce-time/from-long (:timestamp src))
       :date (:date src)
       :group (:groupurl src) ;; ## eliminate
       :groupurl (:groupurl src)
       :groupname (:groupname src)
       :title (:title src)
       :label (:label src)
       :uri (:url src)}))

(defn shard-agendas
  "All agendas from the index, normalized in the same format as when fetched from the website"
  ([] {:items (map agenda-from-hit (all-agendas))}))

(defn matches [term]
  (reduce
   (fn [m q]
       (update-in m [(:url (:_source q))]
                  #(identity %2)
                  (:_score q)))
   {}
   (query-hits term)))
  
(defn distinct-by 
  "Discarding all but the first of an item matching the key"
  [key coll]
 (->
  (reduce 
    (fn [[result seen] item]
           (if (seen (key item))
             [result seen]
             [(concat result [item]) (assoc seen (key item) true)]))
    [nil {}]
    coll)
  (first)))

; (distinct-by :u [{:u 1}{:u 1}{:u 2}{:u 1}])

(defn matching-agenda-documents [itemid & {term :term hits :hits}]
 (distinct-by :uri
  (for [item (if-not (empty? term) (query-hits term) hits)
        :let [src (:_source item)
              grp (:groupurl src)]
        :when (and grp (= itemid (href-itemid grp)))] 
      (document-from-hit item))))

(defn matching-dockets 
   "Returns agendas with their matching support documents as :docs"
   [index & {query :query after :after}] ;; ** Exported!
  {:pre [(string? query)]}
  (db/with-index index
    (let [agenda-docs (group-by #(get-in % [:_source :agenda]) (query-hits query :after after))]
     (distinct-by :uri
      (concat
        (for [docket (map agenda-from-hit (all-agendas))
              :let [adocs (get agenda-docs (:id docket))]
              :when adocs]
           (assoc-in docket [:docs] (map document-from-hit adocs)))
        ; order matters, after to prioritize distinct instances with attachments
        (map agenda-from-hit (search-agendas db/current-index (q/query-string :query query) :after after)))))))

