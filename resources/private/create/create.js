;(function() {
  "use strict";

  function isBlank(s) { var v = _.isFunction(s) ? s() : s; return !v || /^\s*$/.test(v); }

  var tree;

  function operations2tree(e) {
    var key = e[0], value = e[1];
    return [{op: key}, _.isArray(value) ? _.map(value, operations2tree) : {op: value}];
  }

  var model = new function() {
    var self = this;

    self.goPhase1 = function() {
      // $('.selected-location').hide();
      $('.selected-location').show();
      $("#create-part-1").show();
      $("#create-part-2").hide();
      $("#create-part-3").hide();
      $("#create-search").focus();
      self.map.updateSize();
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
    self.organization = ko.observable(null);
    self.organizationLinks = ko.computed(function() { var m = self.organization(); return m ? m.links : null; });
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
    };

    self.addressData.subscribe(function(a) {
      self.addressString(a ? a.street + " " + a.number : "");
    });

    self.propertyId.subscribe(function(id) {
      var human = util.prop.toHumanFormat(id);
      if (human != id) {
        self.propertyId(human);
      } else {
        var code = id ? util.zeropad(3, id.split("-")[0].substring(0, 3)) : null;
        self
          .municipalityCode(code)
          .municipalityName(code ? loc("municipality", code) : null)
          .findMunicipality(code, self.municipality);
      }
    });

    self.operation = ko.observable();
    self.message = ko.observable("");
    self.requestType = ko.observable();

    self.attachmentsForOp = ko.computed(function() {
      var m = self.municipality(),
          ops = m && m["operations-attachments"],
          op = self.operation();
      return (ops && op) ? _.map(ops[op], function(a) { return {"group": a[0], "id": a[1]}; }) : [];
    });

    self.clear = function() {
      if (!self.map) {
        self.map = gis.makeMap("create-map").center(404168, 7205000, 0);
        self.map.addClickHandler(self.click);
      }
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

    self.requestContext = new RequestContext();
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };

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

    // Search activation:

    self.searchNow = function() {
      // $('.selected-location').show();
      self
        .resetXY()
        .addressData(null)
        .propertyId(null)
        .beginUpdateRequest()
        .searchPointByAddressOrPropertyId(self.search());
      return false;
    };
    
    var zoomLevel = {
        "540": 6,
        "550": 7,
        "560": 9
    };
    
    function zoomer(item) { self.center(item.location.x, item.location.y, zoomLevel[item.type] || 8); }
    function fillMunicipality(item) { $("#create-search").val(", " + loc("municipality", item.municipality)).caretToStart(); }
    
    var handlers = [
      [{kind: "poi", type: "540"}, fillMunicipality],  // kunnan nimi, kaupunki
      [{kind: "poi", type: "550"}, fillMunicipality],  // kunnan nimi, maaseutu
      [{kind: "poi", type: "575"}, fillMunicipality],  // maakunnan nimi 
      [{kind: "poi"}, zoomer],
      [{kind: "address"}, function(item) { self.center(item.location.x, item.location.y, 11); }],
      [{kind: "property-id"}, function(item) { self.center(item.location.x, item.location.y, 11); }]
    ];

    var renderers = [
      [{kind: "poi"}, function(item) {
        return $("<a>").html(item.text + " (" + loc("poi", item.type) + ", " + loc("municipality", item.municipality) + ")");
      }],
      [{kind: "address"}, function(item) {
        return $("<a>").html(item.street + ", " + loc("municipality", item.municipality));
      }]
    ];

    function selector(item) { return function(value) { return _.every(value[0], function(v, k) { return item[k] === v; }); }; }
    function toHandler(value) { return value[1]; }
    function invoker(item) { return function(handler) { return handler(item); }; } 
    
    self.autocompleteSelect = function(e, data) {
      console.log("SELECT:", data);
      var item = data.item;
      _(handlers).filter(selector(item)).map(toHandler).each(invoker(item));
      return false;
    }
    
    self.autocompleteRender = function(ul, data) {
      console.log("RENDER:", data);
      var element = _(renderers).filter(selector(data)).first(1).map(toHandler).map(invoker(data)).value();
      return $("<li>")
        .append(element)
        .appendTo(ul);
    };

    self.searchPointByAddressOrPropertyId = function(value) { return util.prop.isPropertyId(value) ? self.searchPointByPropertyId(value) : self.searchPointByAddress(value); };

    self.searchPointByAddress = function(address) {
      locationSearch.pointByAddress(self.requestContext, address, function(result) {
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
        }, _.partial(self.useManualEntry, true));
      return self;
    };

    self.searchPointByPropertyId = function(id) {
      locationSearch.pointByPropertyId(self.requestContext, id, function(result) {
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
        },
        _.partial(self.useManualEntry, true));
      return self;
    };

    self.searchPropertyId = function(x, y) {
      locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyId);
      return self;
    };

    self.searchAddress = function(x, y) {
      locationSearch.addressByPoint(self.requestContext, x, y, self.addressData);
      return self;
    };

    self.create = function(infoRequest) {
      ajax.command("create-application", {
        infoRequest: infoRequest,
        operation: self.operation(),
        y: self.y(),
        x: self.x(),
        address: self.addressString(),
        propertyId: util.prop.toDbFormat(self.propertyId()),
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
      .keypress(function(e) { if (e.which === 13) model.searchNow(); })
      .autocomplete({
        source:     "/proxy/find-address",
        delay:      500,
        minLength:  3,
        select:     model.autocompleteSelect
      })
      .data("ui-autocomplete")._renderItem = model.autocompleteRender;

    tree = $("#create .operation-tree").selectTree({
      template: $("#create-templates"),
      onSelect: function(v) {
        model.operation(v ? v.op : null);
        ajax.query("get-organization-details", {municipality: model.municipality().id, operation: v.op}).success(function(d) {
          model.organization(d);
        }).call();
      },
      baseModel: model
    });

    hub.subscribe({type: "keyup", keyCode: 37}, tree.back);
    hub.subscribe({type: "keyup", keyCode: 33}, tree.start);
    hub.subscribe({type: "keyup", keyCode: 36}, tree.start);

  });

})();
