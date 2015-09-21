LUPAPISTE.ChangeLocationModel = function() {
  "use strict";

  var self = this;
  self.dialogSelector = "#dialog-change-location";

  var _map = null;

  self.map = function() {
    if (!_map) {
      _map = gis
        .makeMap("change-location-map", false)
        .center(404168, 6693765, 13)
        .addClickHandler(function(x, y) {
          self
            .address("")
            .propertyId("")
            .beginUpdateRequest()
            .setXY(x, y)
            .searchPropertyId(x, y)
            .searchAddress(x, y);
          return false;
        });
    }
    return _map;
  };

  // Model

  self.id = 0;
  self.x = 0;
  self.y = 0;
  self.address = ko.observable("");
  self.propertyId = ko.observable("");
  self.propertyIdValidated = ko.observable(true);
  self.propertyIdAutoUpdated = true;
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable();
  self.pending = ko.observable();


  self.ok = ko.computed(function() {
    return util.prop.isPropertyId(self.propertyId()) && self.address() && self.propertyIdValidated();
  });

  self.drawLocation = function() {
    return self.map().clear().add({x: self.x, y: self.y});
  };

  self.setXY = function(x, y) {
    self.x = x;
    self.y = y;
    self.drawLocation();
    return self;
  };

  self.center = function(zoom) {
    self.map().center(self.x, self.y, zoom);
  };

  self.reset = function(app) {
    self.id = app.id();
    self.x = app.location().x();
    self.y = app.location().y();
    self.address(app.address());
    self.propertyId(app.propertyId());
    self.errorMessage(null);
    self.map().clear().updateSize();
    self.center(14);
    self.processing(false);
    self.pending(false);
    self.propertyIdValidated(true);
  };

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
        self.beginUpdateRequest().searchPointByPropertyId(id);
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
      var data = {id: self.id, x: self.x, y: self.y, address: self.address(), propertyId: util.prop.toDbFormat(self.propertyId())};
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
    self.reset(app);
    self.drawLocation();
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
    hub.send("track-click", {category:"Application", label:"", event:"changeLocation"});
  };

  // Service functions

  self.searchPointByPropertyId = function(propertyId) {
    locationSearch.pointByPropertyId(self.requestContext, propertyId, function(result) {
        if (result.data && result.data.length > 0) {
          self.setXY(result.data[0].x, result.data[0].y);
          self.center();
          self.propertyIdValidated(true);
        } else {
          self.errorMessage("error.invalid-property-id");
        }
      });
    return self;
  };

  self.searchPropertyId = function(x, y) {
    locationSearch.propertyIdByPoint(self.requestContext, x, y, function(id) {
      self.propertyId(id);
      self.propertyIdValidated(true);
    });
    return self;
  };

  self.searchAddress = function(x, y) {
    locationSearch.addressByPoint(self.requestContext, x, y, function(a) {
      var newAddress = "";
      if (a) {
        newAddress = a.street;
        if (a.number && a.number !== "0") {
          newAddress = newAddress + " " + a.number;
        }
      }
      self.address(newAddress);
      self.center();
      hub.send("track-click", {category:"Application", label:"map", event:"changeLocationOnMap"});
    });
    return self;
  };

};
