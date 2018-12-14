;(function() {
  "use strict";

  function OrganizationModel() {
    var self = this;
    var isLoading = false;
    self.organization = ko.observable();
    self.names = ko.observableArray();
    self.deactivated = ko.observable(false);
    self.permanentArchiveEnabled = ko.observable(false);
    self.digitizerToolsEnabled = ko.observable(false);
    self.calendarsEnabled = ko.observable(false);
    self.indicator = ko.observable(false).extend({notify: "always"});
    self.pending = ko.observable();
    self.earliestArchivingDate = ko.observable();
    self.backendSystems = ko.observableArray();
    self.availableBackendSystems = ko.observableArray();
    self.elyUspaEnabled = ko.observable(false);

    self.permitTypes = ko.observableArray([]);
    self.municipalities = ko.observableArray([]);

    self.allowedAutologinIps = ko.observableArray();
    self.ssoKeys = ko.observableArray();

    self.adLoginEnabled = ko.observable(false);
    self.adLoginDomains = ko.observable("");
    self.adLoginIdPUri = ko.observable("");
    self.adLoginIdPCert = ko.observable("");

    self.threeDMapEnabled = ko.observable();

    self.threeDMapServerParams = {
      channel: {},
      server: ko.observable({}),
      readOnly: ko.pureComputed( _.negate( self.threeDMapEnabled ) ),
      waiting: ko.observable(false),
      header: "organization.3d-map.server.header",
      urlLabel: "auth-admin.suti-api-settings.urlLabel",
      saveLabel: "save",
      prefix: "3d-map",
      // define mandatory keys, but the default ajax error handling is used
      error: null,
      errorMessageTerm: null
    };

    self.docstoreEnabled = ko.observable(false);
    self.docterminalEnabled = ko.observable(false);
    self.docstorePrice = ko.observable("");
    self.docstoreDescs = ko.observableArray();
    self.stateChangeMsgEnabled = ko.observable(false);

    self.validDocstorePrice = ko.pureComputed(function () {
      var price = util.parseFloat(self.docstorePrice());
      return !_.isNaN(price) && ( price >= 0.0);
    });

    self.updateDocstoreInfo = function() {
      var descs = _(self.docstoreDescs()).map(unWrapDesc).fromPairs().value();
      var documentPrice = Math.floor(util.parseFloat(self.docstorePrice()) * 100);
      ajax.command("update-docstore-info",
                   {"org-id":                self.organization().id(),
                    docStoreInUse:           self.docstoreEnabled(),
                    docTerminalInUse:        self.docterminalEnabled(),
                    documentPrice:           documentPrice,
                    organizationDescription: descs})
        .success(util.showSavedIndicator)
        .error(function(resp) {
          util.showErrorDialog(resp);
        })
        .call();
    };


    function organizationCommand( command, params, pend ) {
      if (isLoading) {
        return;
      }
      ajax.command( command,
                    _.merge(params,
                            {organizationId: self.organization().id()}))
        .pending( pend || _.noop )
        .success( util.showSavedIndicator )
        .error( util.showSavedIndicator )
        .call();
    }

    self.threeDMapServerParams.channel.send = function( serverDetails ) {
      organizationCommand( "update-3d-map-server-details", serverDetails, self.threeDMapServerParams.waiting );
    };

    self.threeDMapEnabled.subscribe( function( value ) {
      organizationCommand( "set-3d-map-enabled", {flag: Boolean( value )}, self.threeDMapServerParams.waiting);
    });

    function updateSsoKeys() {
      _.forEach(self.ssoKeys(), function(ssoKey) {
        ssoKey.selected(_.includes(self.allowedAutologinIps(), ssoKey.ip));
      });
    }

    function loadAvailableSsoKeys() {
      ajax.query("get-single-sign-on-keys")
        .success(function(d) {
          self.ssoKeys(_.map(d.ssoKeys, function(ssoKey) {
            return _.assign(ssoKey, {selected: ko.observable(false)});
          }));
          updateSsoKeys();
        })
        .call();
    }

    function wrapLoc(k, v, lang) {
      return _.set({lang: lang}, k, ko.observable(v));
    }

    function unWrapLoc(o, k) {
      return [o.lang, util.getIn(o, [k])];
    }

    function wrapName(name, lang) {
      return wrapLoc("name", name, lang);
    }

    function unWrapName(o) {
      return unWrapLoc(o, "name");
    }

    function wrapDesc(desc, lang) {
      return wrapLoc("description", desc, lang);
    }

    function unWrapDesc(o) {
      return unWrapLoc(o, "description");
    }

    self.open = function(orgId) {
      ajax
        .query("organization-by-id", {organizationId: orgId})
        .pending(self.pending)
        .success(function(result) {
          // Omit the coordinate properties, since their (unnecessary)
          // mapping freezes the UI.
          self.organization(ko.mapping.fromJS(_.omit(result.data,
                                                     ["areas", "areas-wgs84"])));
          isLoading = true;
          self.names(_.map(util.getIn(result, ["data", "name"]), wrapName));
          self.deactivated( result.data.deactivated);
          self.permanentArchiveEnabled(result.data["permanent-archive-enabled"]);
          self.digitizerToolsEnabled(result.data["digitizer-tools-enabled"]);
          self.docstoreEnabled(_.get(result, "data.docstore-info.docStoreInUse"));
          self.docterminalEnabled(_.get(result, "data.docstore-info.docTerminalInUse"));
          self.docstorePrice((_.get(result, "data.docstore-info.documentPrice") / 100).toFixed(2).replace(".", ","));
          self.docstoreDescs(_.map(util.getIn(result,["data", "docstore-info", "organizationDescription"]), wrapDesc));
          self.calendarsEnabled(result.data["calendars-enabled"]);
          self.threeDMapEnabled( _.get(result, "data.3d-map.enabled"));
          self.threeDMapServerParams.server(_.get( result, "data.3d-map.server"));
          self.backendSystems(_.map(util.getIn(result, ["data", "krysp"]), function(v,k) { return {permitType: k, backendSystem: v["backend-system"]}; }));
          self.stateChangeMsgEnabled(result.data["state-change-msg-enabled"]);
          self.elyUspaEnabled(result.data["ely-uspa-enabled"]);

          if (result.data.hasOwnProperty("ad-login")) {
            self.adLoginEnabled(result.data["ad-login"].enabled);
            self.adLoginDomains(result.data["ad-login"]["trusted-domains"].join(", "));
            self.adLoginIdPUri(result.data["ad-login"]["idp-uri"]);
            self.adLoginIdPCert(result.data["ad-login"]["idp-cert"]);
          } else {
            // If these attributes are not set in the DB, these fields need to be set to be empty.
            // If not, they can show data from another organization when orgs are switched.
            self.adLoginEnabled(false);
            self.adLoginDomains("");
            self.adLoginIdPUri("");
            self.adLoginIdPCert("");
          }

          var archiveTs= result.data["earliest-allowed-archiving-date"];
          if (archiveTs && archiveTs > 0) {
            self.earliestArchivingDate(new Date(result.data["earliest-allowed-archiving-date"]));
          }
          isLoading = false;
        })
        .call();

      ajax
        .query("allowed-autologin-ips-for-organization", {"org-id": orgId})
        .success(function(d) {
          self.allowedAutologinIps(d.ips);
          updateSsoKeys();
        })
        .call();

      ajax
        .query("available-backend-systems")
        .success(function(d) { self.availableBackendSystems(d["backend-systems"]); })
        .call();

      return false;
    };

    self.availableBackendSystemsOptionsText = function(value) {
      return loc(["backend-system", value || "unknown"]);
    };

    self.convertOpenInforequests = function() {
      ajax.command("convert-to-normal-inforequests", {organizationId: self.organization().id()})
        .success(function(resp) {
          var msg = loc("admin.converted.inforequests", resp.n);
          LUPAPISTE.ModalDialog.showDynamicOk(loc("infoRequests"), msg);})
        .call();
    };

    self.openInfoRequests = ko.pureComputed(function() {
      return self.organization() && self.organization().scope && _.reduce(self.organization().scope(), function(result, s) {return result || s["open-inforequest"]();}, false);
    });

    self.saveRow = function(s) {
      var scope = ko.mapping.toJS(s);

      var openingMills = null;
      if (scope.opening) {
        openingMills = new Date(scope.opening).getTime();
      }

      var data = {permitType: scope.permitType,
                  municipality: scope.municipality,
                  inforequestEnabled: scope["inforequest-enabled"],
                  applicationEnabled: scope["new-application-enabled"],
                  openInforequestEnabled: scope["open-inforequest"],
                  openInforequestEmail: scope["open-inforequest-email"],
                  opening: openingMills,
                  pateEnabled: Boolean( scope ["pate-enabled"] ),
                  invoicingEnabled: Boolean( scope ["invoicing-enabled"] )};

      ajax.command("update-organization", data)
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
      return false;
    };

    self.updateOrganizationName = function() {
      var names = _(self.names()).map(unWrapName).fromPairs().value();
      ajax.command("update-organization-name", {"org-id": self.organization().id(),
                                                "name": names})
        .success(function(res) {
          util.showSavedIndicator(res);
          _.forEach(names, function(n, l) {
            self.organization().name[l](n);
          });
        })
        .call();
    };

    self.updateBackendSystems = function() {
      var backendSystems = _.zipObject(_.map(self.backendSystems(), "permitType"),
                                       _.map(self.backendSystems(), function(d) {
                                         return d.backendSystem || "unknown";
                                       }));
      ajax.command("update-organization-backend-systems", {"org-id": self.organization().id(),
                                                           "backend-systems": backendSystems })
        .call();
    };

    self.newScope = function(model) {
      if (!self.pending()) {
        hub.send("show-dialog", {title: "Lis&auml;&auml; lupatyyppi",
                                 size: "medium",
                                 component: "create-scope",
                                 componentParams: {organization: model.organization(),
                                                   permitTypes:  self.permitTypes(),
                                                   municipalities: self.municipalities()}});
      }
    };

    self.saveAdLoginSettings = function() {
      ajax
        .command("update-ad-login-settings", {
          "org-id": self.organization().id(),
          "enabled": self.adLoginEnabled(),
          "trusted-domains": self.adLoginDomains().split(",").map(function (uri) { return uri.trim(); }),
          "idp-uri": self.adLoginIdPUri(),
          "idp-cert": self.adLoginIdPCert()
        })
        .success(util.showSavedIndicator)
        .call();
    };

    self.saveAutologinIps = function() {
      var ips = _(self.ssoKeys()).filter(function(ssoKey) {return ssoKey.selected();}).map("ip").value();
      ajax
        .command("update-allowed-autologin-ips", {"org-id": self.organization().id(), ips: ips})
        .success(util.showSavedIndicator)
        .call();
    };

    function setBooleanAttribute(name, value) {
      if (isLoading) {
        return;
      }
      ajax.command("set-organization-boolean-attribute", {organizationId: self.organization().id(), enabled: value, attribute: name})
        .success(function(res) {
          util.showSavedIndicator(res);
          self.organization()[name](value);
        })
        .call();
    }

    self.deactivated.subscribe(function(value) {
      setBooleanAttribute("deactivated", value);
    });

    self.permanentArchiveEnabled.subscribe(function(value) {
      setBooleanAttribute("permanent-archive-enabled", value);
    });

    self.earliestArchivingDate.subscribe(function (date) {
      if (isLoading) {
        return;
      }
      var ts = date ? date.getTime() : 0;
      ajax.command("set-organization-earliest-allowed-archiving-date", {organizationId: self.organization().id(), date: ts})
        .success(function(res) {
          util.showSavedIndicator(res);
        })
        .call();
    });

    self.resetEarliestArchivingDate = function () {
      self.earliestArchivingDate(null);
    };

    self.digitizerToolsEnabled.subscribe(function(value) {
      setBooleanAttribute("digitizer-tools-enabled", value);
    });

    self.stateChangeMsgEnabled.subscribe(function(value) {
      setBooleanAttribute("state-change-msg-enabled", value);
    });

    self.elyUspaEnabled.subscribe(function(value) {
      setBooleanAttribute("ely-uspa-enabled", value);
    });

    self.calendarsEnabled.subscribe(function(value) {
      if (isLoading) {
        return;
      }
      ajax.command("set-organization-calendars-enabled", {organizationId: self.organization().id(), enabled: value})
        .success(function() {
          self.indicator(true);
          self.organization()["permanent-archive-enabled"](value);
        })
        .call();
    });

    ajax
      .query("permit-types")
      .success(function(d) {
        self.permitTypes(d.permitTypes);
      })
      .call();

    ajax
      .query("municipalities")
      .success(function(d) {
        self.municipalities(d.municipalities);
      })
      .call();


    loadAvailableSsoKeys();
    hub.subscribe("sso-keys-changed", loadAvailableSsoKeys);
  }

  var organizationModel = new OrganizationModel();

  hub.onPageLoad("organization", function(pageLoad) {
    var orgId = _.last(pageLoad.pagePath);
    organizationModel.open(orgId);
  });

  hub.subscribe("organization::scope-added", function(data) {
    organizationModel.open(data.orgId);
  });


  $(function() {
    $("#organization").applyBindings({organizationModel:organizationModel});

    ko.components.register("create-scope", {viewModel: LUPAPISTE.CreateScopeModel, template: {element: "create-scope-template"}});
  });

})();
