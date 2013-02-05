;(function() {
  "use strict";

  function isBlank(s) { var v = _.isFunction(s) ? s() : s; return !v || /^\s*$/.test(v); }
  function isPropertyId(s) { return /^[0-9\-]+$/.test(s); }
  
  var model = new function() {
    var self = this;

    self.goPhase1 = function() {
      $("#create-part-1").find("h2").accordionOpen().end().show();
      $("#create-part-2").find("h2").accordionClose().end().hide();
      $("#create-part-3").find("h2").accordionClose().end().hide();
    };
    
    self.goPhase2 = function() {
      $("#create-part-1")
        .find("h2")
        .accordionClose(function() { $("#create-part-2").show().find("h2").accordionOpen(); });
    };
    
    self.goPhase3 = function() {
      $("#create-part-2")
        .find("h2")
        .accordionClose(function() { $("#create-part-3").show().find("h2").accordionOpen(); });
    };
    
    self.municipalities = ko.observableArray([]);
    self.map = null;

    self.search = ko.observable("");
    self.x = ko.observable(0.0);
    self.y = ko.observable(0.0);
    self.address = ko.observable("");
    self.municipality = ko.observable("");
    self.municipalityCode = ko.observable(null);
    self.propertyId = ko.observable("");

    self.operation = ko.observable();
    self.message = ko.observable("");
    self.links = ko.observableArray();
    self.operations = ko.observable(null);
    self.requestType = ko.observable();

    self.addressOk = ko.computed(function() { return !isBlank(self.municipalityCode) && !isBlank(self.address); });
    
    self.clear = function() {
      if (self.map) self.map.clear().updateSize();
      return self
        .search("")
        .x(0)
        .y(0)
        .address("")
        .municipality("")
        .municipalityCode(null)
        .propertyId("")
        .operation(null)
        .message("")
        .requestType(null)
        .goPhase1();
    };

    self.setMap = function(map) {
      self.map = map;
      self.map.addClickHandler(self.click);
      return self;
    };

    self.resetXY = function() { if (self.map) self.map.clear(); return self.x(0).y(0);  };
    self.setXY = function(x, y) { if (self.map) self.map.clear().add(x, y); return self.x(x).y(y); };
    self.center = function(x, y) { if (self.map) self.map.center(x, y); return self; };
    self.setPropertyId = function(value) { return  self.propertyId(value); };
    self.setMunicipality = function(value) { return isBlank(value) ? self.municipalityCode(null).municipality("") : self.municipalityCode(value).municipality(loc("municipality." + value)); };
    self.setAddress = function(data) { return data ? self.address(data.katunimi + " " + data.katunumero + ", " + data.kuntanimiFin) : self.address(""); };

    self.municipalityCode.subscribe(function(v) {
      self.operations(null).links.removeAll();
      if (!v || v.length === 0) return;
      ajax
        .query("municipality", {municipality: v})
        .success(function (data) { if (self.municipalityCode() === v) self.operations(data.operations).links(data.links); })
        .call();
    });

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
        .setPropertyId("")
        .setMunicipality("")
        .beginUpdateRequest()
        .searchMunicipality(x, y)
        .searchPropertyId(x, y);
      return false;
    };

    // Search activation:

    self.searchNow = function() { self.beginUpdateRequest().searchPointByAddressOrPropertyId(self.search()); return false; };
    self.searchSoon = _.debounce(self.searchNow, 500);

    self.search.subscribe(function(v) {
      self.resetXY().setAddress(null).setPropertyId("").setMunicipality("");
      if (!isBlank(v)) self.searchSoon();
      return false;
    });

    self.searchPointByAddressOrPropertyId = function(value) { return isPropertyId(value) ? self.searchPointByPropertyId(value) : self.serchPointByAddress(value); };

    self.serchPointByAddress = function(address) {
      var requestId = self.updateRequestId;
      ajax
        .get("/proxy/get-address")
        .param("query", address)
        .success(self.makeSearchDone(requestId))
        .call();
      return self;
    };

    self.searchPointByPropertyId = function(propertyId) {
      var requestId = self.updateRequestId;
      ajax
        .get("/proxy/point-by-property-id")
        .param("property-id", propertyId)
        .success(self.makeSearchDone(requestId))
        .call();
      return self;
    };

    self.makeSearchDone = function(requestId) {
      return self.makeSuccess(requestId, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0], x = data.x, y = data.y;
          self
            .setXY(x, y)
            .center(x, y)
            .setAddress(data)
            .beginUpdateRequest()
            .searchMunicipality(x, y)
            .searchPropertyId(x, y);
        }
      });
    };

    self.makeSuccess = function(requestId, fn) {
      return function(result) { if (requestId === self.updateRequestId) fn(result); };
    };

    self.searchMunicipality = function(x, y) {
      var requestId = self.updateRequestId;
      ajax
        .query("municipality-by-location", {x: x, y: y})
        .success(self.makeSuccess(requestId, function(data) { self.setMunicipality(data.result); }))
        .error(function() { if (requestId === self.updateRequestId) self.setMunicipality(null); })
        .call();
      return self;
    };

    self.searchPropertyId = function(x, y) {
      var requestId = self.updateRequestId;
      ajax
        .get("/proxy/property-id-by-point")
        .param("x", x)
        .param("y", y)
        .success(self.makeSuccess(requestId, self.setPropertyId))
        .call();
      return self;
    };

    self.setAddressData = function(data) {
      self
        .setXY(data.x, data.y)
        .setAddress(data)
        .setMunicipality(data.kuntatunnus)
        .beginUpdateRequest()
        .searchPropertyId(data.x, data.y);
    };

    self.getMunicipalityName = function(m) {
      return m.name[loc.getCurrentLanguage()];
    };

    self.create = function(infoRequest) {
      ajax.command("create-application", {
        infoRequest: infoRequest,
        permitType: infoRequest ? "infoRequest" : "buildingPermit", // FIXME: WTF this should be?
        operation: self.operation()["op"],
        y: self.y(),
        x: self.x(),
        address: self.address(),
        propertyId: self.propertyId(),
        messages: isBlank(self.message()) ? [] : [self.message()],
        municipality: self.municipalityCode()
      })
      .success(function(data) {
        window.location.hash = (infoRequest ? "!/inforequest/" : "!/application/") + data.id;
      })
      .call();
    };

    self.createApplication = self.create.bind(self, false);
    self.createInfoRequest = self.create.bind(self, true);

  };

  function toLink(l) {
    return $("<li>")
      .append($("<a>").attr("href", l.url).attr("target", "_blank")
              .text(l.name[loc.getCurrentLanguage()]));
  }

  function generateInfo(value) {
    var e$ = $("<div>").attr("class", "tree-result").append($("<p>").text(loc(value.text)));
    var ul$ = $("<ul>");
    _.each(_.map(model.links(), toLink), function (l) {ul$.append(l);});
    return e$.append(ul$)[0];
  }

  hub.onPageChange("create", model.clear);
 
  ajax
    .query("municipalities")
    .success(function(data) { model.municipalities(data.municipalities); })
    .call();

  $(function() {
    $("#create-accordion-1").show().find("h2").accordionOpen();
    $("#create-accordion-2").show().find("h2").accordionOpen();
    $("#create-accordion-3").show().find("h2").accordionOpen();
    
    model.setMap(gis.makeMap("create-map").center(404168, 7005000, 0));
    ko.applyBindings(model, $("#create")[0]);

    $("#create-search").autocomplete({
      serviceUrl:      "/proxy/find-address",
      deferRequestBy:  500,
      noCache:         true,
      onSelect:        function(value, data) { model.setAddressData(data); }
    });

    var tree = selectionTree.create(
        $("#create .tree-content"),
        $("#create .tree-breadcrumbs"),
        model.operation,
        generateInfo,
        "operations");

    model.operations.subscribe(tree.reset);
  });

})();
