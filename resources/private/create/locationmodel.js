LUPAPISTE.CreateApplicationLocationModel = function(options) {
  "use strict";

  options = _.assign({mapId: "create-map", category: "Create", nextStep: "create-step-2"}, options);

  var self = this;
  LUPAPISTE.LocationModelBase.call(self,
      {mapId: options.mapId, initialZoom: 2, zoomWheelEnabled: true,
        popupContentModel: "section#map-popup-content"});

  self.municipalitySupported = ko.observable(true);

  ko.computed(function() {
    var code = self.municipalityCode();
    self.municipalitySupported(true);
    if (code) {
      municipalities.findById(code, function(m) {
        self.municipalitySupported(m ? true : false);
      });
    }
  });

  self.propertyIdForCreateApplication = ko.pureComputed({
    read: self.propertyIdHumanReadable,
    write: self.propertyId
  });

  self.projectType = "";

  ko.computed(function() {
    if (self.locationServiceUnavailable() && self.propertyIdOk()) {
      ajax.query("municipality-for-property", {propertyId: util.prop.toDbFormat(self.propertyId())})
        .processing(self.processing)
        .success(function(resp) {
          self.municipalityCode(resp.municipality);
        }).call();
    }
  }).extend({ throttle: 200 });

  self.propertyIdNotOk = ko.pureComputed(function() { // show warning when something is inputted, blank is ok
    return !(_.isBlank(self.propertyId()) || self.propertyIdOk());
  });

  //
  // Search API
  //

  self.searchPoint = function(value, searchingListener) {
    if (!_.isEmpty(value)) {
      self.reset();
      return util.prop.isPropertyId(value) ? self._searchLocationByPropertyId(value, searchingListener) : self._searchPointByAddress(value, searchingListener);
    }
    return self;
  };

  //
  // Private functions
  //

  self._searchPointByAddress = function(address, searchingListener) {
    locationSearch.pointByAddress(self.requestContext, address, function(result) {
        if (result.data && result.data.length > 0) {
          self.locationServiceUnavailable(false);
          var data = result.data[0],
              x = data.location.x,
              y = data.location.y;
          self
            .setXY(x,y).center(13)
            .municipalityCode(data.municipality || "")
            .setAddress(data)
            .beginUpdateRequest()
            .searchPropertyId(x, y); // we could use searchPropertyInfo here, but it seems property-id-by-point has slightly faster response times.
        }
      }, self.onError, searchingListener);
    return self;
  };

  self._searchLocationByPropertyId = function(id, searchingListener) {
    locationSearch.locationByPropertyId(self.requestContext, id, function(result) {
        if (result.x && result.municipality) {
          self.locationServiceUnavailable(false);
          var municipality = result.municipality,
              x = result.x,
              y = result.y;
          self
            .setXY(x,y).center(14)
            .propertyId(util.prop.toDbFormat(id))
            .municipalityCode(municipality)
            .beginUpdateRequest()
            .searchAddress(x, y);
        }
      }, self.onError, searchingListener);
    return self;
  };

  self.isArchiveProject = function() {
    return "ARK" === self.projectType;
  };

  self.proceed = _.partial(hub.send, options.nextStep);

};

LUPAPISTE.CreateApplicationLocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.CreateApplicationLocationModel});
