;(function() {
  "use strict";

  function isBlank(s) { var v = _.isFunction(s) ? s() : s; return !v || /^\s*$/.test(v); }
  function isPropertyId(s) { return /^[0-9\-]+$/.test(s); }

  var tree;
  
  function operations2tree(e) {
    var key = e[0], value = e[1];
    return [{op: key}, _.isArray(value) ? _.map(value, operations2tree) : {op: value}];
  }
  
  var model = new function() {
    var self = this;

    self.goPhase1 = function() {
      $('.selected-location').hide();
      $("#create-part-1").show();
      $("#create-part-2").hide();
      $("#create-part-3").hide();
    };
    
    self.goPhase2 = function() {
      tree.reset(_.map(self.municipality().operations, operations2tree));
      $("#create-part-1").hide();
      $("#create-part-2").show();
      window.scrollTo(0, 0);
    };

    self.goPhase3 = function() {
      $("#create-part-2").hide();
      $("#create-part-3").show();
      window.scrollTo(0, 0);
    };

    self.useManualEntry = ko.observable(false);
    
    self.map = null;

    self.search = ko.observable("");
    self.x = ko.observable(0.0);
    self.y = ko.observable(0.0);
    self.addressData = ko.observable(null);
    self.addressString = ko.observable(null);
    self.propertyId = ko.observable(null);
    self.municipality = ko.observable(null);
    self.municipalityCode = ko.observable(null);
    self.municipalityName = ko.observable();
    self.municipalitySupported = ko.observable(true);
    
    self.municipalityCode.subscribe(function(code) {
      if (self.useManualEntry()) municipalities.findById(code, self.municipality);
    });
    
    self.findMunicipality = function(code) {
      municipalities.findById(code, function(m) {
        self
          .municipality(m)
          .municipalitySupported(m ? true : false);
      });
      return self;
    }
    
    self.addressData.subscribe(function(a) {
      if (a) {
        self
          .addressString(a.street + " " + a.number)
          .municipalityCode(a.municipality)
          .municipalityName(a.name[loc.currentLanguage])
          .findMunicipality(a.municipality);
      }
    });
    
    self.propertyId.subscribe(function(id) {
      if (id) {
        self.municipalityCode(id.substring(0, 3));
      }
    });

    self.operation = ko.observable();
    self.message = ko.observable("");
    self.requestType = ko.observable();

    self.clear = function() {
      self.goPhase1();  
      if (!self.map) {
        self.map = gis.makeMap("create-map").center(404168, 7205000, 0);
        self.map.addClickHandler(self.click);
      }
      self.map.clear().updateSize();
      return self
        .search("")
        .x(0)
        .y(0)
        .addressData(null)
        .addressString("")
        .propertyId(null)
        .municipalityCode(null)
        .message("")
        .requestType(null)
        .goPhase1();
    };

    self.resetXY = function() { if (self.map) { self.map.clear(); } return self.x(0).y(0);  };
    self.setXY = function(x, y) { if (self.map) { self.map.clear().add(x, y); } return self.x(x).y(y); };
    self.center = function(x, y, zoom) { if (self.map) { self.map.center(x, y, zoom); } return self; };
    
    self.addressOk = ko.computed(function() { return self.municipality() && !isBlank(self.addressString()); });

    //
    // Concurrency control:
    //

    self.updateRequestId = 0;
    self.beginUpdateRequest = function() { self.updateRequestId++; return self; };

    //
    // Callbacks:
    //

    // Called when user clicks on map:

    self.click = function(x, y) {
      self
        .setXY(x, y)
        .addressData(null)
        .propertyId(null)
        .beginUpdateRequest()
        .searchPropertyId(x, y)
        .searchAddress(x, y);
      return false;
    };

    self.onResponse = function(fn) {
      var requestId = self.updateRequestId;
      return function(result) { if (requestId === self.updateRequestId) fn(result); };
    };

    // Search activation:

    self.searchNow = function() {
      $('.selected-location').show();
      self
        .resetXY()
        .addressData(null)
        .propertyId(null)
        .beginUpdateRequest()
        .searchPointByAddressOrPropertyId(self.search());
      self.map.updateSize();
      return false;
    };

    self.searchPointByAddressOrPropertyId = function(value) { return isPropertyId(value) ? self.searchPointByPropertyId(value) : self.serchPointByAddress(value); };

    self.serchPointByAddress = function(address) {
      ajax
        .get("/proxy/get-address")
        .param("query", address)
        .success(self.onResponse(function(result) {
          if (result.data && result.data.length > 0) {
            var data = result.data[0],
                x = data.x,
                y = data.y;
            self
              .useManualEntry(false)
              .setXY(x, y)
              .center(x, y, 11)
              .addressData(data)
              .beginUpdateRequest()
              .searchPropertyId(x, y);
          }
        }))
        .fail(_.partial(self.useManualEntry, true))
        .call();
      return self;
    };

    self.searchPointByPropertyId = function(id) {
      ajax
        .get("/proxy/point-by-property-id")
        .param("property-id", id)
        .success(self.onResponse(function(result) {
          if (result.data && result.data.length > 0) {
            var data = result.data[0],
                x = data.x,
                y = data.y;
            self
              .useManualEntry(false)
              .setXY(x, y)
              .center(x, y, 11)
              .propertyId(id)
              .beginUpdateRequest()
              .searchAddress(x, y);
          }
        }))
        .fail(_.partial(self.useManualEntry, true))
        .call();
      return self;
    };

    self.searchPropertyId = function(x, y) {
      ajax
        .get("/proxy/property-id-by-point")
        .param("x", x)
        .param("y", y)
        .success(self.onResponse(self.propertyId))
        .call();
      return self;
    };

    self.searchAddress = function(x, y) {
      ajax
        .get("/proxy/address-by-point")
        .param("x", x)
        .param("y", y)
        .success(self.onResponse(self.addressData))
        .call();
      return self;
    };

    self.create = function(infoRequest) {
      ajax.command("create-application", {
        infoRequest: infoRequest,
        operation: self.operation(),
        y: self.y(),
        x: self.x(),
        address: self.addressString(),
        propertyId: self.propertyId(),
        messages: isBlank(self.message()) ? [] : [self.message()],
        municipality: self.municipality().id
      })
      .success(function(data) {
        setTimeout(self.clear, 0);
        window.location.hash = (infoRequest ? "!/inforequest/" : "!/application/") + data.id;
      })
      .call();
    };

    self.createApplication = self.create.bind(self, false);
    self.createInfoRequest = self.create.bind(self, true);

  }();

  hub.onPageChange("create", model.clear);
  
  $(function() {

    $("#create").applyBindings(model);

    $("#create-search")
      .keypress(function(e) {
        if (e.which === 13) model.searchNow();
      })
      .autocomplete({
        serviceUrl:      "/proxy/find-address",
        deferRequestBy:  500,
        noCache:         true,
        onSelect:        model.searchNow
      });

    tree = $("#create .operation-tree").selectTree({
      template: $("#create-templates"),
      onSelect: function(v) { model.operation(v ? v.op : null); },
      baseModel: model
    });
    
    hub.subscribe({type: "keyup", keyCode: 37}, tree.back);
    hub.subscribe({type: "keyup", keyCode: 33}, tree.start);
    hub.subscribe({type: "keyup", keyCode: 36}, tree.start);
    
  });

})();
