(ns govalert.elastic.migrate
  ; Migrating elastic search index for agendas and associated support documents
  (:require [clojurewerkz.elastisch.rest.index :as esi]
            [govalert.elastic.db :as db]))

(def mapping-types 
  {"harvester"
   {:properties {:title {:type "string" :store "yes" :index "not_analyzed"}
                 :govbody {:type "string" :store "yes" :index "not_analyzed"}
                 :agendas {:type "object" :store "yes" :index "not_analyzed"}
                 :docs {:type "object" :store "yes" :index "not_analyzed"}}}
   "subscription" 
    {:properties {:email {:type "string" :store "yes" :index "not_analyzed"}
                  :query {:type "string" :store "yes" :index "not_analyzed"}
                  :updated {:type "date" :store "yes" :index "not_analyzed"}}} 
   "agenda" 
    {:properties {:url   {:type "string" :store "yes" :index "not_analyzed"}
                  :date  {:type "string" :store "yes" :index "not_analyzed"}
                  :title {:type "string" :store "yes"}
                  :summary {:type "string" :store "yes"}
                  :content {:type "string" :analyzer "standard"}}}
   "attachment" 
    {:properties {:url   {:type "string" :store "yes" :index "not_analyzed"}
                  :agenda {:type "string" :store "yes" :index "not_analyzed"}
                  :groupurl {:type "string" :store "yes"}
                  :groupname {:type "string" :store "yes"}
                  :label {:type "string" :store "yes"}
                  :title {:type "string" :store "yes"}
                  :content {:type "string" :analyzer "standard"}}} })

(def mapping-update
  {"attachment" 
    {:_id {:index "not_analyzed" :store "yes" :path "id"}
           :_timestamp {:enabled true :store true :path "timestamp"}}
   "agenda" 
    {:_timestamp {:enabled true :store true :path "timestamp"}}})

(def settings nil)

(defn migrate [& [index]]
  (if-not (esi/exists? (or index db/current-index))
    (try
     (esi/create (or index db/current-index) :mappings mapping-types :settings settings)
     (catch Exception e (println e))))
  (doseq [m [mapping-types mapping-update]]
    (doseq [[name mapping] m]
      (esi/update-mapping (or index "_all") name :mapping mapping))))

(defn delete! [index]
  (esi/delete index))

(defn -main [es-endpoint & [index]]
  (assert es-endpoint)
  (db/init es-endpoint)
  (migrate index))

