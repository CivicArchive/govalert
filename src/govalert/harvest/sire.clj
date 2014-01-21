(ns govalert.harvest.sire
  ; Interface to SIRE content
  (require [clojure.xml :as xml]
           [clojure.set :as set]
           [net.cgrand.enlive-html :as enlive]
           [ring.util.codec :as codec] 
           [clj-http.client :as client]
           [govalert.harvest.lib :as lib]
           [govalert.harvest.types :as types]
           [url-normalizer.core :as url-normalizer]))

;; ## use enlive instead of this DOM hack!!! If it works on XML...

(defn named-children [nm el]
  (filter #(= nm (:tag %)) (:content el)))

(defn extract [doc]
  {:items 
    (for [item (named-children :R (first (named-children :RES doc)))]
      (let [content (:content item)]
        (into 
         {; :raw item
         :MIME (:MIME (:attrs item))
         }
         (for [item content]
           (case (:tag item)
             :U [:U (:content item)]
             :T [:T (:content item)]
             :MT [(:N (:attrs item)) (:V (:attrs item))]
             :S [:S (:content item)]
             nil)))
      ))
  })

(defn open-string [s]
  (java.io.ByteArrayInputStream. (.getBytes s)))

(defn extract-xml [s]
  (-> s
    (open-string)
    (xml/parse)
    (extract)))

(defn response-extract [res]
  (let [h (:headers res)
        s (:body res)]
    (extract-xml s)))

(defn retrieve-backlinks [agenda-url args]
  (->
    (client/get agenda-url
      {:as :auto
       :query-params args})
    (:body)
    (extract-xml)))

(defn fetch-raw-agendas [agenda-url params]
  (->
   (client/get agenda-url 
    {:as :auto
     :query-params params})
    (:body)
    (extract-xml)))

(defn item-date [item]
  (get item "DOC_DATE"))

; (first (:items (extract (fetch-city "fire"))))

; (str (fetch-city "fire" :recent 1))

; (extract x)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CANONICAL PRESENTATION

(defn string-replace-multi [s replacements]
  (reduce
    (fn [s [match replacement]]
      (clojure.string/replace s match replacement))
    s
    replacements))

(defn clean-title [s & {indent :indent}]
  {:post [(not (some #(= 160 (int %)) %))]}
  (string-replace-multi s
    [[(str (char 160)) " "] ;; non break space but fails in html
     [(str (char 65533)) ""] ;; <?> symbol
     ["\n" " "]
     ["<b>" ""]
     ["</b>" ""]
     ["<br>" " "]]))

(defn- make-agenda [item]
  (types/make-agenda
   :title (or (clean-title (get item "TITLE"))
              (apply str (map clean-title (:T item))))
   :type (:MIME item)
   :uri (distinct
         (concat [(get item "DOCUMENT_URL")] ; source doc
                 (:U item)))
   :date (get item "DOC_DATE")
   :summary (apply str (map clean-title (:S item)))
   :links (if-not (empty? (:links item))
                  (map make-agenda (:links item)))))


(defn normalize [& results]
  (let [res (apply merge results)
        items (:changes res)]
    (merge 
     (dissoc res :changes)
     {:items (map make-agenda items)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn uri-args [uri]
  (-> (subs uri (inc (.indexOf uri "?" )))
      (codec/form-decode nil)))

(defn node-text [node]  ;; a;ready covered by enlive enlive/text
  {:post [string?]}
   (cond (string? node) node
         (and (:tag node)
              (not= :script (:tag node))) 
           (node-text (:content node))
         (seq? node) (apply str (map node-text node))
         :else ""))

(defn meetid [uri]
  (get (uri-args uri) "meetid"))

;(defn full-url [base-url relative-url]
;  (.toString (java.net.URL. (java.net.URL. base-url) relative-url)))

(defn full-url [base url]
  (:post [string?])
  (url-normalizer/resolve base url))

; (full-url "http://foo.com/bar" "foo") 

(defn primary-url [agenda]
  {:post [string?]}
  (let [u (:uri agenda)]
    (if (string? u) u (first u))))

(defn extract-doc-anchors [uri] ; eliminate!
   (filter #(and (empty? (:name (:attrs %)))
                 (= "agenda" (get (uri-args (:href (:attrs %))) "doctype")))
              (enlive/select (enlive/html-resource (java.net.URL. uri)) [:a])))


(defn extract-support-refs [support-group-uri]
  {:pre [(string? support-group-uri)]}
 (let [base (lib/resolve-redirect support-group-uri)]
  (for [row (enlive/select (enlive/html-resource (java.net.URL. support-group-uri)) [:td.tabledata])]
       (types/make-attachment
        :uri (str (full-url base (:href (:attrs (first (enlive/select (:content row) [:a]))))))
        :data (:attrs (enlive/select row [:a]))
        :label (clean-title (node-text row))))))

(defn extract-support-groups [agenda-uri]
 (let [base (lib/resolve-redirect agenda-uri)]
  (for [anchor (extract-doc-anchors agenda-uri)]
   (let [href (str (full-url base (:href (:attrs anchor))))]
    {:agenda-uri agenda-uri
     :href href
     :title (clean-title (str (node-text anchor)))
     :documents (if href (extract-support-refs href))}))))

(defn agenda-attachments [agenda]
  (flatten
   (for [grp (extract-support-groups (str (:url (:docs agenda)) (meetid (primary-url agenda))))] ;; ## fishy...
        (for [[ix doc] (map-indexed vector (:documents grp))]
             (assoc doc
                    :index ix
                    :groupurl (:href grp)
                    :groupname (:title grp))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sire-agendas [agendas]
  (let [dockets (fetch-raw-agendas (:url agendas) (:args agendas))]
    (map make-agenda (:items dockets))))

;; ## merge some of these..or eliminate! Also fix the normalization so it doesn't depend on keyword

(defn fetch-raw-sire-agendas [res] ; like match/fetch-raw-agendas -- redundant?
  (->
   (client/get (:url res) 
               {:as :auto
               :query-params (:args res)})
   (:body)
   (extract-xml)))

;(defn fetch-sire-agendas [res]
;  (-> (fetch-raw-sire-agendas res)
;      (set/rename-keys {:items :changes})
;      (normalize)
;      (:items)))

(defn fetch-sire-agendas [res]
  (->> (fetch-raw-sire-agendas res)
       (:items)
       (map make-agenda)))


; (fetch-sire-agendas (:agendas resource))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;







