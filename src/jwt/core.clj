(ns jwt.core
  (:require
    [jwt.base64        :refer [url-safe-encode]]
    [jwt.sign          :refer [get-signature-fn]]
    [clj-time.coerce   :refer [to-long]]
    [clojure.data.json :as json]))

(def ^:private DEFAULT_SIGNATURE_ALGORITHM :HS256)
(def ^:private make-encoded-json (comp url-safe-encode json/write-str))
(defn- update-map [m k f] (if (contains? m k) (update-in m [k] f) m))
(defn- joda-time? [x] (= org.joda.time.DateTime (type x)))
(defn- to-intdate [d] {:pre [(joda-time? d)]} (int (/ (to-long d) 1000)))

(defrecord JWT [header claims signature])

; ----------------------------------
; JsonWebToken
; ----------------------------------
(defprotocol JsonWebToken
  "Protocol for JsonWebToken"
  (init           [this] [this claims] "Initialize token")
  (encoded-header [this] "Get url-safe base64 encoded header json")
  (encoded-claims [this] "Get url-safe base64 encoded claims json")
  (to-str         [this] "Generate JsonWebToken as string"))

(extend-protocol JsonWebToken
  JWT
  (init [this claims]
    (assoc this :header {:alg "none" :typ "JWT"} :claims claims :signature ""))

  (encoded-header [this]
    (-> this :header make-encoded-json))

  (encoded-claims [this]
    (-> this :claims make-encoded-json))

  (to-str [this]
    (str (encoded-header this) "." (encoded-claims this) "." (get this :signature ""))))


; ----------------------------------
; JsonWebSignature
; ----------------------------------
(defprotocol JsonWebSignature
  "Protocol for JonWebSignature"
  (set-alg [this alg] "Set algorithm name to JWS Header Parameter")
  (sign    [this key] [this alg key] "Set signature to this token"))

(extend-protocol JsonWebSignature
  JWT
  (set-alg [this alg]
    (assoc-in this [:header :alg] (name alg)))

  (sign
    ([this key] (sign this DEFAULT_SIGNATURE_ALGORITHM key))
    ([this alg key]
     (let [this*   (set-alg this alg)
           sign-fn (comp url-safe-encode (get-signature-fn alg))
           data    (str (encoded-header this*) "." (encoded-claims this*))]
       (assoc this* :signature (sign-fn key data))))))


; =jwt
(defn jwt [claim]
  (init (->JWT "" "" "")
        (reduce #(update-map % %2 to-intdate) claim [:exp :nbf :iot])))


