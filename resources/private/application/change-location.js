LUPAPISTE.ChangeLocationModel = function() {
  "use strict";

  var self = this;

  LUPAPISTE.LocationModelBase.call(self,
      {mapId:"change-location-map", initialZoom: 13, zoomWheelEnabled: false});

  self.dialogSelector = "#dialog-change-location";

  // Model

  self.id = 0;

  self.propertyIdAutoUpdated = true;
  self.errorMessage = ko.observable(null);

  self.ok = ko.computed(function() {
    return self.propertyIdOk && self.address() && self.propertyIdValidated();
  });

  self._setApplication = function(app) {
    self.id = app.id();
    self.x = app.location().x();
    self.y = app.location().y();
    self.address(app.address());
    self.propertyId(app.propertyId());
    self.municipalityCode(app.municipality());
    self.errorMessage(null);
    self.clearMap().center(14);
    self.processing(false);
    self.pending(false);
    self.propertyIdValidated(true);
  };

  //
  // Concurrency control
  //

  self.beginUpdateRequest = function() {
    self.errorMessage(null);
    self.requestContext.begin();
    return self;
  };

  //
  // Event handlers
  //

  self.propertyId.subscribe(function(id) {
    if (!id || util.prop.isPropertyIdInDbFormat(id)) {
      self.propertyIdAutoUpdated = true;
    }

    var human = util.prop.toHumanFormat(id);
    if (human !== id) {
      self.propertyId(human);
    } else {
      if (!self.propertyIdAutoUpdated && util.prop.isPropertyId(id)) {
        // Id changed from human format to valid human format:
        // Turing test passed, search for new location
        self.beginUpdateRequest().searchLocationByPropertyId(id);
      }
      self.propertyIdAutoUpdated = false;
      self.propertyIdValidated(false);
    }
  });

  // Saving

  self.onSuccess = function() {
    self.errorMessage(null);
    repository.load(self.id);
    LUPAPISTE.ModalDialog.close();
  };

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  self.saveNewLocation = function() {
    if (self.ok()) {
      var data = self.toJS();
      data.id = self.id;
      ajax.command("change-location", data)
        .processing(self.processing)
        .pending(self.pending)
        .success(self.onSuccess)
        .error(self.onError)
        .call();
      hub.send("track-click", {category:"Application", label:"", event:"locationChanged"});
    }
    return false;
  };

  // Open the dialog

  self.changeLocation = function(app) {
    self._setApplication(app);
    self.drawLocation();
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
    hub.send("track-click", {category:"Application", label:"", event:"changeLocation"});
  };

  // Service functions

  self.searchLocationByPropertyId = function(propertyId) {
    locationSearch.locationByPropertyId(self.requestContext, propertyId, function(result) {
        if (result.x && result.y) {
          self.setXY(result.x, result.y);
          self.center();
          self.propertyIdValidated(true);
        } else {
          self.errorMessage("error.invalid-property-id");
        }
      });
    return self;
  };

};

LUPAPISTE.ChangeLocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.ChangeLocationModel});
