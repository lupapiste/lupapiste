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
      window.location = "#!/create-part-1";
      self.map.updateSize();
     };


    self.goPhase2 = function() {
      window.location = "#!/create-part-2";
      tree.reset(_.map(self.operations(), operations2tree));
      window.scrollTo(0, 0);
    };

    self.goPhase3 = function() {
      window.location = "#!/create-part-3";
      if (!self.inforequestsDisabled()) {
        window.scrollTo(0, 0);
      } else {
        LUPAPISTE.ModalDialog.showDynamicOk(
            loc("new-applications-or-inforequests-disabled.dialog.title"),
            loc("new-applications-or-inforequests-disabled.inforequests-disabled"),
            {title: loc("button.ok"), fn: function() {LUPAPISTE.ModalDialog.close();}});
      }
    };

     self.returnPhase2 = function() {
      window.location = "#!/create-part-2";
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
    self.operation = ko.observable();
    self.message = ko.observable("");
    self.requestType = ko.observable();

    // Observables for creating new application from previous permit
    self.creatingAppWithPrevPermit = false;
    self.kuntalupatunnusFromPrevPermit = ko.observable(null);
    self.needMorePrevPermitInfo = ko.observable(false);
    self.creatingAppWithPrevPermitOk = ko.computed(function() {
      var addressOk = self.needMorePrevPermitInfo() ? !_.isEmpty(self.addressString()) : true;
      return !self.processing() && !self.pending() && !_.isEmpty(self.kuntalupatunnusFromPrevPermit()) && !_.isEmpty(self.municipalityCode()) && addressOk;
    });


    self.municipalityCode.subscribe(function(code) {
      if (self.creatingAppWithPrevPermit) {
        self.updateMunicipality(code);
        self.updateOrganizationDetails("aiemmalla-luvalla-hakeminen");
      } else {
        if (code) { self.findOperations(code); }
        if (self.useManualEntry()) { self.updateMunicipality(code); }
      }
    });

    self.updateMunicipality = function(code) {
      municipalities.findById(code, function(m) {
        self
          .municipalityName(m ? m.name : null)
          .municipality(m)
          .municipalitySupported(m ? true : false);
      });
      return self;
    };

    self.findOperations = function(code) {
      municipalities.operationsForMunicipality(code, function(operations) {
        self.operations(operations);
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
          .updateMunicipality(code, self.municipality);
      }
    });

    self.clear = function() {
      var zoomLevel = 2;
      if (self.map) {
        self.map.clear();
        self.map.updateSize();
      } else {
        self.map = gis
          .makeMap("create-map", false)
          .center(404168, 7205000, zoomLevel)
          .addClickHandler(self.click)
          .setPopupContentModel(self, "section#map-popup-content");
      }
      self.creatingAppWithPrevPermit = false;
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
        .kuntalupatunnusFromPrevPermit(null)
        .needMorePrevPermitInfo(false);
    };

    self.resetXY = function() { if (self.map) { self.map.clear(); } return self.x(0).y(0); };
    self.setXY = function(x, y) { if (self.map) { self.map.clear().add({x: x, y: y}, true); } return self.x(x).y(y); };
    self.center = function(x, y, zoom) { if (self.map) { self.map.center(x, y, zoom); } return self; };

    self.addressOk = ko.computed(function() { return self.municipality() && !isBlank(self.addressString()); });
    self.propertyIdOk = ko.computed(function(value) { return util.prop.isPropertyId(self.propertyId()) && !isBlank(self.propertyId());});

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
//        .beginUpdateRequest()    // TODO: Tarvitseeko tahan valiin oman beginUpdateRequest()-kutsun?
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

    var zoomLevelEnum = {
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

    function zoom(item, level) { self.center(item.location.x, item.location.y, level || zoomLevelEnum[item.type] || 11); }
    function zoomer(level) { return function(item) { zoom(item, level); }; }
    function fillMunicipality(item) {
      self.search(", " + loc(["municipality", item.municipality]));
      $("#create-search").caretToStart();
      $("#prev-permit-create-search").caretToStart();
    }
    function fillAddress(item) {
      self.search(item.street + " " + item.number + ", " + loc(["municipality", item.municipality]));
      var addressEndIndex = item.street.length + item.number.toString().length + 1;
      $("#create-search").caretTo(addressEndIndex);
      $("#prev-permit-create-search").caretTo(addressEndIndex);
    }

    function selector(item) { return function(value) { return _.every(value[0], function(v, k) { return item[k] === v; }); }; }
    function toHandler(value) { return value[1]; }
    function invoker(item) { return function(handler) { return handler(item); }; }

    var handlers = [
      [{kind: "poi"}, comp(zoom, fillMunicipality)],
      [{kind: "address"}, comp(fillAddress, self.searchNow)],
      [{kind: "address", type: "street"}, zoomer(13)],
      [{kind: "address", type: "street-city"}, zoomer(13)],
      [{kind: "address", type: "street-number"}, zoomer(14)],
      [{kind: "address", type: "street-number-city"}, zoomer(14)],
      [{kind: "property-id"}, comp(zoomer(14), self.searchNow)]
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

    self.searchPointByAddressOrPropertyId = function(value) {
      if (!_.isEmpty(value)) {
        return util.prop.isPropertyId(value) ? self.searchPointByPropertyId(value) : self.searchPointByAddress(value);
      } else {
        return self;
      }
    };

    self.searchPointByAddress = function(address, successCallback, errorCallback) {
      locationSearch.pointByAddress(self.requestContext, address,
        function(result) {
          if (result.data && result.data.length > 0) {
            var data = result.data[0],
                x = data.location.x,
                y = data.location.y;
            self
              .useManualEntry(false)
              .center(x, y, 13)
              .setXY(x, y)
              .addressData(data)
//              .beginUpdateRequest()    // TODO: Tarvitseeko tahan valiin oman beginUpdateRequest()-kutsun?
              .searchPropertyId(x, y,
                function(d) {
                  if (successCallback) { successCallback(result); } },  // Note: returning the result of the locationSearch.pointByAddress query
                function(d) {
                  // TODO: Mita pitaisi palauttaa taman parametrina?
                  if (errorCallback) { errorCallback(d); } });
          } else {
            // TODO: Pitaisiko tassa kutsua successCallback?
            if (errorCallback) { errorCallback(result); }
          }
        },
        function(d) {
          self.useManualEntry(true);
          if (errorCallback) { errorCallback(d); }
        });
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
              .center(x, y, 14)
              .setXY(x, y)
              .propertyId(id)
              .beginUpdateRequest()
              .searchAddress(x, y);
          }
        },
        _.partial(self.useManualEntry, true));
      return self;
    };

    self.searchPropertyId = function(x, y, successCallback, errorCallback) {
      locationSearch.propertyIdByPoint(self.requestContext, x, y,
          function(d) {
            self.propertyId(d);
            if (successCallback) { successCallback(d); }
          },
          errorCallback
          );
      return self;
    };

    self.searchAddress = function(x, y) {
      locationSearch.addressByPoint(self.requestContext, x, y, self.addressData);
      return self;
    };

    self.updateOrganizationDetails = function(operation) {
      if (self.municipality() && operation) {
        ajax
          .query("organization-details", {
            municipality: self.municipality().id,
            operation: operation,
            lang: loc.getCurrentLanguage()
          })
          .success(function(d) {
            self.inforequestsDisabled(d["inforequests-disabled"]);
            self.newApplicationsDisabled(d["new-applications-disabled"]);
            self.organization(d);
          })
          .error(function(d) {
            self.inforequestsDisabled(true);
            self.newApplicationsDisabled(true);
          })
          .call();
      }
    };

    self.create = function(infoRequest) {
      if (infoRequest) {
        if (self.inforequestsDisabled()) {
          LUPAPISTE.ModalDialog.showDynamicOk(
              loc("new-applications-or-inforequests-disabled.dialog.title"),
              loc("new-applications-or-inforequests-disabled.inforequests-disabled"));
          return;
        }
        LUPAPISTE.ModalDialog.showDynamicOk(loc("create.prompt.title"), loc("create.prompt.text"));
      } else if (self.newApplicationsDisabled()) {
        LUPAPISTE.ModalDialog.showDynamicOk(
            loc("new-applications-or-inforequests-disabled.dialog.title"),
            loc("new-applications-or-inforequests-disabled.new-applications-disabled"));
        return;
      }

      var op = self.operation();
      if (!op) {
        error("No operation!", {selected: tree.getSelected(), stack: tree.getStack()});
      }

      if (self.creatingAppWithPrevPermit) {

        ajax.command("create-application-from-previous-permit", {
          operation: op,
          y: self.y(),
          x: self.x(),
          address: self.addressString(),
          propertyId: util.prop.toDbFormat(self.propertyId()),
          municipality: self.municipality().id,
          kuntalupatunnus: self.kuntalupatunnusFromPrevPermit()
        })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          setTimeout(self.clear, 0);
          window.location = "#!/application/" + data.id;
        })
        .error(function(d) {
          // If app creation failed because the "rakennuksen tiedot" data was not received in the xml message from municipality's backend,
          // then show user a prompt to fill up the address using autocomplete (that now appears),
          // otherwise show user the original error message thrown.
          // The original error is also shown after a request with additional address information about previous permit has has been made.
          if (d.needMorePrevPermitInfo) {
            if (self.needMorePrevPermitInfo()) {
              notify.error(loc("error.dialog.title"), loc("info.no-previous-permit-found-from-backend"));
            } else {
              notify.error(loc("more-prev-app-info-needed-message-title"), loc("more-prev-app-info-needed-message"));
              self.needMorePrevPermitInfo(true);
            }

          } else {
            notify.error(loc("error.dialog.title"), loc(d.text));
          }
        })
        .call();

      } else {

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
          window.location = (infoRequest ? "#!/inforequest/" : "#!/application/") + data.id;
        })
        .call();

      }

    };
    self.createApplication = self.create.bind(self, false);
    self.createInfoRequest = self.create.bind(self, true);

    //
    // For creating new application based on a previous permit
    //

    self.clearForCreateAppWithPrevPermit = function() {
      model.clear();
      self.creatingAppWithPrevPermit = true;
      self.operation("aiemmalla-luvalla-hakeminen");
    };

    self.createApplicationwithPrevPermit = function() {
      if (!self.needMorePrevPermitInfo()) {
        self.create(false);
      } else {
        self.beginUpdateRequest();
        self.searchPointByAddress(
          self.addressString(),
          _.partial(self.create, false),
          function(d) {
            LUPAPISTE.ModalDialog.showDynamicOk(loc("error.dialog.title"), loc("info.no-previous-permit-found-from-backend"));
          });
      }
    };

  }();

  hub.onPageChange("create-part-1", model.clear);
  hub.onPageChange("create-page-prev-permit", model.clearForCreateAppWithPrevPermit);

  function initAutocomplete(id) {
    $(id)
    .keypress(function(e) { if (e.which === 13) { model.searchNow(); }}) // enter
    .autocomplete({
      source:     "/proxy/find-address",
      delay:      500,
      minLength:  3,
      select:     model.autocompleteSelect
    })
    .data("ui-autocomplete")._renderItem = model.autocompleteRender;
  }

  $(function() {
    $("#create-part-1").applyBindings(model);
    $("#create-part-2").applyBindings(model);
    $("#create-part-3").applyBindings(model);
    $("#create-page-prev-permit").applyBindings(model);

    initAutocomplete("#create-search");
    initAutocomplete("#prev-permit-create-search");  // TODO: Miten filtteroida autocomplete-tuloksista pois ne kunnat, joilla ei ole asetettuna taustajarjestelmaa?

    tree = $("#create-part-2 .operation-tree").selectTree({
      template: $("#create-templates"),
      onSelect: function(v) {
        if (v) {
          model.operation(v.op);
          model.updateOrganizationDetails(v.op);
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
