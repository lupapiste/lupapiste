;(function() {
  "use strict";

  // Simple true/false inputs that map directly to organization db attributes
  var enabledFlags = {
    permanentArchive:      "permanent-archive-enabled",
    digitizerTools:        "digitizer-tools-enabled",
    automaticEmails:       "automatic-emails-enabled",
    filebank:              "filebank-enabled",
    extraMeta:             "krysp-extra-attachment-metadata-enabled",
    foremanKrysp:          "foreman-termination-krysp-enabled",
    elyUspa:               "ely-uspa-enabled",
    stateChangeMsg:        "state-change-msg-enabled",
    buildingsExtinct:      "buildings-extinct-enabled",
    rakennusluokatEnabled: "rakennusluokat-enabled",
    reporting:             "reporting-enabled",
    attachmentLinks:       "use-attachment-links-integration"
  };

  function OrganizationModel() {
    var self = this;
    var isLoading = false;
    self.organization = ko.observable();
    self.names = ko.observableArray();
    self.deactivated = ko.observable(false);
    self.dmcityBackend = ko.pureComputed( function() {
      // Poor man's JS version of a pre-check
      var krysp = util.getIn( self.organization, ["krysp"]);
      return _.some( _.values( krysp ),
                     function( k ) {
                       return _.includes( ["matti", "dmcity"],
                                          util.getIn( k, ["backend-system"]));
                     });
    });

    // Init flag observables
    self.flags = {};
    _.forEach(enabledFlags, function(attr, flag) {
      self.flags[flag] = ko.observable(false);
    });

    self.flags.calendars = ko.observable(false);
    self.indicator = ko.observable(false).extend({notify: "always"});
    self.pending = ko.observable();
    self.earliestArchivingDate = ko.observable();
    self.backendSystems = ko.observableArray();
    self.availableBackendSystems = ko.observableArray();
    self.rakennusluokatEnabled = ko.observable(false);

    self.permitTypes = ko.observableArray([]);
    self.municipalities = ko.observableArray([]);

    self.allowedAutologinIps = ko.observableArray();
    self.ssoKeys = ko.observableArray();

    self.flags.adLogin = ko.observable(false);
    self.adLoginDomains = ko.observable("");
    self.adLoginIdPUri = ko.observable("");
    self.adLoginIdPCert = ko.observable("");

    self.suomifiViranomaisTunnus = ko.observable("");
    self.suomifiPalveluTunnus = ko.observable("");

    self.flags.threeDMap = ko.observable();

    self.threeDMapServerParams = {
      channel: {},
      server: ko.observable({}),
      readOnly: ko.pureComputed( _.negate( self.flags.threeDMap ) ),
      waiting: ko.observable(false),
      header: "organization.3d-map.server.header",
      urlLabel: "auth-admin.suti-api-settings.urlLabel",
      saveLabel: "save",
      prefix: "3d-map",
      // define mandatory keys, but the default ajax error handling is used
      error: null,
      errorMessageTerm: null
    };

    self.flags.docstore = ko.observable(false);
    self.flags.docterminal = ko.observable(false);
    self.flags.docdepartmental = ko.observable(false);
    self.docstorePrice = ko.observable("");
    self.docstoreFee = ko.observable("");
    self.docstoreDescs = ko.observableArray();

    function parsePrice() {
      return util.parseFloat(self.docstorePrice());
    }
    function parseFee() {
      return util.parseFloat(self.docstoreFee());
    }

    self.validDocstorePriceAndFee = ko.pureComputed( function() {
      var price = parsePrice();
      var fee = parseFee();
      return !_.isNaN(price) &&  price >= 0.0
        && !_.isNaN( fee ) && fee >= 0.0
        && price > fee;
    });

    self.docstoreInfoOk = ko.pureComputed(function () {
      return self.flags.docstore() ? self.validDocstorePriceAndFee() : true;
    });

    self.updateDocstoreInfo = function() {
      var params = {"org-id":                self.organization().id(),
                    docStoreInUse:           self.flags.docstore(),
                    docTerminalInUse:        self.flags.docterminal(),
                    docDepartmentalInUse:    self.flags.docdepartmental(),
                    };
      if(self.flags.docstore() || self.flags.docterminal() || self.flags.docdepartmental()) {
        var descs = _(self.docstoreDescs()).map(unWrapDesc).fromPairs().value();
        params.organizationDescription = descs;
      }
      if( self.flags.docstore()) {
        var documentPrice = Math.floor(parsePrice() * 100);
        var organizationFee = Math.floor(parseFee() * 100);
        params.pricing = {price: documentPrice,
                          fee:   organizationFee,};
      }
      ajax.command("update-docstore-info", params )
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

    self.flags.threeDMap.subscribe( function( value ) {
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
      hub.send( "organization::open", {organizationId: orgId} );
      ajax
        .query("organization-by-id", {organizationId: orgId})
        .pending(self.pending)
        .success(function(result) {
          // Omit the coordinate properties, since their (unnecessary)
          // mapping freezes the UI.
          var orgData = _.omit(result.data, ["areas", "areas-wgs84"]);
          // Fill scopes with defaults, if needed. This makes sure
          // that every needed observable exists.
          orgData.scope = _.map( orgData.scope,
                                 function( scope ) {
                                   scope.pate = _.defaults( scope.pate, {enabled: false,
                                                                         sftp: false,
                                                                         robot: false} );
                                   scope.bulletins = _.defaults(scope.bulletins, {enabled: false,
                                       "descriptions-from-backend-system": false,
                                       url: "",
                                       "notification-email": ""});
                                   return _.defaults( scope, {"open-inforequest": false,
                                                              "invoicing-enabled": false,
                                                              "open-inforequest-email": "",
                                                              "opening": null});
                                 });
          self.organization(ko.mapping.fromJS( orgData ));
          isLoading = true;
          self.names(_.map(util.getIn(result, ["data", "name"]), wrapName));
          self.deactivated( result.data.deactivated);

          // Update flag input with data from backend
          _.forEach(enabledFlags, function(attr, flag) {
            self.flags[flag](_.get(result.data, attr));
          });

          self.flags.docstore(_.get(result.data, "docstore-info.docStoreInUse", false));
          self.flags.docterminal(_.get(result.data, "docstore-info.docTerminalInUse", false));
          self.flags.docdepartmental(_.get(result.data, "docstore-info.docDepartmentalInUse", false));
          self.docstorePrice((_.get(result, "data.docstore-info.documentPrice", 0) / 100).toFixed(2).replace(".", ","));
          var fee = (_.get(result, "data.docstore-info.organizationFee", 0) / 100);
          self.docstoreFee( (_.isNaN( fee ) ? 0 : fee).toFixed(2).replace(".", ",") );
          self.docstoreDescs(_.map(util.getIn(result,["data", "docstore-info", "organizationDescription"]), wrapDesc));
          self.flags.calendars(result.data["calendars-enabled"]);
          self.flags.threeDMap(_.get(result.data, "3d-map.enabled"));
          self.threeDMapServerParams.server(_.get( result, "data.3d-map.server"));

          self.backendSystems(_.map(orgData.scope, function(v) {
            return {permitType: v.permitType,
                    backendSystem: _.get( result, ["data", "krysp", v.permitType, "backend-system"])}; }));

          self.rakennusluokatEnabled(result.data["rakennusluokat-enabled"]);

          if (result.data.hasOwnProperty("ad-login")) {
            self.flags.adLogin(result.data["ad-login"].enabled);
            self.adLoginDomains(result.data["ad-login"]["trusted-domains"].join(", "));
            self.adLoginIdPUri(result.data["ad-login"]["idp-uri"]);
            self.adLoginIdPCert(result.data["ad-login"]["idp-cert"]);
          } else {
            // If these attributes are not set in the DB, these fields need to be set to be empty.
            // If not, they can show data from another organization when orgs are switched.
            self.flags.adLogin(false);
            self.adLoginDomains("");
            self.adLoginIdPUri("");
            self.adLoginIdPCert("");
          }

          if (_.has(result, "data.suomifi-messages")) {
            var suomifiSettings = result.data["suomifi-messages"];
            self.suomifiPalveluTunnus(_.get(suomifiSettings, "service-id", ""));
            self.suomifiViranomaisTunnus(_.get(suomifiSettings, "authority-id", ""));
          } else {
            self.suomifiPalveluTunnus("");
            self.suomifiViranomaisTunnus("");
          }

          var archiveTs = result.data["earliest-allowed-archiving-date"];
          if (archiveTs && archiveTs > 0) {
            self.earliestArchivingDate(new Date(result.data["earliest-allowed-archiving-date"]));
          }

          if (_.has(result, "data.export-files")) {
            self.attachmentExportFiles(_.map(
              result.data["export-files"],
              function (file) {
                return {
                  id:       file.fileId,
                  filename: file.filename,
                  url:      "/api/raw/download-attachments-export-file?organization-id=" + orgId + "&file-id=" + file.fileId,
                  size:     util.sizeString(file.size),
                  created:  util.finnishDateAndTime(file.created)
                };}));
          } else {
            self.attachmentExportFiles([]);
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

      ajax
        .query("invoicing-config", {"organizationId": orgId})
        .success(function(d) {
            self.readInvoicingConfig(d["invoicing-config"]);
        })
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

    function updateOrg(data) {
        // backend integellently updates only specified keys to scope
        ajax.command("update-organization", data)
            .success(util.showSavedIndicator)
            .error(util.showSavedIndicator)
            .call();
    }

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
                  pateEnabled: Boolean(_.get( scope, "pate.enabled" )),
                  pateSftp: Boolean(_.get( scope, "pate.sftp" )),
                  pateRobot: Boolean(_.get( scope, "pate.robot" )),
                  invoicingEnabled: Boolean( scope ["invoicing-enabled"] )};
      updateOrg(data);
      return false;
    };

    self.saveBulletinRow = function(scopeData) {
        var scope = ko.mapping.toJS(scopeData);
        var data = {
            permitType: scope.permitType,
            municipality: scope.municipality,
            bulletinsEnabled:       Boolean(_.get(scope, "bulletins.enabled")),
            bulletinsUrl:           _.get(scope, "bulletins.url"),
            bulletinsEmail:         _.get(scope, "bulletins.notification-email"),
            bulletinsDescriptions:  Boolean(_.get(scope, "bulletins.descriptions-from-backend-system"))
        };
        updateOrg(data);
    };

    self.updateOrganizationName = function() {
      var names = _(self.names())
        .filter(_.flow(_.partial(_.get,_,"lang"), util.isSupportedLang))
        .map(unWrapName)
        .fromPairs()
        .value();
      ajax.command("update-organization-name", {"organizationId": self.organization().id(), "name": names})
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
      ajax.command("update-organization-backend-systems",
                   {"org-id": self.organization().id(),
                    "backend-systems": backendSystems })
        .success( function( res ) {
          util.showSavedIndicator( res );
          self.open( self.organization().id());
        })
        .call();
    };

    self.resetBulletinSettings = function() {
        ajax.command("reset-bulletin-text-settings", {organizationId: self.organization().id()})
            .success(function(resp) {
                util.showSavedIndicator(resp);
                // refresh data
                self.open(self.organization().id());
            })
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
          "enabled": self.flags.adLogin(),
          "trusted-domains": self.adLoginDomains().split(",").map(function (uri) { return uri.trim(); }),
          "idp-uri": self.adLoginIdPUri(),
          "idp-cert": self.adLoginIdPCert()
        })
        .success(util.showSavedIndicator)
        .call();
    };

    self.saveSuomifiSettings = function () {
      ajax
        .command("update-suomifi-settings", {
          "org-id": self.organization().id(),
          "authority-id": self.suomifiViranomaisTunnus(),
          "service-id": self.suomifiPalveluTunnus()
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
          if (ko.isObservable(self.organization()[name])) {
            self.organization()[name](value);
          } else {
            self.organization()[name] = ko.observable(value);
          }
        })
        .call();
    }

    // Tell backend that a flag was checked
    _.forEach(enabledFlags, function(attr, flag) {
      self.flags[flag].subscribe(function(value) {
        setBooleanAttribute(attr, value);
      });
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

    self.flags.calendars.subscribe(function(value) {
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

    function deactivate( flag ) {
      ajax.command( "toggle-deactivation", {organizationId: self.organization().id(),
                                            deactivated: flag})
        .success( _.wrap( flag, self.deactivated))
        .call();
    }

    self.toggleDeactivation = function( flag ) {
      hub.send( "show-dialog",
                {ltitle: "areyousure",
                 size: "medium", component: "yes-no-dialog",
                 componentParams: {text: loc(flag
                                             ? "admin.deactivated.deactivate-info"
                                             : "admin.deactivated.activate-info"),
                                   yesFn: _.wrap( flag, deactivate)}});
    };

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

    var defaultInvoicingConfig = {
      "local-sftp?": false,
      "integration-url": "",
      credentials : {
        username: "",
        password: ""
      },
      "invoice-file-prefix": "",
      "integration-requires-customer-number?": false,
      "download?": false,
      "backend-id?": false,
      constants: {
        tilauslaji: "",
        myyntiorg: "",
        jakelutie: "",
        sektori: "",
        laskuttaja: "",
        nimike: "",
        tulosyksikko: ""
      }
    };

    self.invoicingConfig = ko.mapping.fromJS(defaultInvoicingConfig);

    self.readInvoicingConfig = function(invoicingConfig){
      ko.mapping.fromJS(invoicingConfig || defaultInvoicingConfig, self.invoicingConfig);
    };

    self.updateInvoicingConfig = function(){
      ajax.command("update-invoicing-config",
                   {"org-id": self.organization().id(),
                    "invoicing-config": ko.mapping.toJS(self.invoicingConfig)})
        .success(util.showSavedIndicator)
        .call();
    };

    self.attachmentExportFiles = ko.observableArray([]);

    self.deleteAttachmentExportFile = function(fileData) {
      hub.send(
        "show-dialog",
        { ltitle: "areyousure",
          size: "small",
          component: "yes-no-dialog",
          componentParams:
            {
              text: window.loc( "areyousure"),
              yesFn: function()
              {
                var file = ko.mapping.toJS(fileData);
                ajax.command("delete-attachments-export-file",
                  { "organization-id": self.organization().id(),
                    "file-id": file.id
                  })
                  .success(function(result) {
                    util.showSavedIndicator(result);
                    self.attachmentExportFiles(
                      _.remove(self.attachmentExportFiles(),
                        function(f) { return f.id !== file.id; })); })
                  .call();
              }}});
    };
  }

  var organizationModel = new OrganizationModel();

  hub.onPageLoad("organization", function(pageLoad) {
    var orgId = _.last(pageLoad.pagePath);
    organizationModel.open(orgId);
    // what Pauli implemented for authorityAdmin could be handy:
    // ajax.pushPreprocessor(function (params) { return _.extend({organizationId: orgId}, params); });
    // but as we leave organization page as admin, there should be possibility to remove pushed preProcessor
    // so it would reguire some sort of registration/de-registration functionality which is overkill atm
    // for my bugfix usecase
  });

  hub.subscribe("organization::scope-added", function(data) {
    organizationModel.open(data.orgId);
  });


  $(function() {
    $("#organization").applyBindings({organizationModel:organizationModel});

    ko.components.register("create-scope", {viewModel: LUPAPISTE.CreateScopeModel, template: {element: "create-scope-template"}});
  });

})();
