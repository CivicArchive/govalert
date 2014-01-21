(ns govalert.harvest.crawl
  ; A special purpose crawler to harvest government meeting agendas and their associated support documents.
  (:require clojure.set
            [clj-http.client :as client]
            [hiccup.util]
            [url-normalizer.core :as url-normalizer]
            [clj-time.format :as time-format]
            [govalert.harvest.pdf :as pdf]
            [govalert.harvest.sire :as sire]
            [govalert.harvest.granicus :as granicus]
            [govalert.harvest.govdelivery :as govdelivery]
            [govalert.harvest.html :as html]
            [govalert.harvest.docx :as docx]
            [govalert.harvest.lib :refer [http-get]]
            [govalert.harvest.lib :as lib]
            [govalert.harvest.types :as types]))

(defn canonical [url & {prefix :prefix}]
  {:href (url-normalizer/normalize url)})

(def rfc822 (time-format/formatters :rfc822))

(defn rfc1123-timestamp [s]
  (if s (time-format/parse (clojure.string/replace s #"GMT" "+0000"))))

(defn extract-links 
  "Extract all links"
  [url & {params :params selector :selector}]
  (let [{headers :headers body :body} (http-get url :as :byte-array :query-params params)
         modified (get headers "last-modified" nil)
         content-type (get headers "content-type" nil)
         url (lib/url-str url params)]
      (cond
        (re-find #"application/pdf" content-type)
          (pdf/extract-links (java.net.URL. url))
        (re-find #"text/html" content-type)  
          (html/extract-links url selector)
        :else (println "Unknown links content type" content-type))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AGENDAS

(defn fetch-agendas
  "Extract links to agendas"
  [uri & agendas]
  (let [{headers :headers body :body}
        (http-get uri :as :byte-array)
         modified (get headers "last-modified" nil)
         content-type (get headers "content-type" nil)]
      (cond
        (re-find #"application/pdf" content-type)
          (println "pdf not yet supported for agendas")
        (re-find #"text/html" content-type)  
          (html/html-agendas {:url uri})
        :else (println "Unknown agenda content type" content-type))))

(defmethod types/agendas "sire" [agendas]
  (sire/sire-agendas agendas))

(defmethod types/agendas "html" [agendas]
  (html/html-agendas agendas))

(defmethod types/agendas :url [url]
  (fetch-agendas url))

(defmethod types/agendas nil [agendas]
  (fetch-agendas (:url agendas)))

(defmethod types/agendas "subgroup" [agendas]
  ; Links leads to a page that links to the real agendas (possible grouped)"
  (flatten
    (for [grouplink (extract-links (:url agendas) :selector (:selector agendas) :params (:args agendas))]
      (let [sub (:sub agendas)]
        (for [a (extract-links (:uri grouplink) :selector (:selector sub))]
           (types/make-agenda
             :groupurl (:uri grouplink)
             :groupname (:label grouplink)
             :uri (:uri a)
             :title (:label a)))))))

(defn get-agendas 
  "A list of agendas for the resource, with docs of the resource overriding those from each agenda"
  [resource]
  (let [agendas (:agendas resource)]
    (->>
     (types/agendas agendas)
     (#(or (:items %) %))  ;; eliminate
     (map #(update-in % [:docs] 
             (fn [docs] (merge docs (:docs resource))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ATTACHMENTS

(defn support-docs 
  "Extract links to attachments"
  [uri & args]
  (for [a (apply extract-links uri args)
        :when (or (:uri a)(:url a))]
    (types/make-attachment
      :uri (or (:uri a)(:url a))
      :label (:label a))))

(defmethod types/attachments "enclosed" [agenda]
  ;; Agenda itself contains the support documents:
   ;(dissoc agenda :docs)
  [(apply assoc
    (types/make-attachment
     :label (:title agenda))
    (apply concat (dissoc agenda :docs)))])

(defmethod types/attachments "sire" [agenda]
    ;; Sire agenda links to group of linked support documents
   (sire/agenda-attachments agenda))

(defmethod types/attachments nil [agenda]
    ;; agenda links to support documents:
    (support-docs (:uri agenda) :params (:args agenda)))

(defn get-docs [agenda]
  (types/attachments agenda))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CONTENT

(defn fetch-content 
  "Plain text for the url, suited for indexing"
  [url & [params]]
  {:pre [(string? url)]}
  (let [{headers :headers body :body}
         (http-get url :as :byte-array :query-params params)
         modified (get headers "last-modified" nil)
         content-type (get headers "content-type" nil)
         stream (java.io.ByteArrayInputStream. body)]
    {:modified (rfc1123-timestamp modified)
     :content
       (cond
         (re-find #"application/pdf" content-type)
           (apply str (pdf/parse-pdf stream))
         (re-find #"text/html" content-type)  
           (html/read-text stream)
         (re-find #"application/vnd.openxmlformats-officedocument.wordprocessingml.document" content-type) 
           (docx/read-text stream)
         :else (println "Unknown content type" content-type)) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CRAWL

(defn url-string [u] ;; redundant name...
  (cond
     (string? u) u 
     (coll? u) (first u)  ; check again?
     (= (type u) java.net.URI) (str u)))

(defn primary-url [agenda]
  {:post [string? #(do (println %) %)]}
  (url-string  (:uri agenda)))

(defn crawl 
  "Generalized agenda crawler/dispatcher, finding attachments using DSL in resource definition"
  [resource & {handle-agenda :handle-agenda handle-attachment :handle-attachment canonize :canonize verbose :verbose throttle :throttle}]
  (doseq [agenda (lib/with-timeout (lib/sec 300) 
                   (get-agendas resource))]
    (if-let [url (primary-url agenda)]
      (try
        (let [agenda-id (str url)
              {content :content} 
                 (lib/with-timeout (lib/sec 30) 
                    (fetch-content url nil))]
          (if verbose (println "Handle Agenda: " (type agenda) url))
          (future
            (lib/with-timeout (lib/sec 10)
              (handle-agenda
               :id (str agenda-id)
               :title (str (:title resource) ": " (:label agenda)) ;; ## agenda has a :title but not :label ...
               :url (str url)
               :date (:date agenda)
               :summary (:summary agenda)
               :content content)))
          (doseq [doc (lib/with-timeout (lib/sec 300) 
                        (get-docs (update-in agenda [:uri] 
                                     (fn [uri] (primary-url agenda)))))]
            (if verbose (println "Handle Attachment: " (type doc) (:uri doc)))
            (Thread/sleep (or throttle 50))
            (future
              (lib/with-timeout (lib/sec 10)
                (try
                  (let [{href :href} ((or canonize canonical)(url-string (:uri doc)))                  
                        {content :content modified :modified}
                            (fetch-content (str href) nil)]
                     (handle-attachment
                         :id (str href)
                         :content content
                         :timestamp modified
                         :url (str href)
                         :agenda agenda-id
                         :title (:label doc)
                         :date (:date agenda)
                         :label (:label doc)
                         :groupurl (str (:groupurl doc))
                         :groupname (:groupname doc)))
                  (catch Exception e (if verbose (println "Failed processing attachment:" e) e)))))))
        (catch Exception e (if verbose (println "Failed processing agenda: " e) e))))))

(defn crawler [& {title :title govbody :govbody agendas :agendas docs :docs verbose :verbose :as spec}]
  (partial crawl spec))








 