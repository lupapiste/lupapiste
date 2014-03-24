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
      tree.reset(_.map(self.operations(), operations2tree));
      $("#create-part-1").hide();
      $("#create-part-2").show();
      window.scrollTo(0, 0);
    };

    self.goPhase3 = function() {
      if (!self.inforequestsDisabled()) {
        $("#create-part-2").hide();
        $("#create-part-3").show();
        window.scrollTo(0, 0);
      } else {
        LUPAPISTE.ModalDialog.showDynamicOk(
            loc("new-applications-or-inforequests-disabled.dialog.title"),
            loc("new-applications-or-inforequests-disabled.inforequests-disabled"),
            {title: loc("button.ok"), fn: function() {LUPAPISTE.ModalDialog.close();}});
      }
    };

     self.returnPhase2 = function() {
      $("#create-part-1").hide();
      $("#create-part-3").hide();
      $("#create-part-2").show();
      window.scrollTo(0, 0);
    };


    self.useManualEntry = ko.observable(false);

    self.map = null;

    self.search = ko.observable("");
    self.x = ko.observable(0);
    self.y = ko.observable(0.0);
    self.addressData = ko.observable(null);
    self.addressString = ko.observable(null);
    self.propertyId = ko.observable(null);
    self.municipality = ko.observable(null);
    self.operations = ko.observable(null);
    self.organization = ko.observable(null);
    self.organizationLinks = ko.computed(function() { var m = self.organization(); return m ? m.links : null; });
    self.attachmentsForOp = ko.computed(function() { var m = self.organization(); return m ? _.map(m.attachmentsForOp, function(d) { return { group: d[0], id: d[1]};}) : null; });
    self.municipalityCode = ko.observable(null);
    self.municipalityName = ko.observable();
    self.municipalitySupported = ko.observable(true);
    self.processing = ko.observable(false);
    self.inforequestsDisabled = ko.observable(false);
    self.newApplicationsDisabled = ko.observable(false);
    self.pending = ko.observable(false);

    self.municipalityCode.subscribe(function(code) {
      if (code) { self.findOperations(code); }
      if (self.useManualEntry()) { municipalities.findById(code, self.municipality); }
    });

    self.findMunicipality = function(code) {
      municipalities.findById(code, function(m) {
        self
          .municipality(m)
          .municipalitySupported(m ? true : false);
      });
      return self;
    };

    self.findOperations = function(code) {
      municipalities.operationsForMunicipality(code, function(opearations) {
        self.operations(opearations);
      });
      return self;
    };

    self.addressData.subscribe(function(a) {
      self.addressString(a ? a.street + " " + a.number : "");
    });

    self.propertyId.subscribe(function(id) {
      var human = util.prop.toHumanFormat(id);
      if (human !== id) {
        self.propertyId(human);
      } else {
        var code = id ? util.zeropad(3, id.split("-")[0].substring(0, 3)) : null;
        self
          .municipalityCode(code)
          .municipalityName(code ? loc(["municipality", code]) : null)
          .findMunicipality(code, self.municipality);
      }
    });

    self.operation = ko.observable();
    self.message = ko.observable("");
    self.requestType = ko.observable();

    self.clear = function() {
      var zoomLevel = features.enabled("use-wmts-map") ? 2 : 0;
      if (!self.map) self.map = gis.makeMap("create-map").center(404168, 7205000, zoomLevel).addClickHandler(self.click);
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

    // Return function that calls every function provided as arguments to 'comp'.
    function comp() {
      var fs = arguments;
      var self = this;
      return function() {
        var args = arguments;
        _.each(fs, function(f) {
          f.apply(self, args);
        });
      };
    }

    function zoom(item, level) { self.center(item.location.x, item.location.y, level || zoomLevel[item.type] || 11); } // original zoom 8
    function zoomer(level) { return function(item) { zoom(item, level); }; }
    function fillMunicipality(item) {
      self.search(", " + loc(["municipality", item.municipality]));
      $("#create-search").caretToStart();
    }
    function fillAddress(item) {
      self.search(item.street + " " + item.number + ", " + loc(["municipality", item.municipality]));
      $("#create-search").caretTo(item.street.length + item.number.toString().length + 1);
    }

    function selector(item) { return function(value) { return _.every(value[0], function(v, k) { return item[k] === v; }); }; }
    function toHandler(value) { return value[1]; }
    function invoker(item) { return function(handler) { return handler(item); }; }

    var handlers = [
      [{kind: "poi"}, comp(zoom, fillMunicipality)],
      [{kind: "address"}, comp(fillAddress, self.searchNow)],
      [{kind: "address", type: "street"}, zoomer(10)],  // wmts zoom 13
      [{kind: "address", type: "street-city"}, zoomer(10)],  // wmts zoom 13
      [{kind: "address", type: "street-number"}, zoomer(11)],   // wmts zoom 14
      [{kind: "address", type: "street-number-city"}, zoomer(11)],  // wmts zoom 14
      [{kind: "property-id"}, comp(zoomer(12), self.searchNow)]  // wmts zoom 14
    ];

    var renderers = [
      [{kind: "poi"}, function(item) {
        return $("<a>")
          .addClass("create-find")
          .addClass("poi")
          .append($("<span>").addClass("name").text(item.text))
          .append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality])))
          .append($("<span>").addClass("type").text(loc(["poi.type", item.type])));
      }],
      [{kind: "address"}, function(item) {
        var a = $("<a>")
          .addClass("create-find")
          .addClass("address")
          .append($("<span>").addClass("street").text(item.street));
        if ((item.type !== "street-city") && (item.type !== "street")) { a.append($("<span>").addClass("number").text(item.number)); }
        if (item.type !== "street-number") { a.append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality]))); }
        return a;
      }],
      [{kind: "property-id"}, function(item) {
        return $("<a>")
          .addClass("create-find")
          .addClass("property-id")
          .append($("<span>").text(util.prop.toHumanFormat(item["property-id"])));
      }]
    ];

    self.autocompleteSelect = function(e, data) {
      var item = data.item;
      _(handlers).filter(selector(item)).map(toHandler).each(invoker(item));
      return false;
    };

    self.autocompleteRender = function(ul, data) {
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
                x = data.location.x,
                y = data.location.y;
            self
              .useManualEntry(false)
              .setXY(x, y)
              .center(x, y, 11)  // wmts zoom 14
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
              .center(x, y, 11)  // wmts zoom 14
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
      if (infoRequest) {
        if (model.inforequestsDisabled()) {
          LUPAPISTE.ModalDialog.showDynamicOk(
              loc("new-applications-or-inforequests-disabled.dialog.title"),
              loc("new-applications-or-inforequests-disabled.inforequests-disabled"));
          return;
        }
        LUPAPISTE.ModalDialog.showDynamicOk(loc("create.prompt.title"), loc("create.prompt.text"));
      } else if (model.newApplicationsDisabled()) {
        LUPAPISTE.ModalDialog.showDynamicOk(
            loc("new-applications-or-inforequests-disabled.dialog.title"),
            loc("new-applications-or-inforequests-disabled.new-applications-disabled"));
        return;
      }

      var op = self.operation();
      if (!op) {
        error("No operation!", {selected: tree.getSelected(), stack: tree.getStack()});
      }

      ajax.command("create-application", {
        infoRequest: infoRequest,
        operation: op,
        y: self.y(),
        x: self.x(),
        address: self.addressString(),
        propertyId: util.prop.toDbFormat(self.propertyId()),
        messages: isBlank(self.message()) ? [] : [self.message()],
        municipality: self.municipality().id
      })
      .processing(self.processing)
      .pending(self.pending)
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
        .keypress(function(e) { if (e.which === 13) { model.searchNow(); }})
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
        if (v) {
          model.operation(v.op);
          ajax.query("organization-details",
              {municipality: model.municipality().id,
               operation: v.op,
               lang: loc.getCurrentLanguage()})
            .success(function(d) {
              model.inforequestsDisabled(d["inforequests-disabled"]);
              model.newApplicationsDisabled(d["new-applications-disabled"]);
              model.organization(d);
            })
            .error(function(d) {
              model.inforequestsDisabled(true);
              model.newApplicationsDisabled(true);
            })
            .call();
        } else {
          model.operation(null);
          model.organization(null);
        }
      },
      baseModel: model
    });

    function ifStep2(fn) {
      if ($("#create-part-2:visible").length === 1) {
        fn();
      }
    }

    hub.subscribe({type: "keyup", keyCode: 37}, _.partial(ifStep2, tree.back));  // left arrow
    hub.subscribe({type: "keyup", keyCode: 33}, _.partial(ifStep2, tree.start)); // page up
    hub.subscribe({type: "keyup", keyCode: 36}, _.partial(ifStep2, tree.start)); // home

  });

})();
