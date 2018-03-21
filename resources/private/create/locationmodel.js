LUPAPISTE.CreateApplicationLocationModel = function(options) {
  "use strict";

  options = _.assign({mapId: "create-map", category: "Create", nextStep: "create-step-2"}, options);

  var self = this;
  LUPAPISTE.LocationModelBase.call(self,
      {mapId: options.mapId, initialZoom: 2, zoomWheelEnabled: true,
       afterClick: _.partial(hub.send, "track-click", {category: options.category, label:"map", event:"mapClick"}),
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

  var municipalityByPropertyId = ko.observable();
  ko.computed(function() {
    if (self.locationServiceUnavailable() && self.propertyIdOk()) {
      ajax.query("municipality-for-property", {propertyId: util.prop.toDbFormat(self.propertyId())})
        .success(function(resp) {
          municipalityByPropertyId(resp.municipality);
        }).call();
    } else {
      municipalityByPropertyId(null);
    }
  }).extend({ throttle: 200 });

  self.propertyInMunicipality = ko.computed(function() {
    return !self.locationServiceUnavailable() || municipalityByPropertyId() && self.municipalityCode() === municipalityByPropertyId();
  });

  self.propertyIdError = ko.computed(function() {
    if (municipalityByPropertyId() && !self.propertyInMunicipality()) {
      return loc("error.propertyId.municipality-mismatch");
    }
  });

  //
  // Search API
  //

  self.searchPoint = function(value, searchingListener) {
    if (!_.isEmpty(value)) {
      self.reset();
      return util.prop.isPropertyId(value) ? self._searchPointByPropertyId(value, searchingListener) : self._searchPointByAddress(value, searchingListener);
    }
    return self;
  };

  //
  // Private functions
  //

  self._searchPointByAddress = function(address, searchingListener) {
    locationSearch.pointByAddress(self.requestContext, address, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.location.x,
              y = data.location.y;
          self
            .setXY(x,y).center(13)
            .setAddress(data)
            .beginUpdateRequest()
            .searchPropertyId(x, y);
        }
      }, self.onError, searchingListener);
    return self;
  };

  self._searchPointByPropertyId = function(id, searchingListener) {
    locationSearch.pointByPropertyId(self.requestContext, id, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.x,
              y = data.y;
          self
            .setXY(x,y).center(14)
            .propertyId(util.prop.toDbFormat(id))
            .beginUpdateRequest()
            .searchAddress(x, y);
        }
      }, self.onError, searchingListener);
    return self;
  };

  self.proceed = _.partial(hub.send, options.nextStep);

};

LUPAPISTE.CreateApplicationLocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.CreateApplicationLocationModel});
