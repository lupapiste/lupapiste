// Model for the KnockoutJS template wrapping around the property list Reagent component
LUPAPISTE.DocgenPropertyListModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  // The field names used to sync the properties on the map and the list
  // and identify the fields for automatic information fetching
  self.propertyIdField = params.propertyIdField || "kiinteistotunnus";
  self.ownersField = params.ownersField || "omistajat";
  self.estateNameField = params.estateNameField || "tilanNimi";
  self.registerDateField = params.registerDateField || "rekisterointiPvm";

  self.validationResults = ko.observable([]);

  self.disabled = self.disposedPureComputed( function () {
    return !self.authModel.ok(self.service.getUpdateCommand(self.documentId));
  });

  // The localization path for the whole property list
  self.title = _.reduce(
    _.concat(params.i18npath,["_group_label"]),
    function(acc, n) {
      return acc + "." + n;
    });

  // Unwrap the observables so they are easier to handle in the component
  self.properties = self.disposedPureComputed(function() {
    return ko.toJS(self.groups());
  });

  function indicator(evt) {
    if (evt.type === "err") {
      hub.send("indicator", {style: "negative", message: "form.err"});
    }
  }

  // Get the validation results for this component and store them in an observable to pass onto the list itself
  function updateValidation(result) {
    if (result.results) {
      self.validationResults(_.filter(result.results, function (entry) {
        return _.isEqual(self.path, _.take(entry.path, self.path.length)) && entry.result;
      }));
    }
  }

  // Functions for automatically filling a field group's fields
  function getPropertyId(index) {
    var property = _.find(self.properties(), function(property) { return property.index === index; });
    return property && property.model[self.propertyIdField] && property.model[self.propertyIdField].model;
  }

  function fetchOwners(index) {
    var propertyId = getPropertyId(index);
    if (propertyId) {
      ajax.datatables("owners", { propertyIds: [propertyId] })
        .success(function( result ) {
          var path = _.concat(self.path, [index, self.ownersField]);
          _.forEach(result.owners, function (owner) {
            var contact = owner.yhteyshenkilo ? owner.yhteyshenkilo : owner;
            var data = _.mapValues({
              etunimi:              contact.etunimet,
              sukunimi:             contact.sukunimi,
              nimi:                 owner.nimi,
              yritystunnus:         owner.ytunnus,
              osoite:               contact.jakeluosoite,
              postinumero:          contact.postinumero,
              postitoimipaikannimi: contact.paikkakunta,
              henkilolaji:          owner.henkilolaji
            }, function( value ) { return { value: value ? value : ""}; });
            self.service.addRepeatingGroupWithData(self.documentId, path, indicator, data);
          });
        })
        .error(indicator)
        .call();
    }
  }

  function fetchOwnersOk(index) {
    return !_.isEmpty(getPropertyId(index)) && lupapisteApp.models.applicationAuthModel.ok("owners");
  }

  self.fetchFns = {};
  self.fetchFns[self.ownersField] = {
    "fetch-fn": fetchOwners,
    "ok-fn":    fetchOwnersOk
  };

  // Update validation results on initialization and show-when updates
  updateValidation({ results: params.validationErrors });
  self.addHubListener("docgen-validation-results",
    function(results) { updateValidation({ results: results }); });

  // OpenLayers hides the canvas on an invisible element
  // and does not restore it if the element is made visible again;
  // however, it does react to window resize events to update the canvas so send a fake event
  // here to redraw the map component which is now visible and whose canvas has to be updated.
  // see https://github.com/openlayers/openlayers/issues/4817
  self.addHubListener("visible-elements-changed", function() {
    window.dispatchEvent(new Event("resize"));
  });


  // Utility functions to CRUD the document rows
  function doCreateRow(path, data) {
    self.service.addRepeatingGroupWithData(self.documentId, path, indicator, data);
  }

  self.createRow = function(path, data) {
    if (!data.kiinteistotunnus || !lupapisteApp.models.applicationAuthModel.ok("building-site-information")) {
      doCreateRow(path, data);
    } else {
      // Automatically fill in additional information about the property when the property id is given
      ajax.datatables("building-site-information", { propertyId: data.kiinteistotunnus.value })
        .success(function( result ) {
          data[self.estateNameField] = {value: result.nimi};
          data[self.registerDateField] = {value: result.rekisterointipvm};
          doCreateRow(path, data);
        })
        .error(function( evt ) {
          indicator(evt);
          doCreateRow(path, data);
        })
        .call();
    }
  };

  self.updateField = function(path, value) {
    self.service.updateDoc(self.documentId, [[path, value]], indicator, updateValidation);
    // Update the repeating group model as well so the UI is updated
    var field = self.service.getInDocument(self.documentId, path).model;
    if (ko.isObservable(field)) {
      field(value);
    }
  };

  self.deleteRow = function(pathWithIndex) {
    hub.send(
      "show-dialog", {ltitle: "areyousure",
        size: "medium",
        component: "yes-no-dialog",
        componentParams:
          {
            text: window.loc( "kiinteistoLista.confirm.remove"),
            noFn: function()
            {
              // Make sure the map-component's property list is up-to-date in case removal was triggered
              // from within the map-component and the propert was removed from its internal state
              self.groups(self.groups());
            },
            yesFn: function()
            {
              var path = _.take(pathWithIndex, pathWithIndex.length - 1);
              var index = _.last(pathWithIndex);
              self.service.removeRepeatingGroup(self.documentId, path, index, indicator, updateValidation);
            }}});
  };
};
