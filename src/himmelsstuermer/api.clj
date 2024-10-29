(ns himmelsstuermer.api
  (:require
    [himmelsstuermer.impl.api :as impl]))


(defn delete-message

  "Delete message with ID `mid` for `user`"

  {:added "0.1.0"}

  [state user mid]
  (impl/delete-message state user mid))


(defn send-message

  "Sends text message to `user`.

  Varargs by type:

  | argument predicate                   | description | comment |
  |----------------------------------|--------------|---------|
  | `string?`  | Message text | |
  | `vector?`  | Message keyboard | |
  | `set?`     | Set of Message Entities | |
  | `int?`     | ID of Text Message to edit | |
  | `keyword?` | One of options | |

  Possible Options:

  | option      | description | comment |
  |-------------|-------------|---------|
  | `:modal`     | Sends 'modaloral' message that appears with notification under 'main' one. This message will have button to delete it in the end | |
  | `:markdown` | Messsage will use Markdown parse_mode | |"

  {:changed "0.1.0"}

  [state user & args]
  (apply impl/send! :text state user args))


;; (defn send-photo

;;   "Sends photo message with picture from java.io.File in `file` as a modalorary message with caption `caption` and inline keyboard `kbd` to `user`.
;;   Possible `opts`:

;;   | option      | description |
;;   |-------------|-------------|
;;   | `:markdown` | Messsage will use Markdown parse_mode |
;;   | Long value  | Temporal Message ID you want to edit. It must be media message. `nil` as `kbd` value then means to leave keyboard layout unchanged |"

;;   {:added "0.1.0"}

;;   [user file caption kbd & opts]
;;   (apply impl/prepare-and-send :photo user {:file file :caption caption} kbd :modal opts))


;; (defn send-document

;;   "Sends java.io.File in `file` as a modalorary message with caption `caption` and inline keyboard `kbd` to `user`.
;;   Possible `opts`:

;;   | option      | description | comment |
;;   |-------------|-------------|---------|
;;   | `:markdown` | Messsage will use Markdown parse_mode | |
;;   | Long value  | Temporal Message ID you want to edit. It must be media message. `nil` as `kbd` value then means to leave keyboard layout unchanged | since 0.9.0 |"

;;   {:added "0.1.0"}

;;   [user file caption kbd & opts]
;;   (apply impl/prepare-and-send :document user {:file file :caption caption} kbd :modal opts))


(defn send-invoice

  "Sends invoice as 'modaloral' message with inline keyboard `kbd` to `user`. Keyboard will have payment button with `text` in the beginning and button to delete it in the end.
  Description of `data` map (all keys required):

  | key               | description |
  |-------------------|-------------|
  | `:title`          | Product name, 1-32 characters
  | `:description`    | Product description, 1-255 characters
  | `:payload`        | Bot-defined invoice payload, 1-128 bytes. This will not be displayed to the user, use for your internal processes.
  | `:provider_token` | Payment provider token
  | `:currency`       | Three-letter ISO 4217 currency code
  | `:prices`         | Price breakdown, a JSON-serialized list of components (e.g. product price, tax, discount, delivery cost, delivery tax, bonus, etc.). Each component have to be map with keys `:label` (string) and `:amount` (integer price of the product in the smallest units of the currency)"

  {:added "0.1.0"}

  ([state user data text] (send-invoice state user data text []))
  ([state user data text kbd]
   (impl/send! :invoice state user text data kbd)))


(defn get-file

  "Returns `java.io.File` or `nil` by `file-id`."

  {:added "0.1.0"}

  [state file-id]
  (impl/get-file state file-id))
