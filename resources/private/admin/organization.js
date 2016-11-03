;(function() {
  "use strict";

  function OrganizationModel() {
    var self = this;
    var isLoading = false;
    self.organization = ko.observable();
    self.names = ko.observableArray();
    self.permanentArchiveEnabled = ko.observable(false);
    self.calendarsEnabled = ko.observable(false);
    self.indicator = ko.observable(false).extend({notify: "always"});
    self.pending = ko.observable();

    self.permitTypes = ko.observableArray([]);
    self.municipalities = ko.observableArray([]);

    self.allowedAutologinIps = ko.observableArray();
    self.ssoKeys = ko.observableArray();

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

    function organizationCommand( command, params ) {
      ajax.command( command,
                    _.merge(params,
                            {organizationId: self.organization().id()}))
        .pending( self.threeDMapServerParams.waiting)
        .success( util.showSavedIndicator )
        .error( util.showSavedIndicator )
        .call();
    }

    self.threeDMapServerParams.channel.send = function( serverDetails ) {
      organizationCommand( "update-3d-map-server-details", serverDetails );
    };

    self.threeDMapEnabled.subscribe( function( value ) {
      organizationCommand( "set-3d-map-enabled", {flag: Boolean( value )});
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

    function wrapName(name, lang) {
      return {lang: lang, name: ko.observable(name)};
    }

    function unWrapName(o) {
      return [o.lang, util.getIn(o, ["name"])];
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
          self.permanentArchiveEnabled(result.data["permanent-archive-enabled"]);
          self.calendarsEnabled(result.data["calendars-enabled"]);
          self.threeDMapEnabled( _.get(result, "data.3d-map.enabled"));
          self.threeDMapServerParams.server(_.get( result, "data.3d-map.server"));
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

      return false;
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
                  opening: openingMills};

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

    self.saveAutologinIps = function() {
      var ips = _(self.ssoKeys()).filter(function(ssoKey) {return ssoKey.selected();}).map("ip").value();
      ajax
        .command("update-allowed-autologin-ips", {"org-id": self.organization().id(), ips: ips})
        .success(util.showSavedIndicator)
        .call();
    };

    self.permanentArchiveEnabled.subscribe(function(value) {
      if (isLoading) {
        return;
      }
      ajax.command("set-organization-permanent-archive-enabled", {organizationId: self.organization().id(), enabled: value})
        .success(function() {
          self.indicator(true);
          self.organization()["permanent-archive-enabled"](value);
        })
        .call();
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
