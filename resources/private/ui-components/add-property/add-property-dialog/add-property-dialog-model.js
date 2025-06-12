

LUPAPISTE.AddPropertyDialogModel = function() {
  "use strict";
  var self = this;
  var app = lupapisteApp.models.application;
  self.x = app.location().x();
  self.y = app.location().y();
  self.propertyIdInHumanFormat = ko.observable(util.prop.toHumanFormat(app.propertyId()));
  self.selectedValidPropertyId = ko.pureComputed(function () {
    return util.prop.isPropertyId(self.propertyIdInHumanFormat());});
  self.propertyIdInDbFormat = ko.pureComputed(function() {
    return self.selectedValidPropertyId() ? util.prop.toDbFormat(self.propertyIdInHumanFormat()) : "";});
  self.selectedNotAppProperty = ko.pureComputed(function() {
    return self.propertyIdInDbFormat() !== app.propertyId();
  });
  self.submitIsEnabled = ko.pureComputed(function() {
    return self.selectedValidPropertyId() && self.selectedNotAppProperty();
  });
  self.propertyIdAutoUpdated = false;
  self.propertyIdNotFoundFromBc = ko.observable(false);
  self.errorMessage = ko.observable(null);

  //
  // Concurrency control
  //

  self.requestContext = new RequestContext();

  self.beginUpdateRequest = function() {
    self.errorMessage(null);
    self.requestContext.begin();
    return self;
  };

  //
  // Event handlers
  //

  self.propertyIdInHumanFormat.subscribe(function(id) {
    self.propertyIdNotFoundFromBc(false);
    if (!self.propertyIdAutoUpdated && util.prop.isPropertyId(id)) {
      self.beginUpdateRequest().searchLocationByPropertyId(id);
    }
    self.propertyIdAutoUpdated = false;
  });

  //
  // Map creation and map actions
  //

  function resolvePropertyId(x, y) {
    ajax
        .get("/proxy/property-id-by-point")
        .param("x", x)
        .param("y", y)
        .success(function(propertyId) {
          self.propertyIdAutoUpdated = true;
          self.propertyIdInHumanFormat(util.prop.toHumanFormat(propertyId));
        })
        .fail(_.noop)
        .call();
  }

  function moveMarkerAndCenterMap(x, y) {
    self.map().clear().add({x: x, y: y});
    self.map().center(x, y, 14);
  }

  self._map = null;
  self.map = function() {
    if (!self._map) {
      self._map = gis
        .makeMap("add-location-map", {zoomWheelEnabled: false})
        .addClickHandler(function(x, y) {
          self.x = x;
          self.y = y;
          resolvePropertyId(x, y);
          moveMarkerAndCenterMap(x, y);
          return false;
        });
    }
    return self._map;
  };

  moveMarkerAndCenterMap(self.x, self.y);

  //
  // Service functions
  //

  function onSuccess (result) {
    if (result.x && result.y) {
      moveMarkerAndCenterMap(result.x, result.y);
    } else {
      self.propertyIdNotFoundFromBc(true);
    }
  }

  function onFail (result) {
    if (result.status === 404) {
      self.propertyIdNotFoundFromBc(true);
    } else {
      self.errorMessage("error.unable-to-fetch-property-info-from-background");
    }
  }

  self.searchLocationByPropertyId = function(propertyId) {
    locationSearch.locationByPropertyId(self.requestContext, propertyId, onSuccess, onFail);
    return self;
  };

  //
  // Submit action
  //

  self.submit = function() {
    if (self.selectedValidPropertyId()) {
      var updates = [["kiinteisto.kiinteistoTunnus", self.propertyIdInDbFormat()]];
      var id = app.id(); // app is disposed after dialog is closed
      ajax
          .command("create-doc", { id: id,
            schemaName: "secondary-kiinteistot",
            updates: updates,
            fetchRakennuspaikka: true })
          .success(function() { repository.load(id); })
          .call();
      hub.send("close-dialog");
    }
    self.errorMessage("error.invalid-property-id");
  };

  self.dispose = function() {
    self.selectedNotAppProperty.dispose();
    self.selectedNotAppProperty = null;
    if (self._map) {
      self._map.destroy();
      self._map = null;
    }
    app = null;
  };

};


