(ns govalert.init
  (:require [govalert.elastic.store :refer (add-harvester)]
            [govalert.elastic.search :refer (harvesters)]
            [govalert.elastic.db :as db]
            [govalert.elastic.migrate :refer [migrate]]
            [cheshire.core :as json]))

(defn add-json-harvester [index name spec]
  (db/with-index index
    (apply add-harvester name (apply concat spec))))

(defn -main 
  ([es-endpoint index name harvest-spec]
    (assert es-endpoint)
    (println "ElasticSearch:" es-endpoint)
    (let [spec (if-not (empty? harvest-spec)
                  (json/parse-string harvest-spec true))
          index (if (empty? index) (:govbody spec) index)]
      (db/init es-endpoint)
      (migrate index)
      (if spec
        (do (println (add-json-harvester index name spec))
            (db/refresh index)))
      (doseq [index (if (empty? index) (db/indices) [index])]
        (println index ":")
        (doseq [h (harvesters index)]
          (println (json/generate-string h {:pretty true :escape-non-ascii true}))))) )
  ([es-endpoint index harvest-spec]
    (-main es-endpoint index nil harvest-spec))
  ([es-endpoint harvest-spec]
    (-main es-endpoint nil harvest-spec))
  ([es-endpoint]
    (-main es-endpoint nil nil nil)))


  



