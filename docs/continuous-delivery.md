# Continuous Delivery

## Mitä?

Pyritään pitämään palvelun lähdekoodi sellaisessa kunnossa, että se voidaan vielä
koska tahansa tuotantoon. Aloite tuotantoonviennistä voi tapahtua tuoteomistajan
aloitteesta, "nyt on featuret kasassa". Liittyy läheisesti jatkuvaan
tuotantoonvientiin (Continuous Deployment).

## Miksi?

* Softa pysyy paremmassa kunnossa, keskeneräisyyksien hallinta
* Nopeammin tuotantoon --> nopeammin arvoa (ja palautetta)


## Miten?

* Continuous Integration
* Continous Deployment
* Feature Flags

## Feature Flags

Ominaisuudet kätketään boolean-lippujen taakse. Ominaisuuksien päälläoloa
hallitaan ympäristökohtaisesti.

## Lupapisteessä

Ominaisuudet kytketään päälle sovelluksen ympäristön asetustiedostoissa.
Tiedostot tulevat mukaan sovelluspakettiin.

Tiedostot ovat resources-kansiossa:
* local.properties (kehittäjän oma työasema)
* dev.properties
* test.properties
* qa.properties
* prod.properties

Rivin alkuun tulee `feature.<nimi>`. Jos arvo on true, on ominaisuus päällä.

```
# feature flags
feature.vrk             true
feature.poikkari        true
feature.neighbors       true
feature.neighborsz      false
```

Käytössä olevat ominaisuudet näkyvät ajoaikaisesti admin-käyttöliittymässä.

Ominaisuuksien käyttö Clojure-koodissa:

```clojure
(when (env/feature? :neighbors)
 (println "I have neighbors."))
```

Komentojen ja kyselyiden mahdollistaminen:

```clojure
(defcommand "neighbor-add"
  {:parameters [id]
   :feature    :neighbors
   :roles      [:authority]}
  [{data :data :as command}]
  (let [neighborId (mongo/create-id)]
    (update-application command
      {$set {(neighbor-path neighborId), (params->new-neighbor data)}})
    (ok :neighborId neighborId)))))
```

Ominaisuuksien käyttö JavaScript-koodissa:

```javascript
if (features.enabled('neighbors')) {
  console.out("I have neighbors.");
}
```

HTML ja KnockoutJS:

```HTML
<h1>BlaBla</h1>
<div data-bind="if: features.enabled('neighbors')">
  <div class="feature">
    <span class="legend">neighbors</span>
    I have neighbors.
  </div>
</div>
```

Tai lyhyemmin:

```HTML
<h1>BlaBla</h1>
<div data-bind="feature: 'neighbors'">
  <span class="legend">neighbors</span>
  I have neighbors.
</div>
```

## Lopullinen tuotantoonvienti

Kun ominaisuus on valmis vietäväksi tuotantoon, feature flag siivotaan pois.
Edellä kuvatut if:it siivotaan pois lähdekoodista ja feature-rivi poistetaan
jokaisesta properties-tiedostosta.



