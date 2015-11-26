LUPAPISTE.LocationModel = function() {
  "use strict";

  var self = this;
  LUPAPISTE.LocationModelBase.call(self,
      {mapId:"create-map",
       initialZoom: 2,
       zoomWheelEnabled: true,
       clickHandler: function(x, y) {
         hub.send("track-click", {category:"Create", label:"map", event:"mapClick"});
         self.reset().setXY(x, y).beginUpdateRequest()
           .searchPropertyId(x, y)
           .searchAddress(x, y);
         return false;
       },
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

  self.reset = function() {
    return self.setXY(0,0).address("").propertyId("").municipalityCode("");
  };

  //
  // Search API
  //

  self.searchPoint = function(value) {
    if (!_.isEmpty(value)) {
      self.reset();
      return util.prop.isPropertyId(value) ? self._searchPointByPropertyId(value) : self._searchPointByAddress(value);
    }
    return self;
  };

  //
  // Private functions
  //

  self._searchPointByAddress = function(address) {
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
      }, self.onError, self.processing);
    return self;
  };

  self._searchPointByPropertyId = function(id) {
    locationSearch.pointByPropertyId(self.requestContext, id, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.x,
              y = data.y;
          self
            .setXY(x,y).center(14)
            .setPropertyId(util.prop.toDbFormat(id))
            .beginUpdateRequest()
            .searchAddress(x, y);
        }
      }, self.onError, self.processing);
    return self;
  };

  self.proceed = _.partial(hub.send, "create-step-2");

};

LUPAPISTE.LocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.LocationModel});
