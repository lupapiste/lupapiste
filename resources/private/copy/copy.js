(function() {
  "use strict";

  function CopyApplicationModel() {
    var self = this;

    self.sourceApplicationId = ko.observable("");
    self.inviteCandidatesFetched = ko.observable(false);
    self.inviteCandidates = ko.observableArray([]);
    self.inviteCandidatesSelected = ko.observable(false);
    self.selectedInviteCandidates = ko.pureComputed(function() {
      return _.filter(self.inviteCandidates(), function(candidate) {
        return candidate.selected();
      });
    });
    self.locationSelected = ko.observable(false);

    self.phase = ko.pureComputed(function() {
      var locationIsSelected = self.locationSelected();
      var inviteCandidatesAreSelected = self.inviteCandidatesSelected();

      if (!locationIsSelected) {
        return 0;
      } else if (!inviteCandidatesAreSelected) {
        return 1;
      } else {
        return 2;
      }
    });

    function phaseClass(thisPhase, currentPhase) {
      if (thisPhase === currentPhase) {
        return "active-phase";
      } else if (thisPhase < currentPhase) {
        return "completed-phase";
      } else {
        return "";
      }
    }

    self.phases = ko.pureComputed(function() {
      var currentPhase = self.phase();
      return [
        {text: "1. " + loc("application.copy.phase.0"),
         cssClass: phaseClass(0, currentPhase)},
        {text: "2. " + loc("application.copy.phase.1"),
         cssClass: phaseClass(1, currentPhase)},
        {text: "3. " + loc("application.copy.phase.2"),
         cssClass: phaseClass(2, currentPhase)}
      ];
    });

    self.buttonActions = [
      null,
      {previous: {ltext: "previous",
                  action: function() {
                    self.clear();
                  }},
       next: {ltext: "continue",
              action: function() {
                self.inviteCandidatesSelected(true);
              }}},
      {previous: {ltext: "previous",
                  action: function() {
                    self.clearCandidates();
                    fetchAuthCandidatesForApplication();
                  }},
       next: {ltext: "done",
              action: function() {
                self.copyApplication();
              }}}];

    function checkIfApplicationCanBeCopiedToSelectedLocation() {
      if (self.locationModel.propertyIdOk() && self.locationModel.addressOk()) {
        var params = self.locationModel.toJS();
        params["source-application-id"] = self.sourceApplicationId(); // TODO
        ajax
          .query("application-copyable-to-location", params)
          .processing(self.processing)
          .pending(self.pending)
          .success(function() {
            fetchAuthCandidatesForApplication();
          })
          .error(function(data) {
            notify.ajaxError(data);
          })
          .call();

      }
    }

    function fetchAuthCandidatesForApplication() {
      var application = self.sourceApplicationId();
      if (application) {
        ajax
          .query("copy-application-invite-candidates", {
            "source-application-id": application
          })
          .success(function(d) {
            self.inviteCandidates(_.map(d.candidates, function(candidate) {
              candidate.selected = ko.observable(false);
              return candidate;
            }));
            self.inviteCandidatesFetched(true);
            self.locationSelected(true);
          })
          .error(function(d) {
            notify.ajaxError(d);
          })
          .call();
      }
    }

    hub.subscribe("copy-location-selected", function() {
      checkIfApplicationCanBeCopiedToSelectedLocation();
    });

    self.locationModel = new LUPAPISTE.CreateApplicationLocationModel({mapId: "copy-map", category: "Copy", nextStep: "copy-location-selected"});

    self.search = ko.observable("");
    self.searching = ko.observable(false);

    self.processing = ko.observable(false);
    self.pending = ko.observable(false);
    self.message = ko.observable("");

    self.authDescription = function(auth) {
      var name = (auth.firstName !== "" ?
                  (auth.firstName + (auth.lastName !== "" ? (" " + auth.lastName) : "")) :
                  (auth.email !== null ? auth.email : ""));
      return name + ", " +
        _.upperFirst(auth.roleSource === "document" ?
                     loc("applicationRole." + auth.role)
                     : (auth.role === "reader" ?
                        loc("authorityrole." + auth.role) : loc(auth.role)));
    };

    self.findOperations = function(code) {
      municipalities.operationsForMunicipality(code, function(operations) {
        self.operations(operations);
      });
      return self;
    };

    self.resetLocation = function() {
      self.locationModel.reset();
      return self;
    };

    self.locationInfo = ko.pureComputed(function() {
      return self.locationModel.propertyIdHumanReadable() + ", " +
        self.locationModel.address() + ", " +
        self.locationModel.municipalityName();
    });

    self.clearCandidates = function() {
      return self.inviteCandidatesFetched(false)
        .inviteCandidatesSelected(false)
        .inviteCandidates([]);
    };

    self.clear = function() {

      self
        .resetLocation()
        .locationSelected(false)
        .sourceApplicationId(pageutil.lastSubPage())
        .search("")
        .message("")
        .clearCandidates();
      self.locationModel.clearMap();
      return self;
    };

    self.copyOK = ko.pureComputed(function() {
      return self.locationModel.propertyIdOk() && self.locationModel.addressOk() && !self.processing();
    });

    //
    // Callbacks:
    //

    // Search activation:

    self.searchNow = function() {
      self.locationModel.clearMap().reset();
      self.locationModel.beginUpdateRequest()
        .searchPoint(self.search(), self.searching);
      return false;
    };

    var zoomLevelEnum = {
      "540": 9,
      "550": 7,
      "560": 9
    };

    // Return function that calls every function provided as arguments to 'comp'.
    function comp() {
      var fs = arguments;
      return function() {
        var args = arguments;
        _.each(fs, function(f) {
          f.apply(self, args);
        });
      };
    }

    function zoom(item, level) {
      var zoomLevel = level || zoomLevelEnum[item.type] || 11;
      self.locationModel.center(zoomLevel, item.location.x, item.location.y);
    }
    function zoomer(level) { return function(item) { zoom(item, level); }; }
    function fillMunicipality(item) {
      self.search(", " + loc(["municipality", item.municipality]));
      $("#copy-search").caretToStart();
    }
    function fillAddress(item) {
      self.search(item.street + " " + item.number + ", " + loc(["municipality", item.municipality]));
      var addressEndIndex = item.street.length + item.number.toString().length + 1;
      $("#copy-search").caretTo(addressEndIndex);
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
          .addClass("create-find") // Todo: copy-find
          .addClass("poi")
          .append($("<span>").addClass("name").text(item.text))
          .append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality])))
          .append($("<span>").addClass("type").text(loc(["poi.type", item.type])));
      }],
      [{kind: "address"}, function(item) {
        var a = $("<a>")
            .addClass("create-find") // Todo: copy-find
            .addClass("address")
            .append($("<span>").addClass("street").text(item.street));
        if (item.number) {
          a.append($("<span>").addClass("number").text(item.number));
        }
        if (item.municipality) {
          a.append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality])));
        }
        return a;
      }],
      [{kind: "property-id"}, function(item) {
        return $("<a>")
          .addClass("create-find") // Todo: copy-find
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
      var element = _(renderers).filter(selector(data)).take(1).map(toHandler).map(invoker(data)).value();
      return $("<li>")
        .append(element)
        .appendTo(ul);
    };

    self.updateOrganizationDetails = function(operation) {
      if (self.locationModel.municipalityCode() && operation) {
        ajax
          .query("organization-details", {
            municipality: self.locationModel.municipalityCode(),
            operation: operation,
            lang: loc.getCurrentLanguage()
          })
          .success(function(d) {
            self.newApplicationsDisabled(d["new-applications-disabled"]);
            self.organization(d);
          })
          .error(function() {
            // TODO display error message?
            self.newApplicationsDisabled(true);
          })
          .call();
      }
    };

    self.copyApplication = function() {
      var params = self.locationModel.toJS();
      params["source-application-id"] = self.sourceApplicationId(); // TODO
      params["auth-invites"] = _.map(self.selectedInviteCandidates(), "id");

      ajax.command("copy-application", params)
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          self.clear();
          params.id = data.id;
          pageutil.openApplicationPage(params);
          hub.send("indicator", {style: "positive",
                                 message: "application.copy.success.text",
                                 sticky: true});
        })
        .error(function(data) {
          notify.ajaxError(data);
        })
        .call();
    };
  }

  var model = new CopyApplicationModel();

  hub.onPageLoad("copy", model.clear);

  function initAutocomplete(id) {
    $(id)
      .on("keypress",function(e) { if (e.which === 13) { model.searchNow(); }}) // enter
      .autocomplete({
        source:     "/proxy/find-address?lang=" + loc.getCurrentLanguage(),
        delay:      500,
        minLength:  3,
        select:     model.autocompleteSelect
      })
      .data("ui-autocomplete")._renderItem = model.autocompleteRender;
  }

  $(function() {
    $("#copy").applyBindings(model);

    initAutocomplete("#copy-search");

  });

})();
